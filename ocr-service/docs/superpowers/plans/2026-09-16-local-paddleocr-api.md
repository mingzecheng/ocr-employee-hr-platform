# Local PaddleOCR API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a locally runnable FastAPI OCR service that uses PaddleOCR to recognize Chinese image text and returns persistable, queryable OCR evidence to the employee archive and HR workflow system.

**Architecture:** Keep the existing FastAPI route and `OcrService` orchestration boundary. Expand `PaddleOcrEngine` into a deterministic adapter that preprocesses a local image, calls PaddleOCR, normalizes PaddleOCR 2.x/3.x results into `TextBlock`, and never fabricates business fields. Runtime configuration selects Paddle by default while tests inject small fake engines so they do not require model downloads.

**Tech Stack:** Python 3.10, FastAPI, Pydantic v2, Pillow, PaddlePaddle 3.3.0 CPU, PaddleOCR 3.x, pytest, httpx.

## Global Constraints

- Preserve `POST /api/ocr/tasks`, `GET /api/ocr/tasks/{taskId}`, and the `{code,message,data}` response envelope exactly.
- Accept only PNG/JPEG images and retain current MIME, payload-size, and Pillow-content validation.
- Default `OCR_ENGINE` to `paddle`; retain `mock` only as an explicit development/test engine.
- Text output must use `TextBlock(pageNo, text, confidence, bbox)` where `bbox` is `[left, top, right, bottom]` in pixels.
- Successful real OCR returns `fields: []`; template field mapping and correction records are explicitly out of scope for this phase.
- Preserve uploaded source files and SHA-256 metadata on both successful and failed recognition.
- Use simple ASCII source-code identifiers; code comments must explain non-obvious compatibility or coordinate behavior only.
- Use `apply_patch` for all hand-authored file edits. The checkout has no Git repository; record validation instead of attempting commits.

---

## File Structure

| Path | Responsibility |
| --- | --- |
| `app/config.py` | Default engine and image-preprocessing configuration. |
| `app/engines.py` | Engine protocol, mock result for explicit test mode, Pillow preprocessing, PaddleOCR execution, 2.x/3.x output conversion. |
| `app/main.py` | FastAPI app construction and process startup error clarity. |
| `tests/test_ocr_api.py` | HTTP contract, task persistence, engine-injection, default-output tests. |
| `tests/test_paddle_engine.py` | Pure unit tests for preprocessing and Paddle result normalization. |
| `tests/test_startup.py` | Explicit mock startup smoke test and Paddle setup failure behavior. |
| `requirements.txt` | Reproducible runtime/test dependency list. |
| `pyproject.toml` | Equivalent package dependency metadata and optional Paddle dependency group. |
| `.env.example` | Default local development configuration. |
| `README.md` | Installation, model first-run behavior, startup, HTTP examples, and integration contract. |

## Task 1: Make PaddleOCR the Explicit Runtime Dependency and Default

**Files:**
- Modify: `requirements.txt`
- Modify: `pyproject.toml`
- Modify: `.env.example`
- Modify: `README.md`
- Modify: `app/config.py`
- Test: `tests/test_ocr_api.py`

**Interfaces:**
- Consumes: `Settings(engine=None, data_dir=None, max_file_size_bytes=None)`.
- Produces: `Settings.max_image_edge: int` and default `Settings.engine == "paddle"` when `OCR_ENGINE` is unset.
- Produces: `.env.example` containing `OCR_ENGINE=paddle` and `OCR_MAX_IMAGE_EDGE=4096`.

- [ ] **Step 1: Write the failing settings test**

Add this to `tests/test_ocr_api.py`:

```python
from app.config import Settings


def test_default_settings_select_paddle_and_limit_image_edge(monkeypatch):
    monkeypatch.delenv("OCR_ENGINE", raising=False)
    monkeypatch.delenv("OCR_MAX_IMAGE_EDGE", raising=False)

    settings = Settings()

    assert settings.engine == "paddle"
    assert settings.max_image_edge == 4096
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
.venv/bin/pytest tests/test_ocr_api.py::test_default_settings_select_paddle_and_limit_image_edge -q
```

