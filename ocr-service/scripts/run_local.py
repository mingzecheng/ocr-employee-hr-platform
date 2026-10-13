from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import sys


SERVICE_DIR = Path(__file__).resolve().parents[1]
REQUIREMENTS_FILE = SERVICE_DIR / "requirements.txt"
REQUIRED_DISTRIBUTIONS = ("fastapi", "uvicorn", "paddlepaddle", "paddleocr")


def runtime_dir() -> Path:
    configured = os.getenv("HR_OCR_VENV_DIR")
    if configured:
        return Path(configured).expanduser().resolve()
    if sys.platform == "darwin":
        return Path.home() / "Library" / "Caches" / "hr-platform" / "ocr-service-venv"
    cache_root = Path(os.getenv("XDG_CACHE_HOME", Path.home() / ".cache"))
    return cache_root / "hr-platform" / "ocr-service-venv"


def has_runtime_dependencies(python: Path) -> bool:
    package_list = ", ".join(repr(name) for name in REQUIRED_DISTRIBUTIONS)
    check = (
        "import importlib.metadata as metadata; "
        f"[metadata.version(name) for name in ({package_list},)]"
    )
    try:
        subprocess.run(
            [str(python), "-c", check],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=15,
        )
        return True
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        return False


def prepare_runtime() -> Path:
    target = runtime_dir()
    python = target / "bin" / "python"
    target.mkdir(parents=True, exist_ok=True)

    if not python.exists():
        base_python = Path(getattr(sys, "_base_executable", sys.executable))
        subprocess.run([str(base_python), "-m", "venv", str(target)], check=True)

    requirements_hash = hashlib.sha256(REQUIREMENTS_FILE.read_bytes()).hexdigest()
    marker = target / ".requirements-sha256"
    installed_hash = marker.read_text().strip() if marker.exists() else ""
    if installed_hash != requirements_hash or not has_runtime_dependencies(python):
        print(f"Installing OCR dependencies into local runtime: {target}", flush=True)
        subprocess.run(
            [str(python), "-m", "pip", "install", "-r", str(REQUIREMENTS_FILE)],
            check=True,
        )
        marker.write_text(f"{requirements_hash}\n")
    return python


def main() -> int:
    parser = argparse.ArgumentParser(description="Start the local PaddleOCR service")
    parser.add_argument(
        "--install-only",
        action="store_true",
        help="create the local runtime and install dependencies without starting the API",
    )
    args = parser.parse_args()

    if sys.version_info < (3, 10):
        parser.error("Python 3.10 or newer is required")

    python = prepare_runtime()
    if args.install_only:
        print(f"OCR runtime ready: {python}")
        return 0

    os.environ.setdefault("OCR_ENGINE", "paddle")
    os.environ.setdefault("OCR_HOST", "127.0.0.1")
    os.environ.setdefault("OCR_PORT", "8000")
    os.chdir(SERVICE_DIR)
    os.execv(
        str(python),
        [
            str(python),
            "-m",
            "uvicorn",
            "app.main:app",
            "--host",
            os.environ["OCR_HOST"],
            "--port",
            os.environ["OCR_PORT"],
        ],
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
