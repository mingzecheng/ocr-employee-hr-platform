from __future__ import annotations

from dataclasses import dataclass
from importlib.metadata import PackageNotFoundError, version as package_version
from pathlib import Path
from tempfile import NamedTemporaryFile
from typing import Any

from PIL import Image, ImageOps

from .models import FieldResult, TextBlock


class OcrEngine:
    """OCR 引擎边界；业务服务不依赖具体模型或厂商 SDK。"""

    name = "unknown"
    version = "unknown"

    def recognize(self, image_path: Path) -> "RecognitionOutput":
        raise NotImplementedError


@dataclass
class RecognitionOutput:
    """引擎统一输出，便于 mock、PaddleOCR 和后续引擎互换。"""

    text_blocks: list[TextBlock]
    fields: list[FieldResult]


@dataclass(frozen=True)
class PreparedImage:
    """临时识别图像及其相对原图的坐标缩放比例。"""

    path: Path
    scale_x: float
    scale_y: float


class MockOcrEngine(OcrEngine):
    name = "mock"
    version = "mock-1.0"

    def recognize(self, image_path: Path) -> RecognitionOutput:
        # mock 只模拟原始文本证据，不伪造尚未实现的业务字段映射。
        del image_path
        return RecognitionOutput(
            text_blocks=[
                TextBlock(
                    pageNo=1,
                    text="employee_name=张三 employee_id=EMP-001",
                    confidence=0.97,
                    bbox=[10, 10, 220, 80],
                )
            ],
            fields=[],
        )


