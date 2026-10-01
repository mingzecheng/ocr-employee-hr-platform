from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import sqlite3
import sys

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import Settings
from app.models import OcrTask
from app.object_storage import (
    MinioObjectStorage,
    ObjectStorage,
    StorageObjectNotFound,
)
from app.store import StorageReferences, TaskStore


@dataclass
class MigrationResult:
    uploaded_count: int = 0
    merged_count: int = 0
    eligible_deletions: list[Path] | None = None
    errors: list[str] | None = None

    def __post_init__(self) -> None:
        if self.eligible_deletions is None:
            self.eligible_deletions = []
        if self.errors is None:
            self.errors = []


def _read_tasks(database_path: Path) -> list[tuple[OcrTask, StorageReferences]]:
    if not database_path.is_file():
        return []
    with sqlite3.connect(database_path) as connection:
        columns = {row[1] for row in connection.execute("PRAGMA table_info(ocr_tasks)")}
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
        rows = connection.execute(f"SELECT {selected} FROM ocr_tasks").fetchall()
    result = []
    for row in rows:
        values = dict(zip(reference_columns, row[1:]))
        result.append(
            (
                OcrTask.model_validate(json.loads(row[0])),
                StorageReferences(
                    source_bucket=values.get("source_bucket"),
                    source_object_key=values.get("source_object_key"),
                    preview_bucket=values.get("preview_bucket"),
                    preview_object_key=values.get("preview_object_key"),
                    storage_version=values.get("storage_version"),
                ),
            )
        )
    return result


def _database_paths(data_dir: Path, legacy_data_dirs: tuple[Path, ...]) -> list[Path]:
    return list(
        dict.fromkeys(
            [data_dir / "ocr_tasks.sqlite3"]
            + [directory / "ocr_tasks.sqlite3" for directory in legacy_data_dirs]
        )
    )


def _local_file(directory: Path, stored_name: str, *, preview: bool) -> Path | None:
    candidate = Path(stored_name)
    if candidate.name != stored_name or (preview and candidate.suffix.lower() != ".png"):
        raise ValueError(f"Invalid stored file name: {stored_name}")
    path = directory / "previews" / candidate if preview else directory / candidate
    return path if path.is_file() else None


def _find_local_file(
    directories: tuple[Path, ...], stored_name: str, *, preview: bool
) -> Path | None:
    for directory in directories:
        path = _local_file(directory, stored_name, preview=preview)
        if path is not None:
            return path
    return None


def _image_content_type(path: Path) -> str:
    return "image/jpeg" if path.suffix.lower() in {".jpg", ".jpeg"} else "image/png"


def _upload_and_verify(
    storage: ObjectStorage,
    key: str,
    content_type: str,
    content: bytes,
    result: MigrationResult,
) -> bool:
    expected_hash = hashlib.sha256(content).hexdigest()
    try:
        existing = storage.get_bytes(key)
        if existing.content != content:
            result.errors.append(f"Object hash mismatch for existing key: {key}")
            return False
        return True
    except StorageObjectNotFound:
        storage.put_bytes(key, content_type, content)
        result.uploaded_count += 1
        actual = storage.get_bytes(key)
        if actual.size != len(content) or actual.sha256 != expected_hash:
            result.errors.append(f"Object read-back verification failed: {key}")
            return False
        return True


