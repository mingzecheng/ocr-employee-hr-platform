from io import BytesIO
import builtins
import hashlib
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.config import Settings
from app.engines import MockOcrEngine, OcrEngine, RecognitionOutput, build_engine
from app.main import create_app
from app.models import TextBlock
from app.object_storage import InMemoryObjectStorage


def image_bytes() -> bytes:
    image = Image.new("RGB", (240, 100), "white")
    output = BytesIO()
    image.save(output, format="PNG")
    return output.getvalue()


def client(tmp_path, engine: OcrEngine | None = None) -> TestClient:
    return TestClient(create_app(data_dir=tmp_path, engine=engine or MockOcrEngine()))


def storage_client(tmp_path, storage, engine: OcrEngine | None = None) -> TestClient:
    return TestClient(
        create_app(
            data_dir=tmp_path,
            engine=engine or MockOcrEngine(),
            storage=storage,
            internal_token="test-token",
        )
    )


def test_default_settings_select_paddle_and_limit_image_edge(monkeypatch):
    monkeypatch.delenv("OCR_ENGINE", raising=False)
    monkeypatch.delenv("OCR_MAX_IMAGE_EDGE", raising=False)

    settings = Settings()

    assert settings.engine == "paddle"
    assert settings.max_image_edge == 4096


def test_default_relative_data_dir_is_anchored_to_ocr_service(monkeypatch):
    monkeypatch.delenv("OCR_DATA_DIR", raising=False)

    settings = Settings()

    assert settings.data_dir == Path(__file__).resolve().parents[1] / "data"


def test_default_settings_expose_legacy_app_data_dir(monkeypatch):
    monkeypatch.delenv("OCR_DATA_DIR", raising=False)
    monkeypatch.delenv("OCR_LEGACY_DATA_DIR", raising=False)

    settings = Settings()

    assert settings.legacy_data_dirs == [Path(__file__).resolve().parents[1] / "app" / "data"]


def test_explicit_default_data_dir_also_exposes_legacy_app_data_dir(monkeypatch):
    monkeypatch.setenv("OCR_DATA_DIR", "./data")
    monkeypatch.delenv("OCR_LEGACY_DATA_DIR", raising=False)

    settings = Settings()

    assert settings.legacy_data_dirs == [Path(__file__).resolve().parents[1] / "app" / "data"]


def test_create_app_defers_default_engine_construction(tmp_path, monkeypatch):
    def fail_if_called(*args, **kwargs):
        raise AssertionError("the default engine must not be built during app construction")

    monkeypatch.setattr("app.main.build_engine", fail_if_called)

    app = create_app(data_dir=tmp_path)

    assert app.state.settings.engine == "paddle"


def test_lifespan_builds_default_engine_with_configured_image_limit(tmp_path, monkeypatch):
    calls: list[tuple[str, int]] = []

    def fake_build_engine(name: str, *, max_image_edge: int):
        calls.append((name, max_image_edge))
        return MockOcrEngine()

    monkeypatch.setattr("app.main.build_engine", fake_build_engine)

    with TestClient(create_app(data_dir=tmp_path)) as api:
        response = api.get("/health")

    assert response.json() == {"status": "ok", "engine": "mock"}
    assert calls == [("paddle", 4096)]


def test_health_reports_local_service(tmp_path):
    response = client(tmp_path).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "engine": "mock"}


def test_mock_engine_returns_text_evidence_without_business_fields(tmp_path):
    response = client(tmp_path).post(
        "/api/ocr/tasks",
        files={"file": ("employee.png", image_bytes(), "image/png")},
    )

    task = response.json()["data"]

    assert task["textBlocks"]
    assert task["fields"] == []


def test_successful_task_persists_and_serves_detection_preview(tmp_path):
    api = client(tmp_path)

    response = api.post(
        "/api/ocr/tasks",
        files={"file": ("employee.png", image_bytes(), "image/png")},
    )

    assert response.status_code == 200
    task = response.json()["data"]
    preview = task["detectionPreview"]
    assert preview["url"] == f"/api/ocr/tasks/{task['taskId']}/detection-preview"
    assert preview["contentType"] == "image/png"
    assert f"ocr/preview/{task['taskId']}.png" in api.app.state.ocr_service.storage.keys()

    preview_response = api.get(preview["url"])

    assert preview_response.status_code == 200
    assert preview_response.headers["content-type"] == "image/png"
    with Image.open(BytesIO(preview_response.content)) as preview_image:
        assert preview_image.size == (240, 100)
        assert preview_image.getpixel((10, 10)) != (255, 255, 255)


