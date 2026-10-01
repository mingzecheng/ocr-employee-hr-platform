from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from io import BytesIO
import os
from pathlib import Path
import secrets
import sys

from fastapi import FastAPI, File, Form, Header, Path as ApiPath, Query, UploadFile
from fastapi.responses import JSONResponse, Response
from PIL import Image

if __package__ in {None, ""}:
    # PyCharm 直接运行 app/main.py 时没有包上下文，补入项目根目录后使用绝对导入。
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from app.config import SUPPORTED_DOCUMENT_TYPES, SUPPORTED_IMAGE_TYPES, Settings
    from app.engines import OcrEngine, build_engine
    from app.evaluation import EvaluationTaskNotFoundError
    from app.models import (
        ApiResponse,
        FieldCorrectionRequest,
        FieldRevision,
        HealthResponse,
        OcrEvaluationRequest,
        OcrEvaluationResult,
        OcrStatistics,
        OcrTask,
        OcrTaskPage,
        dump_model,
    )
    from app.service import OcrService, task_data
    from app.object_storage import InMemoryObjectStorage, MinioObjectStorage, ObjectStorage, StorageObjectNotFound
else:
    from .config import SUPPORTED_DOCUMENT_TYPES, SUPPORTED_IMAGE_TYPES, Settings
    from .engines import OcrEngine, build_engine
    from .evaluation import EvaluationTaskNotFoundError
    from .models import (
        ApiResponse,
        FieldCorrectionRequest,
        FieldRevision,
        HealthResponse,
        OcrEvaluationRequest,
        OcrEvaluationResult,
        OcrStatistics,
        OcrTask,
        OcrTaskPage,
        dump_model,
    )
    from .service import OcrService, task_data
    from .object_storage import InMemoryObjectStorage, MinioObjectStorage, ObjectStorage, StorageObjectNotFound


_TEST_STORAGES: dict[tuple[str, str], InMemoryObjectStorage] = {}


def envelope(*, code, message: str, data, status_code: int = 200) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={"code": code, "message": message, "data": data},
    )