Expected: `FAIL` because the existing default is `mock` and `max_image_edge` does not exist.

- [ ] **Step 3: Implement the minimal configuration and dependency changes**

Update `app/config.py` to expose the new value without changing test overrides:

```python
class Settings:
    def __init__(
        self,
        *,
        engine: str | None = None,
        data_dir: Path | str | None = None,
        max_file_size_bytes: int | None = None,
        max_image_edge: int | None = None,
    ) -> None:
        self.engine = (engine or os.getenv("OCR_ENGINE", "paddle")).lower()
        self.data_dir = Path(data_dir or os.getenv("OCR_DATA_DIR", "./data"))
        self.max_file_size_bytes = max_file_size_bytes or int(
            os.getenv("OCR_MAX_FILE_SIZE_BYTES", str(10 * 1024 * 1024))
        )
        self.max_image_edge = max_image_edge or int(
            os.getenv("OCR_MAX_IMAGE_EDGE", "4096")
        )
```

Add the following runtime dependencies to both package manifests:

```text
paddlepaddle==3.3.0
paddleocr>=3.0,<4
```

In `.env.example`, replace `OCR_ENGINE=mock` with `OCR_ENGINE=paddle` and add `OCR_MAX_IMAGE_EDGE=4096`. Update README installation commands to use the project virtual environment:

```bash
.venv/bin/python -m pip install --upgrade pip
.venv/bin/python -m pip install paddlepaddle==3.3.0 \
  -i https://www.paddlepaddle.org.cn/packages/stable/cpu/
.venv/bin/python -m pip install paddleocr
```

Document that the first `PaddleOCR` initialization downloads its Chinese model into the local Paddle model cache and needs network access once.

- [ ] **Step 4: Run the focused test to verify it passes**

Run:

```bash
.venv/bin/pytest tests/test_ocr_api.py::test_default_settings_select_paddle_and_limit_image_edge -q
```

Expected: `1 passed`.

- [ ] **Step 5: Verify dependency installation for this Mac arm64 virtual environment**

Run:

```bash
.venv/bin/python -m pip install paddlepaddle==3.3.0 -i https://www.paddlepaddle.org.cn/packages/stable/cpu/
.venv/bin/python -m pip install 'paddleocr>=3.0,<4'
.venv/bin/python -c 'import paddle, paddleocr; print(paddle.__version__); print(paddleocr.__version__)'
```

Expected: both imports succeed and print installed versions.

- [ ] **Step 6: Record the Git constraint**

Run:

```bash
git rev-parse --show-toplevel
```

Expected: exits non-zero with `fatal: not a git repository`; do not attempt a commit.

## Task 2: Add Deterministic Image Preprocessing and Version-Tolerant Paddle Result Normalization

**Files:**
- Modify: `app/engines.py`
- Create: `tests/test_paddle_engine.py`

**Interfaces:**
- Consumes: `PaddleOcrEngine(max_image_edge: int = 4096, ocr: Any | None = None)`.
- Produces: `PaddleOcrEngine.recognize(image_path: Path) -> RecognitionOutput`.
- Produces: `PaddleOcrEngine._normalize(raw_results: Any) -> RecognitionOutput` and preserves only nonblank texts.
- Produces: `PaddleOcrEngine._preprocess(image_path: Path) -> Path`; returned temporary image is cleaned after recognition.

- [ ] **Step 1: Write failing result-normalization tests**

Create `tests/test_paddle_engine.py` with these tests:

