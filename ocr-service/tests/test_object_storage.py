import pytest

from app.object_storage import InMemoryObjectStorage, StorageObjectNotFound


def test_put_bytes_is_idempotent_for_same_key_and_content():
    storage = InMemoryObjectStorage()

    first = storage.put_bytes("ocr/source/a.png", "image/png", b"png")
    second = storage.put_bytes("ocr/source/a.png", "image/png", b"png")

    assert first.sha256 == second.sha256
    assert storage.get_bytes("ocr/source/a.png").content == b"png"
    assert storage.keys() == {"ocr/source/a.png"}


def test_missing_object_raises_storage_not_found():
    with pytest.raises(StorageObjectNotFound):
        InMemoryObjectStorage().get_bytes("archive/missing.png")
