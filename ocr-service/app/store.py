from __future__ import annotations

import hashlib
import json
import sqlite3
from dataclasses import dataclass
from pathlib import Path
from tempfile import TemporaryDirectory

from .models import DetectionPreview, FieldRevision, OcrTask, SourceFile, TextBlock, dump_model
from .object_storage import ObjectStorage, StorageObjectNotFound
from .visualization import render_detection_preview


@dataclass(frozen=True)
class StorageReferences:
    source_bucket: str | None = None
    source_object_key: str | None = None
    preview_bucket: str | None = None
    preview_object_key: str | None = None
    storage_version: int | None = None


class SourceFileStore:
    """将 OCR 二进制证据保存到对象存储，旧目录只用于迁移期读取。"""

    def __init__(
        self,
        data_dir: Path,
        legacy_data_dirs: tuple[Path, ...] = (),
        *,
        storage: ObjectStorage,
        bucket: str,
    ) -> None:
        self.data_dir = data_dir
        self.data_dirs = tuple(dict.fromkeys((data_dir, *legacy_data_dirs)))
        self.storage = storage
        self.bucket = bucket
        self.data_dir.mkdir(parents=True, exist_ok=True)

    def save(self, *, original_name: str, content_type: str, content: bytes) -> tuple[SourceFile, str]:
        suffix = Path(original_name).suffix.lower() or ".bin"
        digest = hashlib.sha256(content).hexdigest()
        object_key = f"ocr/source/{digest}{suffix}"
        try:
            self.storage.stat(object_key)
        except StorageObjectNotFound:
            self.storage.put_bytes(object_key, content_type, content)
        return SourceFile(
            originalName=original_name,
            storedName=Path(object_key).name,
            contentType=content_type,
            size=len(content),
            sha256=digest,
        ), object_key

    def register_reference(
        self,
        *,
        original_name: str,
        content_type: str,
        size: int,
        sha256: str,
        object_key: str,
    ) -> SourceFile:
        return SourceFile(
            originalName=original_name,
            storedName=Path(object_key).name,
            contentType=content_type,
            size=size,
            sha256=sha256,
        )

    def save_detection_preview(
        self,
        *,
        task_id: str,
        source_file: SourceFile,
        text_blocks: list[TextBlock],
        source_path: Path,
    ) -> DetectionPreview:
        del source_file
        with TemporaryDirectory(prefix="ocr-preview-") as directory:
            preview_path = Path(directory) / f"{task_id}.png"
            width, height = render_detection_preview(
                source_path=source_path,
                output_path=preview_path,
                text_blocks=text_blocks,
            )
            preview_content = preview_path.read_bytes()
        object_key = f"ocr/preview/{task_id}.png"
        self.storage.put_bytes(object_key, "image/png", preview_content)
        return DetectionPreview(
            url=f"/api/ocr/tasks/{task_id}/detection-preview",
            storedName=f"{task_id}.png",
            contentType="image/png",
            size=len(preview_content),
            sha256=hashlib.sha256(preview_content).hexdigest(),
            width=width,
            height=height,
        )

    def preview_bytes_from_legacy(self, preview: DetectionPreview | None) -> bytes | None:
        if preview is None:
            return None
        stored_path = self._safe_file_name(preview.stored_name, suffix=".png")
        for data_dir in self.data_dirs:
            candidate = data_dir / "previews" / stored_path
            if candidate.is_file():
                return candidate.read_bytes()
        return None

    def source_path(self, stored_name: str) -> Path | None:
        stored_path = self._safe_file_name(stored_name)
        for data_dir in self.data_dirs:
            candidate = data_dir / stored_path
            if candidate.is_file():
                return candidate
        return None

    @staticmethod
    def _safe_file_name(stored_name: str, suffix: str | None = None) -> Path:
        stored_path = Path(stored_name)
        if stored_path.name != stored_name or (suffix and stored_path.suffix.lower() != suffix):
            raise ValueError("Invalid stored file name")
        return stored_path


