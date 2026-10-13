from __future__ import annotations

import os
import socket
import subprocess
import sys
import time
from pathlib import Path
from urllib.request import urlopen

import pytest


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_STARTUP_TIMEOUT_SECONDS = 30


def service_python() -> Path:
    """Prefer the documented local-cache runtime over a cloud-synced venv."""
    configured = os.getenv("HR_OCR_VENV_DIR")
    if configured:
        candidate = Path(configured).expanduser() / "bin" / "python"
    elif sys.platform == "darwin":
        candidate = Path.home() / "Library" / "Caches" / "hr-platform" / "ocr-service-venv" / "bin" / "python"
    else:
        candidate = Path.home() / ".cache" / "hr-platform" / "ocr-service-venv" / "bin" / "python"
    return candidate if candidate.exists() else Path(sys.executable)


def stop_process(process: subprocess.Popen[str]) -> str:
    """Stop a child process and return its buffered output for diagnostics."""
    if process.poll() is None:
        process.terminate()
    try:
        output, _ = process.communicate(timeout=5)
    except subprocess.TimeoutExpired:
        process.kill()
        output, _ = process.communicate(timeout=5)
    return output or ""


def free_port() -> int:
    with socket.socket() as server_socket:
        server_socket.bind(("127.0.0.1", 0))
        return int(server_socket.getsockname()[1])


def test_main_script_starts_health_endpoint(tmp_path):
    port = free_port()
    environment = {
        **os.environ,
        "OCR_ENGINE": "mock",
        "OCR_DATA_DIR": str(tmp_path),
        "OCR_HOST": "127.0.0.1",
        "OCR_PORT": str(port),
        "PYTHONUNBUFFERED": "1",
    }
    process = subprocess.Popen(
        [str(service_python()), "app/main.py"],
        cwd=PROJECT_ROOT,
        env=environment,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    try:
        timeout_seconds = float(
            os.getenv("OCR_STARTUP_TIMEOUT_SECONDS", str(DEFAULT_STARTUP_TIMEOUT_SECONDS))
        )
        deadline = time.monotonic() + timeout_seconds
        response_body = None
        while time.monotonic() < deadline:
            if process.poll() is not None:
                output = process.stdout.read() if process.stdout else ""
                raise AssertionError(f"startup exited with {process.returncode}: {output}")
            try:
                with urlopen(f"http://127.0.0.1:{port}/health", timeout=0.5) as response:
                    response_body = response.read().decode("utf-8")
                    break
            except OSError:
                time.sleep(0.1)

        if response_body is None:
            output = stop_process(process)
            raise AssertionError(
                f"startup did not expose /health within {timeout_seconds:g}s:\n{output[-4000:]}"
            )
        assert response_body == '{"status":"ok","engine":"mock"}'
    finally:
        stop_process(process)


def test_minio_and_internal_ocr_settings_are_read_from_environment(monkeypatch):
    from app.config import Settings

    monkeypatch.setenv("MINIO_ENDPOINT", "http://minio.test:19000")
    monkeypatch.setenv("MINIO_ACCESS_KEY", "access")
    monkeypatch.setenv("MINIO_SECRET_KEY", "secret")
    monkeypatch.setenv("MINIO_BUCKET", "documents")
    monkeypatch.setenv("MINIO_ALLOWED_SOURCE_PREFIX", "archive/")
    monkeypatch.setenv("OCR_INTERNAL_TOKEN", "internal-token")

    settings = Settings()

    assert settings.minio_endpoint == "http://minio.test:19000"
    assert settings.minio_access_key == "access"
    assert settings.minio_secret_key == "secret"
    assert settings.minio_bucket == "documents"
    assert settings.minio_allowed_source_prefix == "archive/"
    assert settings.internal_token == "internal-token"


def configure_production_environment(monkeypatch, tmp_path):
    monkeypatch.setenv("OCR_ENV", "production")
    monkeypatch.setenv("OCR_ENGINE", "paddle")
    monkeypatch.setenv("OCR_DATA_DIR", str(tmp_path))
    monkeypatch.setenv("MINIO_ENDPOINT", "http://minio.internal:9000")
    monkeypatch.setenv("MINIO_ACCESS_KEY", "production-access")
    monkeypatch.setenv("MINIO_SECRET_KEY", "production-minio-secret-2026")
    monkeypatch.setenv("MINIO_BUCKET", "hr-production")
    monkeypatch.setenv("OCR_INTERNAL_TOKEN", "production-ocr-internal-token-2026")


def test_production_ocr_requires_paddle_engine(monkeypatch, tmp_path):
    from app.config import Settings

    configure_production_environment(monkeypatch, tmp_path)
    monkeypatch.setenv("OCR_ENGINE", "mock")

    with pytest.raises(ValueError, match="OCR_ENGINE"):
        Settings()


def test_production_ocr_rejects_development_storage_credentials(monkeypatch, tmp_path):
    from app.config import Settings

    configure_production_environment(monkeypatch, tmp_path)
    monkeypatch.setenv("MINIO_ACCESS_KEY", "minioadmin")

    with pytest.raises(ValueError, match="MINIO_ACCESS_KEY"):
        Settings()


def test_production_ocr_requires_explicit_internal_token(monkeypatch, tmp_path):
    from app.config import Settings

    configure_production_environment(monkeypatch, tmp_path)
    monkeypatch.delenv("OCR_INTERNAL_TOKEN")

    with pytest.raises(ValueError, match="OCR_INTERNAL_TOKEN"):
        Settings()


def test_production_ocr_accepts_explicit_persistent_configuration(monkeypatch, tmp_path):
    from app.config import Settings

    configure_production_environment(monkeypatch, tmp_path)

    settings = Settings()

    assert settings.engine == "paddle"
    assert settings.data_dir == tmp_path
    assert settings.internal_token == "production-ocr-internal-token-2026"