def test_detection_preview_remains_available_after_service_restart(tmp_path):
    with TestClient(create_app(data_dir=tmp_path, engine=MockOcrEngine())) as first_service:
        task = first_service.post(
            "/api/ocr/tasks",
            files={"file": ("employee.png", image_bytes(), "image/png")},
        ).json()["data"]

    with TestClient(create_app(data_dir=tmp_path, engine=MockOcrEngine())) as restarted_service:
        response = restarted_service.get(task["detectionPreview"]["url"])

    assert response.status_code == 200
    assert response.headers["content-type"] == "image/png"


def test_detection_preview_is_backfilled_for_legacy_successful_task(tmp_path):
    application = create_app(data_dir=tmp_path, engine=MockOcrEngine())
    with TestClient(application) as api:
        task = api.post(
            "/api/ocr/tasks",
            files={"file": ("employee.png", image_bytes(), "image/png")},
        ).json()["data"]
        legacy_task = application.state.ocr_service.get_task(task["taskId"])
        assert legacy_task is not None
        legacy_task.detection_preview = None
        application.state.ocr_service.tasks.put(legacy_task)

        response = api.get(f"/api/ocr/tasks/{task['taskId']}/detection-preview")
        queried = api.get(f"/api/ocr/tasks/{task['taskId']}")

    assert response.status_code == 200
    assert response.headers["content-type"] == "image/png"
    assert queried.json()["data"]["detectionPreview"]["storedName"]


def test_detection_preview_is_rebuilt_when_recorded_file_is_missing(tmp_path):
    application = create_app(data_dir=tmp_path, engine=MockOcrEngine())
    with TestClient(application) as api:
        task = api.post(
            "/api/ocr/tasks",
            files={"file": ("employee.png", image_bytes(), "image/png")},
        ).json()["data"]
        preview_key = f"ocr/preview/{task['taskId']}.png"
        application.state.ocr_service.storage.delete(preview_key)

        response = api.get(task["detectionPreview"]["url"])
        rebuilt_task = api.get(f"/api/ocr/tasks/{task['taskId']}").json()["data"]

    assert response.status_code == 200
    assert response.headers["content-type"] == "image/png"
    assert rebuilt_task["detectionPreview"]["storedName"] == task["detectionPreview"]["storedName"]
    assert f"ocr/preview/{task['taskId']}.png" in application.state.ocr_service.storage.keys()


def test_legacy_task_and_preview_are_available_from_current_data_dir(tmp_path):
    legacy_dir = tmp_path / "app-data"
    current_dir = tmp_path / "data"
    storage = InMemoryObjectStorage()
    with TestClient(create_app(data_dir=legacy_dir, engine=MockOcrEngine(), storage=storage)) as legacy_api:
        task = legacy_api.post(
            "/api/ocr/tasks",
            files={"file": ("employee.png", image_bytes(), "image/png")},
        ).json()["data"]
        legacy_task = legacy_api.app.state.ocr_service.get_task(task["taskId"])
        assert legacy_task is not None
        legacy_task.detection_preview = None
        legacy_api.app.state.ocr_service.tasks.put(legacy_task)

    with TestClient(
        create_app(
            data_dir=current_dir,
            legacy_data_dir=legacy_dir,
            engine=MockOcrEngine(),
            storage=storage,
        )
    ) as current_api:
        response = current_api.get(
            f"/api/ocr/tasks/{task['taskId']}/detection-preview"
        )
        queried = current_api.get(f"/api/ocr/tasks/{task['taskId']}")

    assert response.status_code == 200
    assert response.headers["content-type"] == "image/png"
    assert queried.json()["data"]["detectionPreview"]["storedName"]
    assert not (current_dir / "previews").exists()


