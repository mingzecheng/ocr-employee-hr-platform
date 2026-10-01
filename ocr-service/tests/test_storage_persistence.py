import json
import sqlite3
from datetime import datetime, timezone

from app.models import OcrTask, dump_model
from app.store import StorageReferences, TaskStore


def sample_task() -> OcrTask:
    now = datetime.now(timezone.utc)
    return OcrTask(
        taskId="task-1",
        status="SUCCEEDED",
        sourceFile={
            "originalName": "a.png",
            "storedName": "a.png",
            "contentType": "image/png",
            "size": 3,
            "sha256": "a" * 64,
        },
        createdAt=now,
        updatedAt=now,
    )


def test_task_store_persists_internal_object_keys_without_public_response_fields(tmp_path):
    store = TaskStore(tmp_path)
    task = sample_task()
    references = StorageReferences(
        source_bucket="hr-platform",
        source_object_key="archive/employee/1/a.png",
        preview_bucket="hr-platform",
        preview_object_key="ocr/preview/task-1.png",
        storage_version=1,
    )

    store.put(task, references)
    loaded, refs = store.get_with_references(task.task_id)

    assert loaded == task
    assert refs == references
    assert "sourceObjectKey" not in dump_model(loaded)


def test_task_store_adds_reference_columns_to_old_database(tmp_path):
    database_path = tmp_path / "ocr_tasks.sqlite3"
    payload = json.dumps(dump_model(sample_task()))
    with sqlite3.connect(database_path) as connection:
        connection.execute(
            "CREATE TABLE ocr_tasks (task_id TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at TEXT NOT NULL)"
        )
        connection.execute(
            "INSERT INTO ocr_tasks VALUES (?, ?, ?)",
            ("task-1", payload, sample_task().updated_at.isoformat()),
        )

    store = TaskStore(tmp_path)
    loaded, references = store.get_with_references("task-1")

    columns = {
        row[1]
        for row in sqlite3.connect(database_path).execute("PRAGMA table_info(ocr_tasks)")
    }
    assert {
        "source_bucket",
        "source_object_key",
        "preview_bucket",
        "preview_object_key",
        "storage_version",
    } <= columns
    assert loaded.task_id == "task-1"
    assert references.source_object_key is None
