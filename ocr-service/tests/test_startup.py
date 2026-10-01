from __future__ import annotations

import os
import socket
import subprocess
import sys
import time
from pathlib import Path
from urllib.request import urlopen


PROJECT_ROOT = Path(__file__).resolve().parents[1]


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
        [sys.executable, "app/main.py"],
        cwd=PROJECT_ROOT,
        env=environment,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    try:
        deadline = time.monotonic() + 10
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

        assert response_body == '{"status":"ok","engine":"mock"}'
    finally:
        process.terminate()
        process.wait(timeout=5)


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