def _merge_revisions(active_database: Path, source_databases: list[Path]) -> None:
    with sqlite3.connect(active_database) as target:
        target.execute(
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
        for database_path in source_databases:
            if not database_path.is_file():
                continue
            with sqlite3.connect(database_path) as source:
                columns = {row[1] for row in source.execute("PRAGMA table_info(field_revisions)")}
                if not {"revision_id", "task_id", "field_code", "payload", "created_at"} <= columns:
                    continue
                rows = source.execute(
                    "SELECT revision_id, task_id, field_code, payload, created_at FROM field_revisions"
                ).fetchall()
            target.executemany(
                "INSERT OR IGNORE INTO field_revisions "
                "(revision_id, task_id, field_code, payload, created_at) VALUES (?, ?, ?, ?, ?)",
                rows,
            )


def migrate(
    data_dir: Path,
    legacy_data_dirs: tuple[Path, ...],
    *,
    storage: ObjectStorage,
    dry_run: bool = False,
    delete_local_binaries: bool = False,
) -> MigrationResult:
    data_dir = Path(data_dir)
    directories = tuple(dict.fromkeys((data_dir, *legacy_data_dirs)))
    database_paths = _database_paths(data_dir, legacy_data_dirs)
    result = MigrationResult()
    selected: dict[str, tuple[OcrTask, StorageReferences]] = {}
    for database_path in database_paths:
        for item, references in _read_tasks(database_path):
            current = selected.get(item.task_id)
            if current is None or item.updated_at > current[0].updated_at:
                selected[item.task_id] = (item, references)

    task_references: dict[str, StorageReferences] = {}
    deletions: dict[Path, bool] = {}
    for item, references in selected.values():
        source_key = references.source_object_key
        source_verified = False
        source_path = None
        if source_key:
            try:
                source_verified = storage.get_bytes(source_key).sha256 == item.source_file.sha256
            except StorageObjectNotFound:
                source_key = None
        if source_key is None:
            source_path = _find_local_file(directories, item.source_file.stored_name, preview=False)
            if source_path is None:
                result.errors.append(f"Source file not found for task {item.task_id}")
            else:
                content = source_path.read_bytes()
                expected = item.source_file.sha256
                if hashlib.sha256(content).hexdigest() != expected:
                    result.errors.append(f"Source hash mismatch for task {item.task_id}")
                else:
                    suffix = Path(item.source_file.stored_name).suffix.lower() or ".bin"
                    source_key = f"ocr/source/{expected}{suffix}"
                    source_verified = True
                    if not dry_run and _upload_and_verify(
                        storage, source_key, item.source_file.content_type, content, result
                    ):
                        deletions[source_path] = True
                    elif dry_run:
                        deletions[source_path] = True
        if source_key and source_verified and source_path is None:
            source_path = _find_local_file(directories, item.source_file.stored_name, preview=False)
            if source_path is not None:
                deletions[source_path] = True
        preview_key = references.preview_object_key
        preview_verified = False
        preview_path = None
        if preview_key:
            try:
                preview_verified = storage.get_bytes(preview_key).sha256 == item.detection_preview.sha256
            except (StorageObjectNotFound, AttributeError):
                preview_key = None
        if item.detection_preview is not None and preview_key is None:
            preview_path = _find_local_file(
                directories, item.detection_preview.stored_name, preview=True
            )
            if preview_path is None:
                result.errors.append(f"Preview file not found for task {item.task_id}")
            else:
                content = preview_path.read_bytes()
                expected = item.detection_preview.sha256
                if hashlib.sha256(content).hexdigest() != expected:
                    result.errors.append(f"Preview hash mismatch for task {item.task_id}")
                else:
                    preview_key = f"ocr/preview/{item.task_id}.png"
                    preview_verified = True
                    if not dry_run and _upload_and_verify(
                        storage, preview_key, "image/png", content, result
                    ):
                        deletions[preview_path] = True
                    elif dry_run:
                        deletions[preview_path] = True
        if preview_key and preview_verified and preview_path is None and item.detection_preview is not None:
            preview_path = _find_local_file(
                directories, item.detection_preview.stored_name, preview=True
            )
            if preview_path is not None:
                deletions[preview_path] = True
        if source_key and source_verified:
            task_references[item.task_id] = StorageReferences(
                source_bucket=getattr(storage, "bucket", "hr-platform"),
                source_object_key=source_key,
                preview_bucket=getattr(storage, "bucket", "hr-platform") if preview_key and preview_verified else None,
                preview_object_key=preview_key if preview_key and preview_verified else None,
                storage_version=1,
            )

    # Preserve old standalone samples in MinIO before removing the second local copy.
    for directory in directories:
        if not directory.is_dir():
            continue
        for path in directory.iterdir():
            if (
                not path.is_file()
                or path in deletions
                or path.suffix.lower() not in {".png", ".jpg", ".jpeg"}
            ):
                continue
            content = path.read_bytes()
            key = f"ocr/source/{hashlib.sha256(content).hexdigest()}{path.suffix.lower()}"
            if dry_run:
                deletions[path] = True
            elif _upload_and_verify(storage, key, _image_content_type(path), content, result):
                deletions[path] = True

    if not dry_run:
        active_store = TaskStore(data_dir)
        for item, _ in selected.values():
            active_store.put(item, task_references.get(item.task_id))
            if item.task_id in task_references:
                result.merged_count += 1
        _merge_revisions(data_dir / "ocr_tasks.sqlite3", database_paths)
        if delete_local_binaries and not result.errors:
            for path in deletions:
                path.unlink(missing_ok=True)
                result.eligible_deletions.append(path)
    else:
        result.eligible_deletions.extend(deletions)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description="Migrate OCR local binaries into MinIO")
    parser.add_argument("--data-dir", type=Path, default=Path("data"))
    parser.add_argument("--legacy-data-dir", type=Path, action="append", default=[])
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--delete-local-binaries", action="store_true")
    args = parser.parse_args()
    settings = Settings(data_dir=args.data_dir, legacy_data_dir=args.legacy_data_dir[0] if args.legacy_data_dir else None)
    storage = MinioObjectStorage(
        endpoint=settings.minio_endpoint,
        access_key=settings.minio_access_key,
        secret_key=settings.minio_secret_key,
        bucket=settings.minio_bucket,
    )
    result = migrate(
        args.data_dir,
        tuple(args.legacy_data_dir),
        storage=storage,
        dry_run=args.dry_run,
        delete_local_binaries=args.delete_local_binaries,
    )
    print(f"uploaded={result.uploaded_count} merged={result.merged_count}")
    for path in result.eligible_deletions:
        print(f"eligible-delete={path}")
    for error in result.errors:
        print(f"ERROR: {error}", file=sys.stderr)
    return 1 if result.errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
