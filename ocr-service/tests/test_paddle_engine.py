from pathlib import Path
import sys
from types import SimpleNamespace

from PIL import Image, ImageDraw

from app.engines import PaddleOcrEngine, build_engine


class FakePaddleOcr:
    def predict(self, image_path: str):
        del image_path
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
            return [
                {
                    "rec_texts": ["档案"],
                    "rec_scores": [0.99],
                    "rec_boxes": [[1, 2, 30, 20]],
                }
            ]

    result = PaddleOcrEngine(
        max_image_edge=400,
        ocr=InspectingPaddleOcr(),
    ).recognize(source)

    assert result.text_blocks[0].text == "档案"
    assert result.text_blocks[0].bbox == [3, 6, 90, 60]
    assert seen_path is not None
    assert not seen_path.exists()


def test_preprocess_normalizes_low_contrast_document_images(tmp_path):
    source = tmp_path / "low-contrast.png"
    image = Image.new("RGB", (100, 40), (242, 242, 242))
    ImageDraw.Draw(image).rectangle((10, 10, 20, 20), fill=(128, 128, 128))
    image.save(source)
    observed_pixels: dict[str, tuple[int, int, int]] = {}

    class InspectingPaddleOcr:
        def predict(self, image_path: str):
            with Image.open(image_path) as processed:
                observed_pixels["background"] = processed.getpixel((0, 0))
                observed_pixels["text"] = processed.getpixel((15, 15))
            return [{"rec_texts": [], "rec_scores": [], "rec_boxes": []}]

    PaddleOcrEngine(ocr=InspectingPaddleOcr()).recognize(source)

    assert observed_pixels == {
        "background": (255, 255, 255),
        "text": (0, 0, 0),
    }


def test_normalize_supports_legacy_paddle_line_results():
    engine = PaddleOcrEngine(ocr=FakePaddleOcr())

    result = engine._normalize(
        [
            [
                [
                    [[1, 2], [8, 2], [8, 7], [1, 7]],
                    ["员工编号", 0.91],
                ]
            ]
        ]
    )

    assert [(block.text, block.confidence, block.bbox) for block in result.text_blocks] == [
        ("员工编号", 0.91, [1, 2, 8, 7])
    ]


def test_normalize_supports_array_values_without_boolean_coercion():
    class ArrayLike:
        def __init__(self, value):
            self.value = value

        def __bool__(self):
            raise AssertionError("OCR array values must not be evaluated as booleans")

        def tolist(self):
            return self.value

    result = PaddleOcrEngine(ocr=FakePaddleOcr())._normalize(
        [
            {
                "rec_texts": ArrayLike(["档案"]),
                "rec_scores": ArrayLike([0.99]),
                "rec_boxes": ArrayLike([[[10, 20], [80, 20], [80, 50], [10, 50]]]),
            }
        ]
    )

    assert [(block.text, block.confidence, block.bbox) for block in result.text_blocks] == [
        ("档案", 0.99, [10, 20, 80, 50])
    ]


def test_build_engine_passes_image_limit_to_paddle_engine(monkeypatch):
    received: dict[str, int] = {}

    class FakePaddleOcrEngine:
        name = "paddle"

        def __init__(self, *, max_image_edge: int):
            received["max_image_edge"] = max_image_edge

    monkeypatch.setattr("app.engines.PaddleOcrEngine", FakePaddleOcrEngine)

    engine = build_engine("paddle", max_image_edge=512)

    assert engine.name == "paddle"
    assert received == {"max_image_edge": 512}


def test_paddle_constructor_keeps_detection_resolution_at_preprocessed_size(monkeypatch):
    received: dict[str, object] = {}

    class FakePaddleOCR:
        def __init__(self, **kwargs):
            received.update(kwargs)

    monkeypatch.setitem(sys.modules, "paddleocr", SimpleNamespace(PaddleOCR=FakePaddleOCR))

    PaddleOcrEngine(max_image_edge=3072)

    assert received["lang"] == "ch"
    assert received["text_det_limit_side_len"] == 3072
    assert received["text_det_limit_type"] == "max"
