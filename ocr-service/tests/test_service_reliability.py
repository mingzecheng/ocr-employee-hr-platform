from __future__ import annotations

import asyncio
import hashlib
from io import BytesIO
import threading
import time

import httpx
from fastapi.testclient import TestClient
from PIL import Image

from app.engines import MockOcrEngine, OcrEngine
from app.main import create_app


def unique_image_bytes(seed: int) -> bytes:
    # 每个种子产生不同内容，保证 SHA-256 不同，避免命中同 hash 幂等短路。
    image = Image.new("RGB", (240, 100), ((seed * 37) % 256, (seed * 53) % 256, (seed * 71) % 256))
    output = BytesIO()
    image.save(output, format="PNG")
    return output.getvalue()


def authorized_client(app) -> TestClient:
    return TestClient(app, headers={"X-OCR-Internal-Token": app.state.settings.internal_token})


# --------------------------------------------------------------------------- #
# 同 hash 幂等：相同文件重复上传必须返回已有任务，不重复推理。
# --------------------------------------------------------------------------- #
def test_repeated_upload_of_same_file_returns_existing_task(tmp_path):
    api = authorized_client(create_app(data_dir=tmp_path, engine=MockOcrEngine()))
    content = unique_image_bytes(1)

    first = api.post(
        "/api/ocr/tasks",
        files={"file": ("employee.png", content, "image/png")},
    ).json()["data"]
    second = api.post(
        "/api/ocr/tasks",
        files={"file": ("renamed.png", content, "image/png")},
    ).json()["data"]

    assert first["taskId"] == second["taskId"]
    assert first["status"] == "SUCCEEDED"


def test_different_files_create_distinct_tasks(tmp_path):
    api = authorized_client(create_app(data_dir=tmp_path, engine=MockOcrEngine()))

    first = api.post(
        "/api/ocr/tasks",
        files={"file": ("a.png", unique_image_bytes(1), "image/png")},
    ).json()["data"]
    second = api.post(
        "/api/ocr/tasks",
        files={"file": ("b.png", unique_image_bytes(2), "image/png")},
    ).json()["data"]

    assert first["taskId"] != second["taskId"]


# --------------------------------------------------------------------------- #
# 单任务超时：识别超过阈值后任务标记 FAILED。
# --------------------------------------------------------------------------- #
def test_recognition_timeout_marks_task_failed(tmp_path, monkeypatch):
    monkeypatch.setenv("OCR_TASK_TIMEOUT_SECONDS", "0.3")

    class SlowEngine(OcrEngine):
        name = "slow"
        version = "slow-1.0"

        def recognize(self, image_path):
            time.sleep(0.6)
            return MockOcrEngine().recognize(image_path)

    api = authorized_client(create_app(data_dir=tmp_path, engine=SlowEngine()))
    response = api.post(
        "/api/ocr/tasks",
        files={"file": ("employee.png", unique_image_bytes(1), "image/png")},
    )

    assert response.status_code == 200
    task = response.json()["data"]
    assert task["status"] == "FAILED"
    assert "timeout" in (task["errorMessage"] or "").lower()

    # 超时后仍可查询到该任务，且最终落库状态保持 FAILED（后台线程不得翻成 SUCCEEDED）。
    time.sleep(0.8)
    queried = api.get(f"/api/ocr/tasks/{task['taskId']}").json()["data"]
    assert queried["status"] == "FAILED"


# --------------------------------------------------------------------------- #
# 并发上限：同时识别数不超过 Semaphore 配置。
# --------------------------------------------------------------------------- #
def test_concurrent_recognition_is_bounded_by_semaphore(tmp_path, monkeypatch):
    monkeypatch.setenv("OCR_MAX_CONCURRENT_TASKS", "2")
    monkeypatch.setenv("OCR_TASK_TIMEOUT_SECONDS", "30")

    peak = {"active": 0, "max": 0}
    lock = threading.Lock()

    class CountingEngine(OcrEngine):
        name = "counting"
        version = "count-1.0"

        def recognize(self, image_path):
            with lock:
                peak["active"] += 1
                peak["max"] = max(peak["max"], peak["active"])
            time.sleep(0.3)
            with lock:
                peak["active"] -= 1
            return MockOcrEngine().recognize(image_path)

    application = create_app(data_dir=tmp_path, engine=CountingEngine())
    token = application.state.settings.internal_token

    async def drive() -> None:
        transport = httpx.ASGITransport(app=application)
        async with httpx.AsyncClient(transport=transport, base_url="http://ocr.test") as client:
            async def one(index: int):
                return await client.post(
                    "/api/ocr/tasks",
                    files={
                        "file": (f"t{index}.png", unique_image_bytes(index + 10), "image/png")
                    },
                    headers={"X-OCR-Internal-Token": token},
                )

            await asyncio.gather(*(one(i) for i in range(4)))

    asyncio.run(drive())

    assert peak["max"] <= 2
    assert peak["max"] >= 1


# --------------------------------------------------------------------------- #
# 启动恢复：崩溃残留的 RECOGNIZING 任务在服务重启后重置为 INTERRUPTED。
# --------------------------------------------------------------------------- #
def test_startup_resets_stuck_recognizing_task_to_interrupted(tmp_path):
    application = create_app(data_dir=tmp_path, engine=MockOcrEngine())
    with authorized_client(application) as first:
        task = first.post(
            "/api/ocr/tasks",
            files={"file": ("employee.png", unique_image_bytes(1), "image/png")},
        ).json()["data"]
        # 模拟进程在识别中途崩溃：把已成功任务强行改回 RECOGNIZING 后落库。
        stuck = application.state.ocr_service.get_task(task["taskId"])
        assert stuck is not None
        stuck.status = "RECOGNIZING"
        stuck.error_message = None
        application.state.ocr_service.tasks.put(stuck)

    restarted = create_app(data_dir=tmp_path, engine=MockOcrEngine())
    with authorized_client(restarted) as second:
        recovered = second.get(f"/api/ocr/tasks/{task['taskId']}").json()["data"]

    assert recovered["status"] == "INTERRUPTED"
    assert recovered["errorMessage"]


def test_interrupted_status_is_announced_in_openapi_schema(tmp_path):
    specification = authorized_client(create_app(data_dir=tmp_path, engine=MockOcrEngine())).get(
        "/openapi.json"
    ).json()
    enum = specification["components"]["schemas"]["OcrTask"]["properties"]["status"]["enum"]
    assert "INTERRUPTED" in enum