class PaddleOcrEngine(OcrEngine):
    name = "paddle"

    def __init__(self, *, max_image_edge: int = 4096, ocr: Any | None = None) -> None:
        if max_image_edge < 1:
            raise ValueError("max_image_edge must be greater than zero")
        self._max_image_edge = max_image_edge
        self.version = self._installed_paddleocr_version()
        self._ocr = (
            ocr
            if ocr is not None
            else self._build_ocr(max_image_edge=max_image_edge)
        )

    @staticmethod
    def _installed_paddleocr_version() -> str:
        try:
            return package_version("paddleocr")
        except PackageNotFoundError:
            return "unknown"

    @staticmethod
    def _build_ocr(*, max_image_edge: int) -> Any:
        # 保持第三方依赖在运行时加载，方便测试注入轻量级伪引擎。
        try:
            from paddleocr import PaddleOCR
        except ImportError as exc:
            raise RuntimeError(
                "PaddleOCR is not installed; install paddlepaddle and paddleocr first"
            ) from exc
        return PaddleOCR(
            lang="ch",
            # The input is already limited to this edge; do not shrink it again in detection.
            text_det_limit_side_len=max_image_edge,
            text_det_limit_type="max",
        )

    def recognize(self, image_path: Path) -> RecognitionOutput:
        prepared = self._preprocess(image_path)
        try:
            output = self._normalize(self._predict(prepared.path))
            self._restore_original_coordinates(output, prepared)
            return output
        finally:
            prepared.path.unlink(missing_ok=True)

    def _predict(self, image_path: Path) -> Any:
        if hasattr(self._ocr, "predict"):
            return self._ocr.predict(str(image_path))
        return self._ocr.ocr(str(image_path), cls=True)

    def _preprocess(self, image_path: Path) -> PreparedImage:
        """生成方向正确、尺寸受限的临时 RGB 图像，供 OCR 引擎读取。"""

        with Image.open(image_path) as source:
            # 低对比度扫描件容易在文本检测阶段产生碎片框；灰度化并拉伸对比度可保留笔画边界。
            image = ImageOps.autocontrast(
                ImageOps.grayscale(ImageOps.exif_transpose(source))
            ).convert("RGB")
            original_width, original_height = image.size
            image.thumbnail(
                (self._max_image_edge, self._max_image_edge),
                Image.Resampling.LANCZOS,
            )
            with NamedTemporaryFile(suffix=".png", delete=False) as temporary_file:
                image.save(temporary_file, format="PNG")
                return PreparedImage(
                    path=Path(temporary_file.name),
                    scale_x=original_width / image.width,
                    scale_y=original_height / image.height,
                )

    @staticmethod
    def _restore_original_coordinates(
        output: RecognitionOutput,
        prepared: PreparedImage,
    ) -> None:
        if prepared.scale_x == 1 and prepared.scale_y == 1:
            return
        for block in output.text_blocks:
            left, top, right, bottom = block.bbox
            block.bbox = [
                round(left * prepared.scale_x),
                round(top * prepared.scale_y),
                round(right * prepared.scale_x),
                round(bottom * prepared.scale_y),
            ]

    def _normalize(self, raw_results: Any) -> RecognitionOutput:
        # PaddleOCR 不同大版本的返回结构不同，这里统一成服务自己的文本块格式。
        text_blocks: list[TextBlock] = []
        for page_no, raw_page in enumerate(raw_results or [], start=1):
            payload = self._page_payload(raw_page)
            texts = self._as_sequence(self._first_present(payload, "rec_texts", "rec_text"))
            scores = self._as_sequence(
                self._first_present(payload, "rec_scores", "rec_score")
            )
            boxes = self._as_sequence(
                self._first_present(payload, "rec_boxes", "text_region")
            )
            for index, text in enumerate(texts):
                normalized_text = str(text).strip()
                if not normalized_text:
                    continue
                confidence = float(scores[index]) if index < len(scores) else 0.0
                bbox = self._bbox(boxes[index]) if index < len(boxes) else [0, 0, 0, 0]
                text_blocks.append(
                    TextBlock(
                        pageNo=page_no,
                        text=normalized_text,
                        confidence=confidence,
                        bbox=bbox,
                    )
                )
        return RecognitionOutput(text_blocks=text_blocks, fields=[])

    @staticmethod
    def _first_present(payload: dict, *keys: str) -> Any:
        # NumPy 数组不能参与布尔短路判断，按键存在和 None 值选择兼容 PaddleOCR 结果。
        for key in keys:
            value = payload.get(key)
            if value is not None:
                return value
        return []

    @staticmethod
    def _page_payload(raw_page: Any) -> dict:
        # 兼容旧版 dict、新版 Result 对象，以及 JSON 字符串三种常见返回形式。
        if isinstance(raw_page, dict):
            return raw_page.get("res", raw_page)
        if isinstance(raw_page, (list, tuple)):
            boxes: list[Any] = []
            texts: list[Any] = []
            scores: list[Any] = []
            for line in raw_page:
                if not isinstance(line, (list, tuple)) or len(line) < 2:
                    continue
                boxes.append(line[0])
                content = line[1]
                if isinstance(content, (list, tuple)):
                    texts.append(content[0] if content else "")
                    scores.append(content[1] if len(content) > 1 else 0.0)
                else:
                    texts.append("")
                    scores.append(0.0)
            return {"rec_boxes": boxes, "rec_texts": texts, "rec_scores": scores}
        payload = getattr(raw_page, "json", None)
        if callable(payload):
            payload = payload()
        if isinstance(payload, str):
            import json

            payload = json.loads(payload)
        if isinstance(payload, dict):
            return payload.get("res", payload)
        return {}

    @staticmethod
    def _as_sequence(value: Any) -> list[Any]:
        if value is None:
            return []
        if isinstance(value, (list, tuple)):
            return list(value)
        to_list = getattr(value, "tolist", None)
        if callable(to_list):
            normalized = to_list()
            return normalized if isinstance(normalized, list) else [normalized]
        return [value]

    @staticmethod
    def _bbox(raw_box: Any) -> list[int]:
        # 统一将四点多边形或 [x1, y1, x2, y2] 转成矩形框。
        to_list = getattr(raw_box, "tolist", None)
        if callable(to_list):
            raw_box = to_list()
        if isinstance(raw_box, (list, tuple)) and raw_box and isinstance(raw_box[0], (list, tuple)):
            points = raw_box
            xs = [int(point[0]) for point in points]
            ys = [int(point[1]) for point in points]
            return [min(xs), min(ys), max(xs), max(ys)]
        if isinstance(raw_box, (list, tuple)) and len(raw_box) >= 4:
            return [int(value) for value in raw_box[:4]]
        return [0, 0, 0, 0]


def build_engine(name: str, *, max_image_edge: int = 4096) -> OcrEngine:
    if name == "mock":
        return MockOcrEngine()
    if name == "paddle":
        return PaddleOcrEngine(max_image_edge=max_image_edge)
    raise ValueError(f"Unsupported OCR engine: {name}")
