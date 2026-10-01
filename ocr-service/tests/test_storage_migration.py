import sqlite3
from datetime import datetime, timezone

from app.models import OcrTask, dump_model
from app.object_storage import InMemoryObjectStorage
from app.store import TaskStore
from scripts.migrate_local_storage import migrate


def image_bytes() -> bytes:
    return b"source-image"


def task(task_id: str, updated_at: datetime | None = None) -> OcrTask:
    now = updated_at or datetime.now(timezone.utc)
    return OcrTask(
        taskId=task_id,
        status="SUCCEEDED",
        sourceFile={
            "originalName": "employee.png",
            "storedName": f"{task_id}.png",
            "contentType": "image/png",
            "size": len(image_bytes()),
            "sha256": __import__("hashlib").sha256(image_bytes()).hexdigest(),
        },
        detectionPreview={
            "url": f"/api/ocr/tasks/{task_id}/detection-preview",
            "storedName": f"{task_id}-preview.png",
            "contentType": "image/png",
            "size": 7,
            "sha256": __import__("hashlib").sha256(b"preview").hexdigest(),
            "width": 1,
            "height": 1,
        },
        createdAt=now,
        updatedAt=now,
    )


def write_task_database(directory, item: OcrTask):
    directory.mkdir(parents=True)
    with sqlite3.connect(directory / "ocr_tasks.sqlite3") as connection:
        connection.execute(
            "CREATE TABLE ocr_tasks (task_id TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at TEXT NOT NULL)"
        )
        connection.execute(
            "INSERT INTO ocr_tasks VALUES (?, ?, ?)",
            (item.task_id, __import__("json").dumps(dump_model(item)), item.updated_at.isoformat()),
        )
        connection.execute(
            "CREATE TABLE field_revisions (revision_id TEXT PRIMARY KEY, task_id TEXT NOT NULL, "
            "field_code TEXT NOT NULL, payload TEXT NOT NULL, created_at TEXT NOT NULL)"
        )


def test_dry_run_does_not_upload_or_delete_local_files(tmp_path):
    data_dir = tmp_path / "data"
    item = task("task-1")
    write_task_database(data_dir, item)
    (data_dir / item.source_file.stored_name).write_bytes(image_bytes())
    (data_dir / "previews").mkdir()
    (data_dir / "previews" / item.detection_preview.stored_name).write_bytes(b"preview")
    storage = InMemoryObjectStorage()

    result = migrate(data_dir, (), storage=storage, dry_run=True)

    assert result.errors == []
    assert storage.keys() == set()
    assert (data_dir / item.source_file.stored_name).exists()
    assert not (data_dir / "ocr_tasks.sqlite3").stat().st_size == 0


def test_successful_migration_is_idempotent_and_can_delete_verified_binaries(tmp_path):
    data_dir = tmp_path / "data"
    item = task("task-1")
    write_task_database(data_dir, item)
    (data_dir / item.source_file.stored_name).write_bytes(image_bytes())
    (data_dir / "previews").mkdir()
    (data_dir / "previews" / item.detection_preview.stored_name).write_bytes(b"preview")
    storage = InMemoryObjectStorage()

    first = migrate(data_dir, (), storage=storage)
    second = migrate(data_dir, (), storage=storage)

    assert first.errors == []
    assert second.errors == []
    assert first.uploaded_count == 2
    assert second.uploaded_count == 0
    assert set(storage.keys()) == {"ocr/source/" + item.source_file.sha256 + ".png", "ocr/preview/task-1.png"}

    deleted = migrate(data_dir, (), storage=storage, delete_local_binaries=True)
    assert deleted.errors == []
    assert not (data_dir / item.source_file.stored_name).exists()
    assert not (data_dir / "previews" / item.detection_preview.stored_name).exists()


def test_missing_binary_fails_without_deleting_other_files(tmp_path):
    data_dir = tmp_path / "data"
    item = task("task-1")
    write_task_database(data_dir, item)
    (data_dir / "previews").mkdir()
    (data_dir / "previews" / item.detection_preview.stored_name).write_bytes(b"preview")
    storage = InMemoryObjectStorage()

    result = migrate(data_dir, (), storage=storage, delete_local_binaries=True)

    assert result.errors
    assert storage.keys() == {"ocr/preview/task-1.png"}
    assert (data_dir / "previews" / item.detection_preview.stored_name).exists()


def test_orphan_source_images_are_migrated_before_local_cleanup(tmp_path):
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    with sqlite3.connect(data_dir / "ocr_tasks.sqlite3") as connection:
        connection.execute(
            "CREATE TABLE ocr_tasks (task_id TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at TEXT NOT NULL)"
        )
    orphan = data_dir / "sample.png"
    orphan.write_bytes(image_bytes())
    storage = InMemoryObjectStorage()

    result = migrate(data_dir, (), storage=storage, delete_local_binaries=True)

    assert result.errors == []
    assert storage.keys() == {
        "ocr/source/" + __import__("hashlib").sha256(image_bytes()).hexdigest() + ".png"
    }
    assert not orphan.exists()