```python
from pathlib import Path

from PIL import Image

from app.engines import PaddleOcrEngine


class FakePaddleOcr:
    def predict(self, image_path: str):
        assert Path(image_path).exists()
        return [
            {
                "rec_texts": [" 张三 ", "", "EMP-001"],
                "rec_scores": [0.98, 0.50, 0.96],
                "rec_boxes": [
                    [[10, 40], [60, 30], [75, 85], [5, 95]],
                    [[1, 1], [2, 1], [2, 2], [1, 2]],
                    [100, 20, 220, 65],
                ],
            }
        ]


def test_normalize_converts_polygons_and_discards_blank_text():
    engine = PaddleOcrEngine(ocr=FakePaddleOcr())

    result = engine._normalize(FakePaddleOcr().predict("unused"))

    assert [(block.text, block.confidence, block.bbox) for block in result.text_blocks] == [
        ("张三", 0.98, [5, 30, 75, 95]),
        ("EMP-001", 0.96, [100, 20, 220, 65]),
    ]
    assert result.fields == []


def test_recognize_downscales_oversized_image_and_cleans_temporary_file(tmp_path):
    source = tmp_path / "large.png"
    Image.new("RGB", (1200, 600), "white").save(source)
    seen_path: Path | None = None

    class InspectingPaddleOcr:
        def predict(self, image_path: str):
            nonlocal seen_path
            seen_path = Path(image_path)
            with Image.open(seen_path) as processed:
                assert processed.size == (400, 200)
            return [{"rec_texts": ["档案"], "rec_scores": [0.99], "rec_boxes": [[1, 2, 30, 20]]}]

    result = PaddleOcrEngine(max_image_edge=400, ocr=InspectingPaddleOcr()).recognize(source)

    assert result.text_blocks[0].text == "档案"
    assert seen_path is not None
    assert not seen_path.exists()
```

- [ ] **Step 2: Run the new tests to verify they fail**

Run:

```bash
.venv/bin/pytest tests/test_paddle_engine.py -q
```

Expected: `FAIL` because the constructor does not accept injected OCR or `max_image_edge`, blank text is retained, and no preprocessing exists.

- [ ] **Step 3: Implement preprocessing, injection, and normalization**

Replace the Paddle engine implementation in `app/engines.py` with the following behavior:

```python
class PaddleOcrEngine(OcrEngine):
    name = "paddle"

    def __init__(self, *, max_image_edge: int = 4096, ocr: Any | None = None) -> None:
        self._max_image_edge = max_image_edge
        self._ocr = ocr or self._build_ocr()

    @staticmethod
    def _build_ocr() -> Any:
        try:
            from paddleocr import PaddleOCR
        except ImportError as exc:
            raise RuntimeError(
                "PaddleOCR is not installed; install paddlepaddle and paddleocr first"
            ) from exc
        return PaddleOCR(lang="ch")

    def recognize(self, image_path: Path) -> RecognitionOutput:
        prepared_path = self._preprocess(image_path)
        try:
            raw_results = self._predict(prepared_path)
            return self._normalize(raw_results)
        finally:
            prepared_path.unlink(missing_ok=True)

    def _predict(self, image_path: Path) -> Any:
        if hasattr(self._ocr, "predict"):
            return self._ocr.predict(str(image_path))
        return self._ocr.ocr(str(image_path), cls=True)
```

Implement `_preprocess` with `PIL.ImageOps.exif_transpose`, `.convert("RGB")`, and `thumbnail((self._max_image_edge, self._max_image_edge))`. Use `tempfile.NamedTemporaryFile(suffix=".png", delete=False)` to create an image file in the system temporary directory, save PNG data to it, and return `Path(temp_file.name)` after closing it. In `_normalize`, call `str(text).strip()` before appending; use the exact existing `_bbox` conversion helper for polygon-to-rectangle normalization. Keep `fields=[]`.

Extend `_page_payload` to accept old-style line results in addition to dict/JSON results:

```python
if isinstance(raw_page, list):
    boxes, texts, scores = [], [], []
    for line in raw_page:
        if not isinstance(line, (list, tuple)) or len(line) < 2:
            continue
        boxes.append(line[0])
        content = line[1]
        texts.append(content[0] if isinstance(content, (list, tuple)) else "")
        scores.append(content[1] if isinstance(content, (list, tuple)) and len(content) > 1 else 0.0)
    return {"rec_boxes": boxes, "rec_texts": texts, "rec_scores": scores}
```

- [ ] **Step 4: Run the focused unit tests to verify they pass**