class TaskStore:
    """使用本地 SQLite 保存 OCR 任务，服务重启后仍可查询识别证据。"""

    def __init__(self, data_dir: Path, legacy_data_dirs: tuple[Path, ...] = ()) -> None:
        data_dir.mkdir(parents=True, exist_ok=True)
        self.database_path = data_dir / "ocr_tasks.sqlite3"
        candidate_paths = (
            self.database_path,
            *(legacy_dir / "ocr_tasks.sqlite3" for legacy_dir in legacy_data_dirs),
        )
        self.database_paths = tuple(
            dict.fromkeys(
                path
                for path in candidate_paths
                if path.is_file() or path == self.database_path
            )
        )
        self._initialize()

    def _initialize(self) -> None:
        with sqlite3.connect(self.database_path) as connection:
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS ocr_tasks (
                    task_id TEXT PRIMARY KEY,
                    payload TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    source_bucket TEXT,
                    source_object_key TEXT,
                    preview_bucket TEXT,
                    preview_object_key TEXT,
                    storage_version INTEGER
                )
                """
            )
            existing_columns = {
                row[1] for row in connection.execute("PRAGMA table_info(ocr_tasks)")
            }
            for column, definition in (
                ("source_bucket", "TEXT"),
                ("source_object_key", "TEXT"),
                ("preview_bucket", "TEXT"),
                ("preview_object_key", "TEXT"),
                ("storage_version", "INTEGER"),
            ):
                if column not in existing_columns:
                    connection.execute(f"ALTER TABLE ocr_tasks ADD COLUMN {column} {definition}")
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS field_revisions (
                    revision_id TEXT PRIMARY KEY,
                    task_id TEXT NOT NULL,
                    field_code TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
                """
            )
            connection.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_field_revisions_task_id_created_at
                ON field_revisions (task_id, created_at)
                """
            )

    def put(
        self,
        task: OcrTask,
        references: StorageReferences | None = None,
    ) -> OcrTask:
        payload = json.dumps(dump_model(task), ensure_ascii=False, separators=(",", ":"))
        with sqlite3.connect(self.database_path) as connection:
            connection.execute(
                """
                INSERT INTO ocr_tasks (
                    task_id, payload, updated_at, source_bucket, source_object_key,
                    preview_bucket, preview_object_key, storage_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(task_id) DO UPDATE SET
                    payload = excluded.payload,
                    updated_at = excluded.updated_at,
                    source_bucket = COALESCE(excluded.source_bucket, ocr_tasks.source_bucket),
                    source_object_key = COALESCE(excluded.source_object_key, ocr_tasks.source_object_key),
                    preview_bucket = COALESCE(excluded.preview_bucket, ocr_tasks.preview_bucket),
                    preview_object_key = COALESCE(excluded.preview_object_key, ocr_tasks.preview_object_key),
                    storage_version = COALESCE(excluded.storage_version, ocr_tasks.storage_version)
                """,
                (
                    task.task_id,
                    payload,
                    task.updated_at.isoformat(),
                    references.source_bucket if references else None,
                    references.source_object_key if references else None,
                    references.preview_bucket if references else None,
                    references.preview_object_key if references else None,
                    references.storage_version if references else None,
                ),
            )
        return task

    def get(self, task_id: str) -> OcrTask | None:
        result = self.get_with_references(task_id)
        return result[0] if result else None

    def get_with_references(
        self, task_id: str
    ) -> tuple[OcrTask, StorageReferences] | None:
        for database_path in self.database_paths:
            if not database_path.is_file():
                continue
            with sqlite3.connect(database_path) as connection:
                columns = self._task_columns(connection)
                reference_columns = [
                    column
                    for column in (
                        "source_bucket",
                        "source_object_key",
                        "preview_bucket",
                        "preview_object_key",
                        "storage_version",
                    )
                    if column in columns
                ]
                selected = ", ".join(["payload", *reference_columns])
                row = connection.execute(
                    f"SELECT {selected} FROM ocr_tasks WHERE task_id = ?",
                    (task_id,),
                ).fetchone()
            if row is not None:
                values = dict(zip(reference_columns, row[1:]))
                return OcrTask.model_validate(json.loads(row[0])), StorageReferences(
                    source_bucket=values.get("source_bucket"),
                    source_object_key=values.get("source_object_key"),
                    preview_bucket=values.get("preview_bucket"),
                    preview_object_key=values.get("preview_object_key"),
                    storage_version=values.get("storage_version"),
                )
        return None

    @staticmethod
    def _task_columns(connection: sqlite3.Connection) -> set[str]:
        return {row[1] for row in connection.execute("PRAGMA table_info(ocr_tasks)")}

    def list_tasks(self) -> list[OcrTask]:
        tasks_by_id: dict[str, OcrTask] = {}
        for database_path in self.database_paths:
            if not database_path.is_file():
                continue
            with sqlite3.connect(database_path) as connection:
                rows = connection.execute("SELECT payload FROM ocr_tasks").fetchall()
            for row in rows:
                task = OcrTask.model_validate(json.loads(row[0]))
                tasks_by_id.setdefault(task.task_id, task)
        tasks = list(tasks_by_id.values())
        return sorted(tasks, key=lambda task: (task.created_at, task.task_id), reverse=True)

    def update_task_with_revision(self, task: OcrTask, revision: FieldRevision) -> OcrTask:
        task_payload = json.dumps(
            dump_model(task),
            ensure_ascii=False,
            separators=(",", ":"),
        )
        revision_payload = json.dumps(
            dump_model(revision),
            ensure_ascii=False,
            separators=(",", ":"),
        )
        with sqlite3.connect(self.database_path) as connection:
            connection.execute(
                """
                INSERT INTO ocr_tasks (
                    task_id, payload, updated_at, source_bucket, source_object_key,
                    preview_bucket, preview_object_key, storage_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(task_id) DO UPDATE SET
                    payload = excluded.payload,
                    updated_at = excluded.updated_at,
                    source_bucket = COALESCE(excluded.source_bucket, ocr_tasks.source_bucket),
                    source_object_key = COALESCE(excluded.source_object_key, ocr_tasks.source_object_key),
                    preview_bucket = COALESCE(excluded.preview_bucket, ocr_tasks.preview_bucket),
                    preview_object_key = COALESCE(excluded.preview_object_key, ocr_tasks.preview_object_key),
                    storage_version = COALESCE(excluded.storage_version, ocr_tasks.storage_version)
                """,
                (
                    task.task_id,
                    task_payload,
                    task.updated_at.isoformat(),
                    *(self._reference_values(task.task_id)),
                ),
            )
            connection.execute(
                """
                INSERT INTO field_revisions (
                    revision_id, task_id, field_code, payload, created_at
                ) VALUES (?, ?, ?, ?, ?)
                """,
                (
                    revision.revision_id,
                    revision.task_id,
                    revision.field_code,
                    revision_payload,
                    revision.created_at.isoformat(),
                ),
            )
        return task

    def _reference_values(self, task_id: str) -> tuple[str | None, ...]:
        result = self.get_with_references(task_id)
        if result is None:
            return (None, None, None, None, None)
        references = result[1]
        return (
            references.source_bucket,
            references.source_object_key,
            references.preview_bucket,
            references.preview_object_key,
            references.storage_version,
        )

    def list_field_revisions(self, task_id: str) -> list[FieldRevision]:
        revisions_by_id: dict[str, FieldRevision] = {}
        for database_path in self.database_paths:
            if not database_path.is_file():
                continue
            with sqlite3.connect(database_path) as connection:
                rows = connection.execute(
                    """
                    SELECT payload FROM field_revisions
                    WHERE task_id = ?
                    ORDER BY created_at ASC, revision_id ASC
                    """,
                    (task_id,),
                ).fetchall()
            for row in rows:
                revision = FieldRevision.model_validate(json.loads(row[0]))
                revisions_by_id.setdefault(revision.revision_id, revision)
        return sorted(
            revisions_by_id.values(),
            key=lambda revision: (revision.created_at, revision.revision_id),
        )

    def count_field_revisions(self) -> int:
        revision_ids: set[str] = set()
        for database_path in self.database_paths:
            if not database_path.is_file():
                continue
            with sqlite3.connect(database_path) as connection:
                rows = connection.execute("SELECT revision_id FROM field_revisions").fetchall()
            revision_ids.update(row[0] for row in rows)
        return len(revision_ids)
