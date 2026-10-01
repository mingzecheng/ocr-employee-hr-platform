from __future__ import annotations

from datetime import datetime, timezone
import hashlib
from pathlib import Path
from time import perf_counter
from tempfile import TemporaryDirectory
from uuid import uuid4

from .engines import OcrEngine
from .evaluation import evaluate_samples
from .extraction import EmployeeProfileFieldExtractor
from .models import (
    FieldRevision,
    OcrEvaluationRequest,
    OcrEvaluationResult,
    OcrStatistics,
    OcrTask,
    OcrTaskPage,
    dump_model,
)
from .object_storage import ObjectStorage, StorageObjectNotFound, validate_object_key
from .store import SourceFileStore, StorageReferences, TaskStore


class OcrService:
    """编排文件保存、OCR 识别和任务状态。"""

    def __init__(
        self,
        *,
        data_dir: Path,
        legacy_data_dirs: list[Path] | tuple[Path, ...] = (),
        engine: OcrEngine,
        low_confidence_threshold: float = 0.85,
        storage: ObjectStorage,
        bucket: str,
        allowed_source_prefix: str = "archive/",
    ) -> None:
        normalized_legacy_dirs = tuple(Path(path) for path in legacy_data_dirs)
        self.storage = storage
        self.bucket = bucket
        self.allowed_source_prefix = (
            allowed_source_prefix if allowed_source_prefix.endswith("/") else f"{allowed_source_prefix}/"
        )
        self.source_files = SourceFileStore(
            data_dir,
            normalized_legacy_dirs,
            storage=storage,
            bucket=bucket,
        )
        self.tasks = TaskStore(data_dir, normalized_legacy_dirs)
        self.engine = engine
        self.employee_profile_extractor = EmployeeProfileFieldExtractor(
            low_confidence_threshold=low_confidence_threshold
        )

    def create_task(
        self,
        *,
        original_name: str,
        content_type: str,
        content: bytes | None = None,
        document_type: str | None = None,
        source_object_key: str | None = None,
        source_size: int | None = None,
        source_sha256: str | None = None,
    ) -> OcrTask:
        if (content is None) == (source_object_key is None):
            raise ValueError("Exactly one OCR source mode is required")
        if content is not None:
            source_file, object_key = self.source_files.save(
                original_name=original_name,
                content_type=content_type,
                content=content,
            )
        else:
            object_key = self._validate_archive_source_key(source_object_key or "")
            stored = self.storage.stat(object_key)
            if source_size is not None and source_size != stored.size:
                raise ValueError("Source object size does not match request")
            source_file = self.source_files.register_reference(
                original_name=original_name,
                content_type=content_type or stored.content_type,
                size=source_size if source_size is not None else stored.size,
                sha256=source_sha256 or "",
                object_key=object_key,
            )
            if source_sha256:
                actual = self.storage.get_bytes(object_key)
                if actual.sha256 != source_sha256:
                    raise ValueError("Source object hash does not match request")
        references = StorageReferences(
            source_bucket=self.bucket,
            source_object_key=object_key,
            storage_version=1,
        )
        now = datetime.now(timezone.utc)
        task = OcrTask(
            taskId=uuid4().hex,
            status="RECOGNIZING",
            sourceFile=source_file,
            engineVersion=self.engine.version,
            createdAt=now,
            updatedAt=now,
        )
        self.tasks.put(task, references)
        recognition_started_at = perf_counter()
        try:
            # 原图只短暂落到系统临时目录，识别与预览完成后由上下文管理器清理。
            with TemporaryDirectory(prefix="ocr-task-") as directory:
                source_path = Path(directory) / source_file.stored_name
                downloaded = self.storage.download_to_path(object_key, source_path)
                if source_file.sha256 and downloaded.sha256 != source_file.sha256:
                    raise ValueError("Stored source object hash does not match metadata")
                if not source_file.sha256:
                    source_file.sha256 = downloaded.sha256
                recognition = self.engine.recognize(source_path)
                task.status = "SUCCEEDED"
                task.text_blocks = recognition.text_blocks
                task.fields = recognition.fields
                if document_type == "employee_profile":
                    task.fields = self.employee_profile_extractor.extract(task.text_blocks)
                task.detection_preview = self.source_files.save_detection_preview(
                    task_id=task.task_id,
                    source_file=task.source_file,
                    text_blocks=task.text_blocks,
                    source_path=source_path,
                )
                references = StorageReferences(
                    source_bucket=self.bucket,
                    source_object_key=object_key,
                    preview_bucket=self.bucket,
                    preview_object_key=f"ocr/preview/{task.task_id}.png",
                    storage_version=1,
                )
        except Exception as exc:  # Recognition and preview failures are queryable states.
            task.status = "FAILED"
            task.error_message = str(exc)
        finally:
            task.processing_duration_ms = (perf_counter() - recognition_started_at) * 1000
        task.updated_at = datetime.now(timezone.utc)
        return self.tasks.put(task, references)

    def get_task(self, task_id: str) -> OcrTask | None:
        return self.tasks.get(task_id)

    def ensure_detection_preview(self, task_id: str) -> tuple[OcrTask, bytes | None] | None:
        result = self.tasks.get_with_references(task_id)
        if result is None:
            return None
        task, references = result

        if task.status != "SUCCEEDED":
            return task, None

        if references.preview_object_key:
            try:
                preview_content = self.storage.get_bytes(references.preview_object_key).content
                if task.detection_preview is None and preview_content is not None:
                    task.detection_preview = self._legacy_preview(task.task_id, preview_content)
                    task.updated_at = datetime.now(timezone.utc)
                    self.tasks.put(task, references)
                return task, preview_content
            except StorageObjectNotFound:
                pass

        legacy_preview = self.source_files.preview_bytes_from_legacy(task.detection_preview)
        if legacy_preview is not None:
            preview_key = f"ocr/preview/{task.task_id}.png"
            self.storage.put_bytes(preview_key, "image/png", legacy_preview)
            task.detection_preview = task.detection_preview or self._legacy_preview(task.task_id, legacy_preview)
            task.updated_at = datetime.now(timezone.utc)
            self.tasks.put(
                task,
                StorageReferences(self.bucket, references.source_object_key, self.bucket, preview_key, 1),
            )
            return task, legacy_preview

        source_path: Path | None = None
        with TemporaryDirectory(prefix="ocr-preview-task-") as directory:
            candidate = Path(directory) / task.source_file.stored_name
            try:
                if references.source_object_key:
                    try:
                        self.storage.download_to_path(references.source_object_key, candidate)
                        source_path = candidate
                    except StorageObjectNotFound:
                        legacy_source = self.source_files.source_path(task.source_file.stored_name)
                        if legacy_source is None:
                            return task, None
                        candidate.write_bytes(legacy_source.read_bytes())
                        source_path = candidate
                        self.storage.put_bytes(
                            references.source_object_key,
                            task.source_file.content_type,
                            candidate.read_bytes(),
                        )
                else:
                    legacy_source = self.source_files.source_path(task.source_file.stored_name)
                    if legacy_source is None:
                        return task, None
                    candidate.write_bytes(legacy_source.read_bytes())
                    source_path = candidate
                    source_content = candidate.read_bytes()
                    source_key = f"ocr/source/{task.source_file.sha256}{Path(task.source_file.stored_name).suffix.lower()}"
                    self.storage.put_bytes(source_key, task.source_file.content_type, source_content)
                    references = StorageReferences(self.bucket, source_key, references.preview_bucket,
                                                  references.preview_object_key, 1)
                task.detection_preview = self.source_files.save_detection_preview(
                    task_id=task.task_id,
                    source_file=task.source_file,
                    text_blocks=task.text_blocks,
                    source_path=source_path,
                )
                preview_key = f"ocr/preview/{task.task_id}.png"
                references = StorageReferences(
                    self.bucket,
                    references.source_object_key,
                    self.bucket,
                    preview_key,
                    1,
                )
                task.updated_at = datetime.now(timezone.utc)
                self.tasks.put(task, references)
                return task, self.storage.get_bytes(preview_key).content
            except (FileNotFoundError, StorageObjectNotFound):
                return task, None

    @staticmethod
    def _legacy_preview(task_id: str, content: bytes):
        from PIL import Image
        from io import BytesIO
        with Image.open(BytesIO(content)) as image:
            width, height = image.size
        from .models import DetectionPreview
        return DetectionPreview(
            url=f"/api/ocr/tasks/{task_id}/detection-preview",
            storedName=f"{task_id}.png",
            contentType="image/png",
            size=len(content),
            sha256=hashlib.sha256(content).hexdigest(),
            width=width,
            height=height,
        )

    def _validate_archive_source_key(self, object_key: str) -> str:
        key = validate_object_key(object_key)
        if not key.startswith(self.allowed_source_prefix):
            raise ValueError("Source object key is outside the allowed archive prefix")
        return key

    def list_tasks(self, *, page: int, page_size: int) -> OcrTaskPage:
        tasks = self.tasks.list_tasks()
        offset = (page - 1) * page_size
        return OcrTaskPage(
            items=tasks[offset : offset + page_size],
            total=len(tasks),
            page=page,
            pageSize=page_size,
        )

    def statistics(self) -> OcrStatistics:
        tasks = self.tasks.list_tasks()
        failure_reasons: dict[str, int] = {}
        for task in tasks:
            if task.status == "FAILED" and task.error_message:
                failure_reasons[task.error_message] = failure_reasons.get(task.error_message, 0) + 1
        return OcrStatistics(
            totalTasks=len(tasks),
            succeededTasks=sum(task.status == "SUCCEEDED" for task in tasks),
            failedTasks=sum(task.status == "FAILED" for task in tasks),
            reviewRequiredTasks=sum(
                any(field.review_required for field in task.fields) for task in tasks
            ),
            fieldRevisionCount=self.tasks.count_field_revisions(),
            failureReasons=failure_reasons,
        )

    def evaluate(self, request: OcrEvaluationRequest) -> OcrEvaluationResult:
        return evaluate_samples(samples=request.samples, task_lookup=self.get_task)

    def correct_field(
        self,
        *,
        task: OcrTask,
        field_code: str,
        value: str,
        operator_id: str,
        reason: str | None,
    ) -> OcrTask | None:
        for field in task.fields:
            if field.field_code != field_code:
                continue
            previous_value = field.value
            current_value = value.strip()
            validation_passed, validation_message = self.employee_profile_extractor.validate(
                field_code,
                current_value,
            )
            field.value = current_value
            field.validation_status = "PASSED" if validation_passed else "FAILED"
            field.validation_message = validation_message
            # 人工确认已消除 OCR 置信度风险；仍不允许以无效格式关闭复核提示。
            field.review_required = not validation_passed
            now = datetime.now(timezone.utc)
            task.updated_at = now
            revision = FieldRevision(
                revisionId=uuid4().hex,
                taskId=task.task_id,
                fieldCode=field_code,
                previousValue=previous_value,
                currentValue=current_value,
                operatorId=operator_id,
                reason=reason,
                createdAt=now,
            )
            return self.tasks.update_task_with_revision(task, revision)
        return None

    def list_field_revisions(self, task_id: str) -> list[FieldRevision]:
        return self.tasks.list_field_revisions(task_id)


def task_data(task: OcrTask) -> dict:
    """按接口别名序列化任务，保证 datetime 等值可直接返回 JSON。"""

    return dump_model(task)