def create_app(
    *,
    data_dir: Path | str | None = None,
    legacy_data_dir: Path | str | None = None,
    engine: OcrEngine | None = None,
    engine_name: str | None = None,
    storage: ObjectStorage | None = None,
    internal_token: str | None = None,
) -> FastAPI:
    settings = Settings(
        data_dir=data_dir,
        legacy_data_dir=legacy_data_dir,
        engine=engine_name,
        internal_token=internal_token,
    )

    def build_storage() -> ObjectStorage:
        if storage is not None:
            return storage
        # A caller-provided data directory is the test/integration injection boundary.
        if data_dir is not None:
            key = (str(Path(data_dir).resolve()), settings.minio_bucket)
            return _TEST_STORAGES.setdefault(key, InMemoryObjectStorage())
        return MinioObjectStorage(
            endpoint=settings.minio_endpoint,
            access_key=settings.minio_access_key,
            secret_key=settings.minio_secret_key,
            bucket=settings.minio_bucket,
        )

    @asynccontextmanager
    async def lifespan(application: FastAPI) -> AsyncIterator[None]:
        if not hasattr(application.state, "ocr_service"):
            # 真实模型只在服务进程启动时初始化，避免导入路由模块触发模型加载。
            selected_engine = build_engine(
                settings.engine,
                max_image_edge=settings.max_image_edge,
            )
            application.state.ocr_service = OcrService(
                data_dir=settings.data_dir,
                legacy_data_dirs=settings.legacy_data_dirs,
                engine=selected_engine,
                low_confidence_threshold=settings.low_confidence_threshold,
                storage=build_storage(),
                bucket=settings.minio_bucket,
                allowed_source_prefix=settings.minio_allowed_source_prefix,
            )
        yield

    app = FastAPI(
        title="Local OCR Service",
        version="0.1.0",
        description="员工档案与人事流程平台的本地 OCR 接口服务。负责图片上传、文字识别和 OCR 证据查询。",
        lifespan=lifespan,
    )
    app.state.settings = settings

    if engine is not None:
        app.state.ocr_service = OcrService(
            data_dir=settings.data_dir,
            legacy_data_dirs=settings.legacy_data_dirs,
            engine=engine,
            low_confidence_threshold=settings.low_confidence_threshold,
            storage=build_storage(),
            bucket=settings.minio_bucket,
            allowed_source_prefix=settings.minio_allowed_source_prefix,
        )

    @app.get(
        "/health",
        summary="检查 OCR 服务健康状态",
        description="返回服务进程是否可用以及当前选择的 OCR 引擎名称。",
        response_model=HealthResponse,
        tags=["系统"],
    )
    def health() -> dict[str, str]:
        service = getattr(app.state, "ocr_service", None)
        engine_label = service.engine.name if service else settings.engine
        return {"status": "ok", "engine": engine_label}

    @app.post(
        "/api/ocr/tasks",
        summary="上传图片并创建 OCR 任务",
        description=(
            "上传图片并创建识别任务，随后同步执行 OCR。接口只需要图片文件，服务会保存原图并返回文本块、"
            "坐标和置信度；documentType=employee_profile 时会额外执行员工档案字段映射。"
            "识别成功后直接返回 SUCCEEDED 任务，识别失败时仍保留原图。"
        ),
        response_model=ApiResponse[OcrTask],
        tags=["OCR 任务"],
    )
    async def create_ocr_task(
        file: UploadFile | None = File(None, description="待识别的 PNG 或 JPEG 图片文件"),
        document_type: str | None = Form(
            default=None,
            alias="documentType",
            description="可选材料类型；employee_profile 会启用员工档案字段映射",
        ),
        source_object_key: str | None = Form(default=None, alias="sourceObjectKey"),
        original_name: str | None = Form(default=None, alias="originalName"),
        source_content_type: str | None = Form(default=None, alias="contentType"),
        source_size: int | None = Form(default=None, alias="size"),
        source_sha256: str | None = Form(default=None, alias="sha256"),
        internal_token: str | None = Header(default=None, alias="X-OCR-Internal-Token"),
    ) -> JSONResponse:
        if file is not None and source_object_key is not None:
            return envelope(
                code="INVALID_REQUEST",
                message="Provide either file or sourceObjectKey, not both",
                data=None,
                status_code=400,
            )
        if file is None and source_object_key is None:
            return envelope(
                code="INVALID_REQUEST",
                message="An image file or sourceObjectKey is required",
                data=None,
                status_code=400,
            )
        if source_object_key is not None:
            if not secrets.compare_digest(internal_token or "", settings.internal_token):
                return envelope(
                    code="FORBIDDEN",
                    message="Invalid OCR internal token",
                    data=None,
                    status_code=403,
                )
            if not original_name or not source_content_type:
                return envelope(
                    code="INVALID_SOURCE_REFERENCE",
                    message="Object reference metadata is incomplete",
                    data=None,
                    status_code=400,
                )
            content_type = source_content_type.lower()
        else:
            assert file is not None
            # 接口只接收文件；文件类型由 MIME 和实际图片内容共同确认。
            content_type = (file.content_type or "").lower()
        if content_type not in SUPPORTED_IMAGE_TYPES:
            return envelope(
                code="UNSUPPORTED_FILE_TYPE",
                message="Only PNG and JPEG images are supported",
                data=None,
                status_code=400,
            )
        if document_type is not None and document_type not in SUPPORTED_DOCUMENT_TYPES:
            return envelope(
                code="UNSUPPORTED_DOCUMENT_TYPE",
                message="Unsupported document type",
                data=None,
                status_code=400,
            )
        if file is not None:
            content = await file.read(settings.max_file_size_bytes + 1)
            if len(content) > settings.max_file_size_bytes:
                return envelope(
                    code="FILE_TOO_LARGE",
                    message="Uploaded file exceeds the configured size limit",
                    data=None,
                    status_code=400,
                )
            try:
                # 不能只信任客户端 MIME 类型，必须解析文件内容确认它是真图片。
                with Image.open(BytesIO(content)) as image:
                    image.verify()
            except Exception:
                return envelope(
                    code="INVALID_IMAGE",
                    message="Uploaded file is not a valid image",
                    data=None,
                    status_code=400,
                )
            create_kwargs = {
                "original_name": file.filename or "upload",
                "content_type": content_type,
                "content": content,
            }
        else:
            create_kwargs = {
                "original_name": original_name,
                "content_type": content_type,
                "source_object_key": source_object_key,
                "source_size": source_size,
                "source_sha256": source_sha256,
            }
        try:
            task = app.state.ocr_service.create_task(
                **create_kwargs,
                document_type=document_type,
            )
        except StorageObjectNotFound:
            return envelope(
                code="SOURCE_OBJECT_NOT_FOUND",
                message="OCR source object not found",
                data=None,
                status_code=404,
            )
        except ValueError as exc:
            return envelope(
                code="INVALID_SOURCE_REFERENCE",
                message=str(exc),
                data=None,
                status_code=400,
            )
        return envelope(code=0, message="OK", data=task_data(task))

    @app.get(
        "/api/ocr/tasks",
        summary="分页查询 OCR 任务",
        description="按创建时间倒序分页返回本地持久化的 OCR 任务。",
        response_model=ApiResponse[OcrTaskPage],
        tags=["OCR 任务"],
    )
    def list_ocr_tasks(
        page: int = Query(default=1, ge=1, description="页码，从 1 开始"),
        page_size: int = Query(
            default=20,
            ge=1,
            le=100,
            alias="pageSize",
            description="每页任务数量，最大 100",
        ),
    ) -> JSONResponse:
        result = app.state.ocr_service.list_tasks(page=page, page_size=page_size)
        return envelope(code=0, message="OK", data=dump_model(result))

    @app.get(
        "/api/ocr/statistics",
        summary="查询 OCR 任务统计",
        description="汇总本地持久化任务的成功、失败、待复核和人工修订情况。",
        response_model=ApiResponse[OcrStatistics],
        tags=["OCR 任务"],
    )
    def get_ocr_statistics() -> JSONResponse:
        return envelope(code=0, message="OK", data=dump_model(app.state.ocr_service.statistics()))

    @app.post(
        "/api/ocr/evaluations",
        summary="统计 OCR 评测结果",
        description=(
            "使用即时提交的脱敏字段真值，按高、中、低清晰度统计字段准确率、检出率、"
            "失败率和平均处理耗时。真值数据不会被服务持久化。"
        ),
        response_model=ApiResponse[OcrEvaluationResult],
        tags=["OCR 评测"],
    )
    def evaluate_ocr_tasks(request: OcrEvaluationRequest) -> JSONResponse:
        try:
            result = app.state.ocr_service.evaluate(request)
        except EvaluationTaskNotFoundError as exc:
            return envelope(
                code="TASK_NOT_FOUND",
                message=f"OCR task not found: {exc.task_id}",
                data=None,
                status_code=404,
            )
        return envelope(code=0, message="OK", data=dump_model(result))

    @app.get(
        "/api/ocr/tasks/{task_id}/detection-preview",
        summary="获取 OCR 检测框效果图",
        description="返回保留原图内容并绘制文本检测框、序号、置信度和识别文本的 PNG 效果图。",
        response_model=None,
        tags=["OCR 任务"],
    )
    def get_detection_preview(
        task_id: str = ApiPath(..., description="OCR 任务唯一标识"),
    ) -> Response:
        preview_result = app.state.ocr_service.ensure_detection_preview(task_id)
        if preview_result is None:
            return envelope(
                code="TASK_NOT_FOUND",
                message="OCR task not found",
                data=None,
                status_code=404,
            )
        task, preview_content = preview_result
        if preview_content is None or task.detection_preview is None:
            return envelope(
                code="DETECTION_PREVIEW_NOT_FOUND",
                message="OCR detection preview not found",
                data=None,
                status_code=404,
            )
        return Response(
            content=preview_content,
            media_type=task.detection_preview.content_type,
            headers={
                "Content-Disposition": f'inline; filename="{task.detection_preview.stored_name}"'
            },
        )

    @app.get(
        "/api/ocr/tasks/{task_id}/field-revisions",
        summary="查询 OCR 字段修订记录",
        description="按时间顺序返回指定 OCR 任务的人工字段修订历史。",
        response_model=ApiResponse[list[FieldRevision]],
        tags=["OCR 任务"],
    )
    def list_field_revisions(
        task_id: str = ApiPath(..., description="OCR 任务唯一标识"),
    ) -> JSONResponse:
        task = app.state.ocr_service.get_task(task_id)
        if task is None:
            return envelope(
                code="TASK_NOT_FOUND",
                message="OCR task not found",
                data=None,
                status_code=404,
            )
        revisions = app.state.ocr_service.list_field_revisions(task_id)
        return envelope(
            code=0,
            message="OK",
            data=[dump_model(revision) for revision in revisions],
        )

    @app.put(
        "/api/ocr/tasks/{task_id}/fields/{field_code}",
        summary="人工修订 OCR 映射字段",
        description="更新一个已映射字段，重新执行格式校验，并保存不可变的修订记录。",
        response_model=ApiResponse[OcrTask],
        tags=["OCR 任务"],
    )
    def correct_field(
        correction: FieldCorrectionRequest,
        task_id: str = ApiPath(..., description="OCR 任务唯一标识"),
        field_code: str = ApiPath(..., description="待修订的字段编码"),
    ) -> JSONResponse:
        task = app.state.ocr_service.get_task(task_id)
        if task is None:
            return envelope(
                code="TASK_NOT_FOUND",
                message="OCR task not found",
                data=None,
                status_code=404,
            )
        corrected_task = app.state.ocr_service.correct_field(
            task=task,
            field_code=field_code,
            value=correction.value,
            operator_id=correction.operator_id,
            reason=correction.reason,
        )
        if corrected_task is None:
            return envelope(
                code="FIELD_NOT_FOUND",
                message="OCR field not found",
                data=None,
                status_code=404,
            )
        return envelope(code=0, message="OK", data=task_data(corrected_task))

    @app.get(
        "/api/ocr/tasks/{task_id}",
        summary="查询 OCR 任务结果",
        description="根据任务 ID 查询处理状态、原图信息、文本块、字段结果和失败原因。",
        response_model=ApiResponse[OcrTask],
        tags=["OCR 任务"],
    )
    def get_ocr_task(
        task_id: str = ApiPath(..., description="待查询的 OCR 任务唯一标识")
    ) -> JSONResponse:
        task = app.state.ocr_service.get_task(task_id)
        if task is None:
            return envelope(
                code="TASK_NOT_FOUND",
                message="OCR task not found",
                data=None,
                status_code=404,
            )
        return envelope(code=0, message="OK", data=task_data(task))

    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn

    # 支持 PyCharm 的 Python 运行配置直接启动服务，无需手工拼接 Uvicorn 命令。
    uvicorn.run(
        app,
        host=os.getenv("OCR_HOST", "127.0.0.1"),
        port=int(os.getenv("OCR_PORT", "8000")),
    )
