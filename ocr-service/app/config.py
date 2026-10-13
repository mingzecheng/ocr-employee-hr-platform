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
        self.environment = os.getenv("OCR_ENV", "development").strip().lower()
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
        # 本科演示规模：用信号量限制同时识别的图片数，避免 PaddleOCR 把进程 CPU/显存打满；
        # 单个识别任务超过超时阈值即判失败，防止慢任务永久悬挂占着请求线程。
        self.task_timeout_seconds = float(
            os.getenv("OCR_TASK_TIMEOUT_SECONDS", "60")
        )
        if self.task_timeout_seconds <= 0:
            raise ValueError("OCR_TASK_TIMEOUT_SECONDS must be greater than zero")
        self.max_concurrent_tasks = int(
            os.getenv("OCR_MAX_CONCURRENT_TASKS", "2")
        )
        if self.max_concurrent_tasks < 1:
            raise ValueError("OCR_MAX_CONCURRENT_TASKS must be at least one")
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
        if self.environment == "production":
            self._validate_production()

    def _validate_production(self) -> None:
        invalid: list[str] = []
        if self.engine != "paddle":
            invalid.append("OCR_ENGINE must be paddle")

        data_dir = os.getenv("OCR_DATA_DIR", "")
        if not data_dir or not Path(data_dir).is_absolute():
            invalid.append("OCR_DATA_DIR must be an explicit absolute persistent path")

        if not os.getenv("MINIO_ENDPOINT") or self.minio_endpoint == "http://127.0.0.1:9000":
            invalid.append("MINIO_ENDPOINT must be configured")
        access_key = os.getenv("MINIO_ACCESS_KEY", "")
        if len(access_key) < 8 or access_key == "minioadmin":
            invalid.append("MINIO_ACCESS_KEY must be a production credential")
        secret_key = os.getenv("MINIO_SECRET_KEY", "")
        if len(secret_key) < 16 or secret_key == "change-me-minio-password":
            invalid.append("MINIO_SECRET_KEY must contain at least 16 characters")
        if not os.getenv("MINIO_BUCKET"):
            invalid.append("MINIO_BUCKET must be configured")

        internal_token = os.getenv("OCR_INTERNAL_TOKEN", "")
        if len(internal_token) < 32 or internal_token == "local-ocr-internal-token":
            invalid.append("OCR_INTERNAL_TOKEN must contain at least 32 characters")

        if invalid:
            raise ValueError("Invalid production OCR configuration: " + "; ".join(invalid))


# MIME 类型校验只是第一层，接口层还会使用 Pillow 验证文件内容是否真的是图片。
SUPPORTED_IMAGE_TYPES = {"image/png", "image/jpeg", "image/jpg"}
SUPPORTED_DOCUMENT_TYPES = {"employee_profile"}