Run:

```bash
.venv/bin/pytest tests/test_paddle_engine.py -q
```

Expected: `2 passed`.

- [ ] **Step 5: Add 2.x line-output coverage and run it**

Add this test:

```python
def test_normalize_supports_legacy_paddle_line_results():
    engine = PaddleOcrEngine(ocr=FakePaddleOcr())

    result = engine._normalize([[[[[1, 2], [8, 2], [8, 7], [1, 7]], ["员工编号", 0.91]]]])

    assert [(block.text, block.confidence, block.bbox) for block in result.text_blocks] == [
        ("员工编号", 0.91, [1, 2, 8, 7])
    ]
```

Run:

```bash
.venv/bin/pytest tests/test_paddle_engine.py -q
```

Expected: `3 passed`.

- [ ] **Step 6: Re-run existing tests for regression detection**

Run:

```bash
.venv/bin/pytest tests/test_ocr_api.py tests/test_startup.py -q
```

Expected: legacy mock tests may fail only where they assert fabricated fields; address those assertions in Task 3 rather than changing real-engine behavior.

## Task 3: Wire Configuration Into the Service and Align API Tests With Real OCR Semantics

**Files:**
- Modify: `app/main.py`
- Modify: `app/engines.py`
- Modify: `tests/test_ocr_api.py`
- Modify: `tests/test_startup.py`

**Interfaces:**
- Consumes: `create_app(data_dir=None, engine=None, engine_name=None)`.
- Produces: `PaddleOcrEngine(max_image_edge=settings.max_image_edge)` when `engine_name="paddle"` or environment selects Paddle.
- Produces: all successful tasks with source metadata, `SUCCEEDED`, nonempty injected-engine `textBlocks`, and empty `fields`.

- [ ] **Step 1: Write the failing API contract test for real OCR-shaped results**

Add an injected engine to `tests/test_ocr_api.py`:

```python
class FixedTextEngine(OcrEngine):
    name = "paddle"

    def recognize(self, image_path):
        return RecognitionOutput(
            text_blocks=[
                TextBlock(pageNo=1, text="张三", confidence=0.98, bbox=[10, 20, 70, 45])
            ],
            fields=[],
        )


def test_upload_returns_raw_text_blocks_without_fabricated_business_fields(tmp_path):
    api = client(tmp_path, engine=FixedTextEngine())

    response = api.post(
        "/api/ocr/tasks",
        files={"file": ("employee.png", image_bytes(), "image/png")},
    )

    task = response.json()["data"]
    assert task["status"] == "SUCCEEDED"
    assert task["textBlocks"] == [
        {"pageNo": 1, "text": "张三", "confidence": 0.98, "bbox": [10, 20, 70, 45]}
    ]
    assert task["fields"] == []
```

Add required imports:

```python
from app.engines import OcrEngine, RecognitionOutput, build_engine
from app.models import TextBlock
```

- [ ] **Step 2: Run the API test to verify it fails**

Run:

```bash
.venv/bin/pytest tests/test_ocr_api.py::test_upload_returns_raw_text_blocks_without_fabricated_business_fields -q
```

Expected: `FAIL` until imports and the test helper are correctly added.

- [ ] **Step 3: Pass preprocessing configuration to the selected Paddle engine**

Change engine construction to accept an optional image-edge limit:

```python
def build_engine(name: str, *, max_image_edge: int = 4096) -> OcrEngine:
    if name == "mock":
        return MockOcrEngine()
    if name == "paddle":
        return PaddleOcrEngine(max_image_edge=max_image_edge)
    raise ValueError(f"Unsupported OCR engine: {name}")
```

Then in `create_app` in `app/main.py`, construct the engine as:

```python
selected_engine = engine or build_engine(
    settings.engine,
    max_image_edge=settings.max_image_edge,
)
```

Change `MockOcrEngine` to return an empty `fields` list. Update existing mock assertions that expect `employee_name`, `employee_id`, `validationStatus`, or `value`; replace them with assertions that `textBlocks` is nonempty and `fields == []`.