def test_task_records_the_engine_version_used_for_recognition(tmp_path):
    class VersionedEngine(MockOcrEngine):
        name = "test-engine"
        version = "2026.09.16"

    response = client(tmp_path, engine=VersionedEngine()).post(
        "/api/ocr/tasks",
        files={"file": ("employee.png", image_bytes(), "image/png")},
    )

    assert response.status_code == 200
    assert response.json()["data"]["engineVersion"] == "2026.09.16"


def test_employee_profile_mapping_extracts_fields_when_document_type_is_supplied(tmp_path):
    class EmployeeProfileEngine(OcrEngine):
        name = "employee-profile"

        def recognize(self, image_path):
            del image_path
            return RecognitionOutput(
                text_blocks=[
                    TextBlock(
                        pageNo=1,
                        text="员工姓名：张三",
                        confidence=0.98,
                        bbox=[10, 20, 200, 60],
                    ),
                    TextBlock(
                        pageNo=1,
                        text="员工编号：EMP-001",
                        confidence=0.96,
                        bbox=[10, 80, 240, 120],
                    ),
                ],
                fields=[],
            )

    response = client(tmp_path, engine=EmployeeProfileEngine()).post(
        "/api/ocr/tasks",
        data={"documentType": "employee_profile"},
        files={"file": ("employee.png", image_bytes(), "image/png")},
    )

    assert response.status_code == 200
    fields = response.json()["data"]["fields"]
    assert [(field["fieldCode"], field["value"]) for field in fields] == [
        ("employee_name", "张三"),
        ("employee_id", "EMP-001"),
    ]
    assert all(field["validationStatus"] == "PASSED" for field in fields)
    assert all(field["reviewRequired"] is False for field in fields)


def test_manual_field_correction_updates_task_and_records_revision(tmp_path):
    class EmployeeProfileEngine(OcrEngine):
        name = "employee-profile"

        def recognize(self, image_path):
            del image_path
            return RecognitionOutput(
                text_blocks=[
                    TextBlock(
                        pageNo=1,
                        text="员工姓名：张三",
                        confidence=0.62,
                        bbox=[10, 20, 200, 60],
                    )
                ],
                fields=[],
            )

    api = client(tmp_path, engine=EmployeeProfileEngine())
    created = api.post(
        "/api/ocr/tasks",
        data={"documentType": "employee_profile"},
        files={"file": ("employee.png", image_bytes(), "image/png")},
    ).json()["data"]

    corrected = api.put(
        f"/api/ocr/tasks/{created['taskId']}/fields/employee_name",
        json={
            "value": "李四",
            "operatorId": "hr-001",
            "reason": "原图复核后修正",
        },
    )
    revisions = api.get(f"/api/ocr/tasks/{created['taskId']}/field-revisions")

    assert corrected.status_code == 200
    corrected_field = corrected.json()["data"]["fields"][0]
    assert corrected_field["value"] == "李四"
    assert corrected_field["validationStatus"] == "PASSED"
    assert corrected_field["reviewRequired"] is False
    assert revisions.status_code == 200
    assert revisions.json()["data"] == [
        {
            "revisionId": revisions.json()["data"][0]["revisionId"],
            "taskId": created["taskId"],
            "fieldCode": "employee_name",
            "previousValue": "张三",
            "currentValue": "李四",
            "operatorId": "hr-001",
            "reason": "原图复核后修正",
            "createdAt": revisions.json()["data"][0]["createdAt"],
        }
    ]


def test_task_list_is_paginated_and_statistics_summarize_runtime_state(tmp_path):
    successful_api = client(tmp_path)
    successful = successful_api.post(
        "/api/ocr/tasks",
        files={"file": ("successful.png", image_bytes(), "image/png")},
    ).json()["data"]
    failed_api = client(tmp_path, engine=FailingEngine())
    failed = failed_api.post(
        "/api/ocr/tasks",
        files={"file": ("failed.png", image_bytes(), "image/png")},
    ).json()["data"]

    page = successful_api.get("/api/ocr/tasks", params={"page": 1, "pageSize": 1})
    statistics = successful_api.get("/api/ocr/statistics")

    assert page.status_code == 200
    assert page.json()["data"] == {
        "items": [failed],
        "total": 2,
        "page": 1,
        "pageSize": 1,
    }
    assert statistics.status_code == 200
    assert statistics.json()["data"] == {
        "totalTasks": 2,
        "succeededTasks": 1,
        "failedTasks": 1,
        "reviewRequiredTasks": 0,
        "fieldRevisionCount": 0,
        "failureReasons": {"recognizer unavailable": 1},
    }
    assert successful["status"] == "SUCCEEDED"


