# Local OCR Service Implementation Plan

> **For agentic workers:** This plan is executed inline in the current workspace. Steps use checkbox syntax for tracking.

**Goal:** Build a locally runnable FastAPI OCR service that accepts document uploads and directly returns structured OCR task results while allowing mock and PaddleOCR engines to be switched by configuration.

**Architecture:** A small HTTP API owns task state and file persistence in a local data directory. An `OcrEngine` protocol isolates recognition from the API; the mock engine provides deterministic development behavior and the PaddleOCR adapter is loaded only when selected. The first version uses an in-memory task store for runtime simplicity and persists uploaded source files so failed OCR does not lose the original evidence.

**Tech Stack:** Python 3.10+, FastAPI, Pydantic v2, Uvicorn, pytest, httpx, Pillow, optional PaddleOCR.

## Global Constraints

- API paths are `/api/ocr/tasks` and `/api/ocr/tasks/{task_id}`.
- API responses contain `code`, `message`, and `data`.
- The upload request contains only the required `file` field; business document classification is outside the OCR service.
- Original uploads are stored separately from derived OCR results and are never overwritten.
- OCR failure returns a queryable `FAILED` task with an error message and retained source file.
- No real employee or enterprise data is needed for local development.

## File Map

- Create `ocr-service/pyproject.toml`: project metadata and dependency/test configuration.
- Create `ocr-service/requirements.txt`: installable runtime and test dependencies.
- Create `ocr-service/.env.example`: local configuration defaults.
- Create `ocr-service/app/config.py`: environment-backed settings.
- Create `ocr-service/app/models.py`: response, field, and task models.
- Create `ocr-service/app/engines.py`: engine protocol, deterministic mock engine, and optional PaddleOCR adapter.
- Create `ocr-service/app/store.py`: local source-file storage and in-memory task store.
- Create `ocr-service/app/service.py`: upload, recognition, and task state business logic.
- Create `ocr-service/app/main.py`: FastAPI application and route handlers.
- Create `ocr-service/tests/test_ocr_api.py`: endpoint contract and state transition tests.
- Create `ocr-service/README.md`: setup, run, API examples, and PaddleOCR activation instructions.

### Task 1: Scaffold and upload contract

- [x] Write a failing test that imports `app.main`, checks `/health`, and rejects unsupported file types.
- [x] Run `pytest -q` from `ocr-service`; confirmed collection failure because `app` did not exist yet.
- [x] Add the project files, settings, response envelope, upload validation, and health route.
- [x] Run the focused tests; health and validation assertions pass.

### Task 2: Task creation and result query

- [x] Add a failing test for a valid image upload returning a task ID, `PENDING`/terminal status, retained source metadata, text blocks, fields, confidence, bounding box, and validation status.
- [x] Run the focused test and confirmed it failed on the missing task implementation.
- [x] Add the models, file store, task store, mock engine, and service orchestration.
- [x] Run the focused test and then the full test file; task creation/query tests pass.

### Task 3: Direct result and failure persistence

- [x] Add a failing test for direct successful results and absence of the manual review route, plus retaining source files when an engine fails.
- [x] Run the focused test and confirmed the old `REVIEW_REQUIRED` behavior failed the new contract.
- [x] Implement direct `SUCCEEDED` results, field `value`, and failed-task transitions.
- [x] Run the full test suite; all tests pass.

### Task 4: PaddleOCR adapter and local operator documentation

- [x] Add a failing unit test for engine selection, including a clear error when PaddleOCR is selected but unavailable.
- [x] Implement lazy PaddleOCR import, normalized result conversion, and `OCR_ENGINE=mock|paddle` settings.
- [x] Add `.env.example`, `requirements.txt`, `pyproject.toml`, and README commands for `uv` and standard `pip` environments.
- [x] Run tests and a syntax check; the mock path remains dependency-light and the PaddleOCR-unavailable path is explicit.

### Task 5: Runtime verification

- [x] Install service dependencies into `.venv`.
- [x] Start Uvicorn on `127.0.0.1:8000` with the mock engine.
- [x] Exercise health, upload, and query through real HTTP requests.
- [x] Run the complete pytest suite and verify the generated local source file exists.

## Verification Commands

```bash
cd ocr-service
uv venv .venv
uv pip install -r requirements.txt
uv run pytest -q
OCR_ENGINE=mock uv run uvicorn app.main:app --host 127.0.0.1 --port 8000
```

The service is complete for this phase only when the tests pass and the real HTTP upload/query operations return the documented envelope without losing the uploaded source file.