- [ ] **Step 4: Run the focused API test to verify it passes**

Run:

```bash
.venv/bin/pytest tests/test_ocr_api.py::test_upload_returns_raw_text_blocks_without_fabricated_business_fields -q
```

Expected: `1 passed`.

- [ ] **Step 5: Update OpenAPI and startup expectations**

In `tests/test_ocr_api.py`, retain the route and request-body tests but replace the `FieldResult` value assertion with:

```python
assert schemas["TextBlock"]["properties"]["bbox"]["description"] == (
    "文本位置框，格式为 [左, 上, 右, 下]"
)
```

In `tests/test_startup.py`, leave `OCR_ENGINE="mock"` explicit so this subprocess check remains fast and does not download models. Add a test that monkeypatches imports or calls `build_engine("paddle")` only in an environment where the dependency is unavailable; assert its `RuntimeError` contains `install paddlepaddle and paddleocr first`.

- [ ] **Step 6: Run all unit and API tests**

Run:

```bash
.venv/bin/pytest -q
```

Expected: all tests pass. Existing FastAPI/Starlette deprecation warnings may remain; they are not test failures and are outside this feature scope.

## Task 4: Verify the Real Local PaddleOCR HTTP Flow and Document It

**Files:**
- Modify: `README.md`
- Test: manual command against `POST /api/ocr/tasks` and `GET /api/ocr/tasks/{taskId}`

**Interfaces:**
- Consumes: `OCR_ENGINE=paddle`, a local Chinese PNG/JPEG image, and the installed virtual environment.
- Produces: HTTP 200 `SUCCEEDED` response with at least one nonempty `textBlocks[*].text` and a queryable `taskId`.

- [ ] **Step 1: Create a local non-sensitive Chinese smoke-test image**

Run this command from `ocr-service`:

```bash
.venv/bin/python - <<'PY'
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

output = Path("data/ocr-smoke.png")
image = Image.new("RGB", (900, 260), "white")
draw = ImageDraw.Draw(image)
font_candidates = [
    "/System/Library/Fonts/PingFang.ttc",
    "/System/Library/Fonts/STHeiti Light.ttc",
]
font = next((ImageFont.truetype(path, 52) for path in font_candidates if Path(path).exists()), None)
if font is None:
    raise RuntimeError("No supported local Chinese font was found")
draw.text((40, 55), "员工姓名：张三", fill="black", font=font)
draw.text((40, 135), "员工编号：EMP-001", fill="black", font=font)
image.save(output)
print(output)
PY
```

Expected: prints `data/ocr-smoke.png` and writes a synthetic, non-personal-data image.

- [ ] **Step 2: Start the service using the real Paddle engine**

Run:

```bash
OCR_ENGINE=paddle OCR_DATA_DIR=./data .venv/bin/python app/main.py
```

Expected: Uvicorn listens at `http://127.0.0.1:8000`; first run may download models before the server starts responding.

- [ ] **Step 3: Upload and save the returned task ID**

In another terminal, run:

```bash
curl -sS -X POST http://127.0.0.1:8000/api/ocr/tasks \
  -F file=@data/ocr-smoke.png \
  | tee /tmp/ocr-task.json
.venv/bin/python - <<'PY'
import json
from pathlib import Path

body = json.loads(Path("/tmp/ocr-task.json").read_text())
assert body["code"] == 0
assert body["data"]["status"] == "SUCCEEDED"
assert any(block["text"].strip() for block in body["data"]["textBlocks"])
print(body["data"]["taskId"])
PY
```

Expected: prints a nonempty task ID.

- [ ] **Step 4: Query and compare persisted OCR evidence**

Run with the value printed in Step 3:

```bash
curl -sS http://127.0.0.1:8000/api/ocr/tasks/<taskId> \
  | .venv/bin/python -m json.tool
```

Expected: task status is `SUCCEEDED`; `sourceFile.sha256` is present; queried `textBlocks` match the POST response; `fields` is `[]`.

- [ ] **Step 5: Final regression and configuration checks**