def test_evaluation_reports_field_accuracy_and_failures_by_sample_clarity(tmp_path):
    class EvaluationEngine(OcrEngine):
        name = "evaluation"
        version = "test-1.0"

        def recognize(self, image_path):
            del image_path
            return RecognitionOutput(
                text_blocks=[
                    TextBlock(
                        pageNo=1,
                        text="员工姓名：张三",
                        confidence=0.98,
                        bbox=[10, 20, 200, 60],
                    ),
                    TextBlock(
                        pageNo=1,
                        text="员工编号：EMP-001",
                        confidence=0.96,
                        bbox=[10, 80, 240, 120],
                    ),
                ],
                fields=[],
            )

    successful_api = client(tmp_path, engine=EvaluationEngine())
    successful = successful_api.post(
        "/api/ocr/tasks",
        data={"documentType": "employee_profile"},
        files={"file": ("high.png", image_bytes(), "image/png")},
    ).json()["data"]
    failed_api = client(tmp_path, engine=FailingEngine())
    failed = failed_api.post(
        "/api/ocr/tasks",
        data={"documentType": "employee_profile"},
        files={"file": ("low.png", image_bytes(), "image/png")},
    ).json()["data"]

    response = successful_api.post(
        "/api/ocr/evaluations",
        json={
            "samples": [
                {
                    "taskId": successful["taskId"],
                    "clarity": "HIGH",
                    "expectedFields": {
                        "employee_name": "张三",
                        "employee_id": "emp-001",
                    },
                },
                {
                    "taskId": failed["taskId"],
                    "clarity": "LOW",
                    "expectedFields": {"employee_name": "李四"},
                },
            ]
        },
    )

    assert response.status_code == 200
    result = response.json()["data"]
    assert result["sampleCount"] == 2
    assert result["expectedFieldCount"] == 3
    assert result["detectedFieldCount"] == 2
    assert result["correctFieldCount"] == 2
    assert result["fieldDetectionRate"] == pytest.approx(2 / 3)
    assert result["fieldAccuracy"] == pytest.approx(2 / 3)
    assert result["failedTaskCount"] == 1
    assert result["failureRate"] == 0.5
    assert result["averageProcessingDurationMs"] >= 0
    assert result["byClarity"] == [
        {
            "clarity": "HIGH",
            "sampleCount": 1,
            "expectedFieldCount": 2,
            "detectedFieldCount": 2,
            "correctFieldCount": 2,
            "fieldDetectionRate": 1.0,
            "fieldAccuracy": 1.0,
            "failedTaskCount": 0,
            "failureRate": 0.0,
            "averageProcessingDurationMs": result["byClarity"][0][
                "averageProcessingDurationMs"
            ],
        },
        {
            "clarity": "LOW",
            "sampleCount": 1,
            "expectedFieldCount": 1,
            "detectedFieldCount": 0,
            "correctFieldCount": 0,
            "fieldDetectionRate": 0.0,
            "fieldAccuracy": 0.0,
            "failedTaskCount": 1,
            "failureRate": 1.0,
            "averageProcessingDurationMs": result["byClarity"][1][
                "averageProcessingDurationMs"
            ],
        },
    ]


def test_upload_rejects_unsupported_file_type_and_invalid_image(tmp_path):
    api = client(tmp_path)

    unsupported_file = api.post(
        "/api/ocr/tasks",
        files={"file": ("employee.txt", b"not an image", "text/plain")},
    )
    invalid_image = api.post(
        "/api/ocr/tasks",
        files={"file": ("employee.png", b"not an image", "image/png")},
    )

    assert unsupported_file.status_code == 400
    assert unsupported_file.json()["code"] == "UNSUPPORTED_FILE_TYPE"
    assert invalid_image.status_code == 400
    assert invalid_image.json()["code"] == "INVALID_IMAGE"


