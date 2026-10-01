from __future__ import annotations

import os
from pathlib import Path


class Settings:
    """读取环境变量，并允许测试通过构造参数覆盖默认值。"""

    def __init__(
        self,
        *,
        engine: str | None = None,
        data_dir: Path | str | None = None,
        legacy_data_dir: Path | str | None = None,
        max_file_size_bytes: int | None = None,
        max_image_edge: int | None = None,
        low_confidence_threshold: float | None = None,
        minio_endpoint: str | None = None,
        minio_access_key: str | None = None,
        minio_secret_key: str | None = None,
        minio_bucket: str | None = None,
        minio_allowed_source_prefix: str | None = None,
        internal_token: str | None = None,
    ) -> None:
        self.engine = (engine or os.getenv("OCR_ENGINE", "paddle")).lower()
        configured_data_dir = os.getenv("OCR_DATA_DIR")
        if data_dir is not None:
            self.data_dir = Path(data_dir)
        else:
            if configured_data_dir is None:
                self.data_dir = Path(__file__).resolve().parents[1] / "data"
            else:
                configured_path = Path(configured_data_dir)
                self.data_dir = (
                    configured_path
                    if configured_path.is_absolute()
                    else Path(__file__).resolve().parents[1] / configured_path
                )
        default_data_dir = Path(__file__).resolve().parents[1] / "data"
        configured_legacy_data_dir = legacy_data_dir or os.getenv("OCR_LEGACY_DATA_DIR")
        if configured_legacy_data_dir is not None:
            legacy_path = Path(configured_legacy_data_dir)
            self.legacy_data_dirs = [
                legacy_path
                if legacy_path.is_absolute()
                else Path(__file__).resolve().parents[1] / legacy_path
            ]
        elif self.data_dir == default_data_dir:
            self.legacy_data_dirs = [Path(__file__).resolve().parent / "data"]
        else:
            self.legacy_data_dirs = []
        self.max_file_size_bytes = max_file_size_bytes or int(
            os.getenv("OCR_MAX_FILE_SIZE_BYTES", str(10 * 1024 * 1024))
        )
        self.max_image_edge = max_image_edge or int(
            os.getenv("OCR_MAX_IMAGE_EDGE", "4096")
        )
        self.low_confidence_threshold = (
            low_confidence_threshold
            if low_confidence_threshold is not None
            else float(os.getenv("OCR_LOW_CONFIDENCE_THRESHOLD", "0.85"))
        )
        if not 0 <= self.low_confidence_threshold <= 1:
            raise ValueError("OCR_LOW_CONFIDENCE_THRESHOLD must be between zero and one")
        self.minio_endpoint = minio_endpoint or os.getenv(
            "MINIO_ENDPOINT", "http://127.0.0.1:9000"
        )
        self.minio_access_key = minio_access_key or os.getenv("MINIO_ACCESS_KEY", "minioadmin")
        self.minio_secret_key = minio_secret_key or os.getenv(
            "MINIO_SECRET_KEY", "change-me-minio-password"
        )
        self.minio_bucket = minio_bucket or os.getenv("MINIO_BUCKET", "hr-platform")
        self.minio_allowed_source_prefix = minio_allowed_source_prefix or os.getenv(
            "MINIO_ALLOWED_SOURCE_PREFIX", "archive/"
        )
        self.internal_token = internal_token or os.getenv(
            "OCR_INTERNAL_TOKEN", "local-ocr-internal-token"
        )


# MIME 类型校验只是第一层，接口层还会使用 Pillow 验证文件内容是否真的是图片。
SUPPORTED_IMAGE_TYPES = {"image/png", "image/jpeg", "image/jpg"}
SUPPORTED_DOCUMENT_TYPES = {"employee_profile"}