Run:

```bash
.venv/bin/pytest -q
OCR_ENGINE=mock OCR_DATA_DIR=/tmp/ocr-startup-check .venv/bin/python app/main.py
```

Expected: test suite passes; second command exposes `GET /health` with `{"status":"ok","engine":"mock"}`. Stop the development server cleanly after checking it.

- [ ] **Step 6: Update README verification section**

Add a concise section containing the exact `curl` upload command, documented success fields (`taskId`, `status`, `sourceFile`, `textBlocks`, `fields`), and a note that `fields` remains empty until the later employee-document template mapping module. Do not include `/tmp/ocr-task.json` or the one-off generated smoke image in user documentation.

## Plan Self-Review

### Spec coverage

- Local FastAPI upload/query interface: Task 3 and Task 4.
- PaddleOCR Chinese image-text recognition: Task 1 install/configuration and Task 2 implementation.
- Image preprocessing: Task 2.
- Text, coordinates, confidence normalization: Task 2 and Task 3 API contract.
- Original image/task evidence and failure state: preserved by existing `OcrService`; Task 3 regression suite verifies it.
- Later HR integration boundary: documented in Task 4 and maintained by `taskId` plus raw `TextBlock` output.
- Code quality/comments and automated testing: Tasks 2 and 3 use focused TDD tests and keep comments limited to version/coordinate behavior.

### Execution adjustment: defer default-engine construction

During Task 1 execution, changing `OCR_ENGINE` to `paddle` exposed that the module-level `app = create_app()` statement constructs PaddleOCR while test modules import `app.main`. This causes an unrelated settings test to initialize/download models. Add the following steps at the beginning of Task 3 before any API contract changes:

- [ ] **Step 0a: Write the failing no-eager-construction test**

```python
def test_create_app_defers_default_engine_construction(tmp_path, monkeypatch):
    def fail_if_called(*args, **kwargs):
        raise AssertionError("the default engine must not be built during app construction")

    monkeypatch.setattr("app.main.build_engine", fail_if_called)

    app = create_app(data_dir=tmp_path)

    assert app.state.settings.engine == "paddle"
```

- [ ] **Step 0b: Run it to verify it fails**

Run:

```bash
.venv/bin/pytest tests/test_ocr_api.py::test_create_app_defers_default_engine_construction -q
```

Expected: `FAIL` because `create_app` currently calls `build_engine(settings.engine)` immediately.

- [ ] **Step 0c: Initialize the default engine at application startup**

In `app/main.py`, keep an explicitly injected `engine` eager for isolated API tests. When `engine is None`, preserve only `Settings` during `create_app`, then create `OcrService(data_dir=settings.data_dir, engine=build_engine(...))` in the FastAPI startup handler. Store the ready service on `app.state.ocr_service`; route handlers read that state rather than a closure-local service. The health response uses the injected engine name when present and otherwise reports the configured engine name. This keeps `python app/main.py` startup failure explicit when Paddle dependencies are missing while making imports and factory construction model-free.

- [ ] **Step 0d: Re-run the test to verify it passes**

Run:

```bash
.venv/bin/pytest tests/test_ocr_api.py::test_create_app_defers_default_engine_construction -q
```

Expected: `1 passed` without PaddleOCR model initialization.

The deferred field mapping, validation, low-confidence business status, manual corrections, database persistence, authorization, and full HR workflow modules are explicitly non-goals of this first OCR-core implementation, consistent with the accepted design.

### Placeholder scan

No `TODO`, `TBD`, “appropriate”, “similar to”, or undefined interface references remain. Every task specifies paths, tests, commands, expected outcomes, and concrete interfaces.

### Type consistency

- `PaddleOcrEngine` receives `max_image_edge` from `build_engine`, which receives it from `Settings` in `create_app`.
- `RecognitionOutput` remains the only output passed from an engine to `OcrService`.
- All API tests use `TextBlock` and `RecognitionOutput` from the same existing modules.
- `fields=[]` is consistent among mock, injected test engine, normalized Paddle results, API response, and README contract.