def test_upload_creates_queryable_task_with_structured_result(tmp_path):
    api = client(tmp_path)

    response = api.post(
        "/api/ocr/tasks",
        files={"file": ("employee.png", image_bytes(), "image/png")},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 0
    task = body["data"]
    assert task["taskId"]
    assert task["status"] == "SUCCEEDED"
    assert task["sourceFile"]["originalName"] == "employee.png"
    assert task["textBlocks"]
    assert task["textBlocks"][0]["bbox"] == [10, 10, 220, 80]
    assert task["fields"] == []

    queried = api.get(f"/api/ocr/tasks/{task['taskId']}")
    assert queried.status_code == 200
    assert queried.json()["data"]["taskId"] == task["taskId"]


def test_task_can_be_queried_after_service_restarts(tmp_path):
    with TestClient(create_app(data_dir=tmp_path, engine=MockOcrEngine())) as first_service:
        created = first_service.post(
            "/api/ocr/tasks",
            files={"file": ("employee.png", image_bytes(), "image/png")},
        ).json()["data"]

    with TestClient(create_app(data_dir=tmp_path, engine=MockOcrEngine())) as restarted_service:
        response = restarted_service.get(f"/api/ocr/tasks/{created['taskId']}")

    assert response.status_code == 200
    assert response.json()["data"] == created


def test_upload_requires_only_image_file(tmp_path):
    api = client(tmp_path)

    response = api.post(
        "/api/ocr/tasks",
        files={"file": ("employee.png", image_bytes(), "image/png")},
    )

    assert response.status_code == 200
    task = response.json()["data"]
    assert task["status"] == "SUCCEEDED"
    assert "documentType" not in task
    assert "archiveId" not in task
    assert "businessId" not in task


def test_successful_upload_returns_result_without_manual_review_step(tmp_path):
    api = client(tmp_path)

    response = api.post(
        "/api/ocr/tasks",
        files={"file": ("employee.png", image_bytes(), "image/png")},
    )

    task = response.json()["data"]
    assert task["status"] == "SUCCEEDED"
    assert task["textBlocks"][0]["text"]
    assert task["fields"] == []

    specification = api.get("/openapi.json").json()
    assert "/api/ocr/tasks/{task_id}/review" not in specification["paths"]


class FailingEngine(OcrEngine):
    name = "failing"

    def recognize(self, image_path):
        raise RuntimeError("recognizer unavailable")


def test_failed_recognition_keeps_source_file_and_error(tmp_path):
    api = client(tmp_path, engine=FailingEngine())

    response = api.post(
        "/api/ocr/tasks",
        files={"file": ("employee.png", image_bytes(), "image/png")},
    )

    assert response.status_code == 200
    task = response.json()["data"]
    assert task["status"] == "FAILED"
    assert task["errorMessage"] == "recognizer unavailable"
    assert f"ocr/source/{task['sourceFile']['sha256']}.png" in api.app.state.ocr_service.storage.keys()


def test_detection_preview_failure_marks_task_failed(tmp_path, monkeypatch):
    application = create_app(data_dir=tmp_path, engine=MockOcrEngine())
    with TestClient(application) as api:
        def fail_preview(**kwargs):
            del kwargs
            raise OSError("preview storage unavailable")

        monkeypatch.setattr(
            application.state.ocr_service.source_files,
            "save_detection_preview",
            fail_preview,
        )

        response = api.post(
            "/api/ocr/tasks",
            files={"file": ("employee.png", image_bytes(), "image/png")},
        )

        task = response.json()["data"]
        queried = api.get(f"/api/ocr/tasks/{task['taskId']}").json()["data"]

    assert response.status_code == 200
    assert task["status"] == "FAILED"
    assert task["errorMessage"] == "preview storage unavailable"
    assert queried["status"] == "FAILED"


def test_multipart_upload_persists_binary_in_minio_only(tmp_path):
    storage = InMemoryObjectStorage()
    with storage_client(tmp_path, storage) as api:
        response = api.post(
            "/api/ocr/tasks",
            files={"file": ("employee.png", image_bytes(), "image/png")},
        )

    assert response.status_code == 200
    assert not list(tmp_path.glob("*.png"))
    assert not (tmp_path / "previews").exists()
    assert {key for key in storage.keys() if key.startswith("ocr/source/")}
    assert {key for key in storage.keys() if key.startswith("ocr/preview/")}


def test_archive_object_reference_uses_existing_minio_source_without_copy(tmp_path):
    storage = InMemoryObjectStorage({"archive/employee/1/a.png": image_bytes()})
    with storage_client(tmp_path, storage) as api:
        response = api.post(
            "/api/ocr/tasks",
            data={
                "sourceObjectKey": "archive/employee/1/a.png",
                "originalName": "a.png",
                "contentType": "image/png",
                "size": str(len(image_bytes())),
                "sha256": hashlib.sha256(image_bytes()).hexdigest(),
            },
            headers={"X-OCR-Internal-Token": "test-token"},
        )

    assert response.status_code == 200
    assert set(storage.keys()) == {
        "archive/employee/1/a.png",
        f"ocr/preview/{response.json()['data']['taskId']}.png",
    }


def test_object_reference_requires_internal_token(tmp_path):
    storage = InMemoryObjectStorage({"archive/employee/1/a.png": image_bytes()})
    with storage_client(tmp_path, storage) as api:
        response = api.post(
            "/api/ocr/tasks",
            data={
                "sourceObjectKey": "archive/employee/1/a.png",
                "originalName": "a.png",
                "contentType": "image/png",
            },
            headers={"X-OCR-Internal-Token": "wrong"},
        )

    assert response.status_code == 403


def test_unknown_task_returns_structured_not_found(tmp_path):
    response = client(tmp_path).get("/api/ocr/tasks/missing-task")

    assert response.status_code == 404
    assert response.json() == {
        "code": "TASK_NOT_FOUND",
        "message": "OCR task not found",
        "data": None,
    }


def test_paddle_engine_selection_reports_missing_dependency(monkeypatch):
    original_import = builtins.__import__

    def import_without_paddleocr(name, *args, **kwargs):
        if name == "paddleocr":
            raise ImportError("paddleocr is unavailable")
        return original_import(name, *args, **kwargs)

    monkeypatch.setattr(builtins, "__import__", import_without_paddleocr)

    with pytest.raises(RuntimeError, match="install paddlepaddle and paddleocr first"):
        build_engine("paddle")


def test_openapi_documents_ocr_routes_and_field_meanings(tmp_path):
    specification = client(tmp_path).get("/openapi.json").json()
    paths = specification["paths"]

    assert paths["/api/ocr/tasks"]["post"]["summary"] == "上传图片并创建 OCR 任务"
    assert "识别任务" in paths["/api/ocr/tasks"]["post"]["description"]
    assert paths["/api/ocr/tasks/{task_id}"]["get"]["summary"] == "查询 OCR 任务结果"
    assert paths["/api/ocr/tasks/{task_id}/detection-preview"]["get"]["summary"] == (
        "获取 OCR 检测框效果图"
    )
    assert "/api/ocr/tasks/{task_id}/review" not in paths

    schemas = specification["components"]["schemas"]
    task_properties = schemas["OcrTask"]["properties"]
    assert task_properties["taskId"]["description"] == "OCR 任务唯一标识"
    assert task_properties["status"]["description"] == "任务当前状态"
    assert task_properties["fields"]["description"] == (
        "documentType=employee_profile 时返回映射字段；未指定材料类型时为空"
    )
    assert task_properties["detectionPreview"]["description"] == (
        "带 OCR 文本检测框、序号、置信度和识别文本的派生效果图"
    )
    assert task_properties["status"]["enum"] == [
        "PENDING",
        "PREPROCESSING",
        "RECOGNIZING",
        "SUCCEEDED",
        "FAILED",
    ]

    source_properties = schemas["SourceFile"]["properties"]
    assert source_properties["originalName"]["description"] == "用户上传时的原始文件名"
    assert schemas["TextBlock"]["properties"]["bbox"]["description"] == (
        "文本位置框，格式为 [左, 上, 右, 下]"
    )
    assert "ReviewRequest" not in schemas

    upload_body_ref = paths["/api/ocr/tasks"]["post"]["requestBody"]["content"][
        "multipart/form-data"
    ]["schema"]["$ref"]
    upload_body_name = upload_body_ref.rsplit("/", 1)[-1]
    upload_body = schemas[upload_body_name]
    assert list(upload_body["properties"]) == [
        "file",
        "documentType",
        "sourceObjectKey",
        "originalName",
        "contentType",
        "size",
        "sha256",
    ]
    assert "required" not in upload_body
