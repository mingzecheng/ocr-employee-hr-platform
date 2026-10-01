from __future__ import annotations

from dataclasses import dataclass
from hashlib import sha256
from io import BytesIO
from pathlib import Path
from typing import Protocol
from urllib.parse import urlparse


@dataclass(frozen=True)
class StoredObject:
    key: str
    content_type: str
    size: int
    sha256: str
    content: bytes | None = None


class StorageError(RuntimeError):
    """Base error for object storage failures."""


class StorageObjectNotFound(StorageError):
    pass


class StorageUnavailable(StorageError):
    pass


class ObjectStorage(Protocol):
    bucket: str

    def put_bytes(self, key: str, content_type: str, content: bytes) -> StoredObject: ...

    def get_bytes(self, key: str) -> StoredObject: ...

    def download_to_path(self, key: str, path: Path) -> StoredObject: ...

    def stat(self, key: str) -> StoredObject: ...

    def delete(self, key: str) -> None: ...


def validate_object_key(key: str) -> str:
    if not key or key.startswith("/"):
        raise ValueError("Invalid object key")
    parts = key.split("/")
    if any(part in {"", ".", ".."} for part in parts):
        raise ValueError("Invalid object key")
    return key


def _stored(key: str, content_type: str, content: bytes | None, size: int | None = None) -> StoredObject:
    return StoredObject(
        key=key,
        content_type=content_type or "application/octet-stream",
        size=len(content) if content is not None else int(size or 0),
        sha256=sha256(content).hexdigest() if content is not None else "",
        content=content,
    )


class InMemoryObjectStorage:
    """Deterministic storage double used by OCR tests and migration checks."""

    bucket = "hr-platform"

    def __init__(self, objects: dict[str, bytes] | None = None) -> None:
        self._objects: dict[str, tuple[str, bytes]] = {}
        for key, content in (objects or {}).items():
            self._objects[validate_object_key(key)] = ("image/png", content)

    def put_bytes(self, key: str, content_type: str, content: bytes) -> StoredObject:
        key = validate_object_key(key)
        self._objects[key] = (content_type, bytes(content))
        return _stored(key, content_type, bytes(content))

    def get_bytes(self, key: str) -> StoredObject:
        key = validate_object_key(key)
        try:
            content_type, content = self._objects[key]
        except KeyError as exc:
            raise StorageObjectNotFound(f"Object not found: {key}") from exc
        return _stored(key, content_type, content)

    def download_to_path(self, key: str, path: Path) -> StoredObject:
        stored = self.get_bytes(key)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(stored.content or b"")
        return stored

    def stat(self, key: str) -> StoredObject:
        stored = self.get_bytes(key)
        return StoredObject(stored.key, stored.content_type, stored.size, stored.sha256)

    def delete(self, key: str) -> None:
        self._objects.pop(validate_object_key(key), None)

    def keys(self) -> set[str]:
        return set(self._objects)


class MinioObjectStorage:
    def __init__(
        self,
        *,
        endpoint: str,
        access_key: str,
        secret_key: str,
        bucket: str,
    ) -> None:
        self.bucket = bucket
        parsed = urlparse(endpoint if "://" in endpoint else f"http://{endpoint}")
        self._client = self._build_client(parsed, access_key, secret_key)

    @staticmethod
    def _build_client(parsed, access_key: str, secret_key: str):
        try:
            from minio import Minio
        except ImportError as exc:
            raise StorageUnavailable("minio package is not installed") from exc
        return Minio(
            parsed.netloc,
            access_key=access_key,
            secret_key=secret_key,
            secure=parsed.scheme == "https",
        )

    def put_bytes(self, key: str, content_type: str, content: bytes) -> StoredObject:
        from minio.error import S3Error

        key = validate_object_key(key)
        try:
            self._ensure_bucket()
            self._client.put_object(
                self.bucket,
                key,
                BytesIO(content),
                length=len(content),
                content_type=content_type,
            )
            return _stored(key, content_type, content)
        except S3Error as exc:
            raise StorageUnavailable(f"Failed to store object: {exc.code}") from exc
        except StorageError:
            raise
        except Exception as exc:
            raise StorageUnavailable("Failed to store object") from exc

    def get_bytes(self, key: str) -> StoredObject:
        key = validate_object_key(key)
        try:
            response = self._client.get_object(self.bucket, key)
            try:
                content = response.read()
                content_type = response.headers.get("Content-Type", "application/octet-stream")
            finally:
                response.close()
                response.release_conn()
            return _stored(key, content_type, content)
        except Exception as exc:
            self._raise_storage_error(exc, key)
            raise AssertionError("unreachable")

    def download_to_path(self, key: str, path: Path) -> StoredObject:
        key = validate_object_key(key)
        try:
            response = self._client.get_object(self.bucket, key)
            digest = sha256()
            size = 0
            path.parent.mkdir(parents=True, exist_ok=True)
            try:
                with path.open("wb") as output:
                    while chunk := response.read(1024 * 1024):
                        output.write(chunk)
                        digest.update(chunk)
                        size += len(chunk)
                content_type = response.headers.get("Content-Type", "application/octet-stream")
            finally:
                response.close()
                response.release_conn()
            return StoredObject(key, content_type, size, digest.hexdigest())
        except Exception as exc:
            self._raise_storage_error(exc, key)
            raise AssertionError("unreachable")

    def stat(self, key: str) -> StoredObject:
        key = validate_object_key(key)
        try:
            result = self._client.stat_object(self.bucket, key)
            return StoredObject(
                key=key,
                content_type=result.content_type or "application/octet-stream",
                size=result.size,
                sha256=result.etag.strip('"') if result.etag else "",
            )
        except Exception as exc:
            self._raise_storage_error(exc, key)
            raise AssertionError("unreachable")

    def delete(self, key: str) -> None:
        key = validate_object_key(key)
        try:
            self._client.remove_object(self.bucket, key)
        except Exception as exc:
            self._raise_storage_error(exc, key)

    def _ensure_bucket(self) -> None:
        if not self._client.bucket_exists(self.bucket):
            self._client.make_bucket(self.bucket)

    def _raise_storage_error(self, exc: Exception, key: str) -> None:
        try:
            from minio.error import S3Error
        except ImportError:
            raise StorageUnavailable("MinIO operation failed") from exc
        if isinstance(exc, S3Error) and exc.code in {"NoSuchKey", "NoSuchObject", "NoSuchBucket"}:
            raise StorageObjectNotFound(f"Object not found: {key}") from exc
        raise StorageUnavailable("MinIO operation failed") from exc
