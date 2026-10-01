from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps

from .models import TextBlock


_FONT_CANDIDATES = (
    "/System/Library/Fonts/PingFang.ttc",
    "/System/Library/Fonts/STHeiti Medium.ttc",
    "/System/Library/Fonts/Hiragino Sans GB.ttc",
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
    "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
)
_BOX_COLORS = ("#D32F2F", "#1565C0", "#2E7D32", "#EF6C00", "#6A1B9A")


def render_detection_preview(
    *,
    source_path: Path,
    output_path: Path,
    text_blocks: list[TextBlock],
) -> tuple[int, int]:
    """在原图副本上绘制检测框和简短标注，并保存为 PNG。"""

    with Image.open(source_path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")
        width, height = image.size
        draw = ImageDraw.Draw(image)
        font_size = max(14, min(28, round(max(width, height) / 80)))
        font = _load_font(font_size)
        line_width = max(2, round(font_size / 7))

        for index, block in enumerate(text_blocks, start=1):
            left, top, right, bottom = _clip_bbox(block.bbox, width=width, height=height)
            color = _BOX_COLORS[(index - 1) % len(_BOX_COLORS)]
            draw.rectangle((left, top, right, bottom), outline=color, width=line_width)
            label = _fit_label(
                f"{index} {block.confidence:.2f} {block.text}",
                font=font,
                max_width=max(80, width - 8),
            )
            label_box = draw.textbbox((0, 0), label, font=font)
            label_width = label_box[2] - label_box[0] + 8
            label_height = label_box[3] - label_box[1] + 6
            label_left = min(max(0, left), max(0, width - label_width))
            label_top = top - label_height
            if label_top < 0:
                label_top = min(height - label_height, bottom)
            draw.rectangle(
                (label_left, label_top, label_left + label_width, label_top + label_height),
                fill=color,
            )
            draw.text(
                (label_left + 4, label_top + 3),
                label,
                fill="white",
                font=font,
            )

        output_path.parent.mkdir(parents=True, exist_ok=True)
        image.save(output_path, format="PNG")
        return width, height


def _load_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for candidate in _FONT_CANDIDATES:
        path = Path(candidate)
        if path.exists():
            try:
                return ImageFont.truetype(str(path), size=size)
            except OSError:
                continue
    return ImageFont.load_default()


def _clip_bbox(bbox: list[int], *, width: int, height: int) -> tuple[int, int, int, int]:
    left, top, right, bottom = (bbox + [0, 0, 0, 0])[:4]
    left = max(0, min(width - 1, int(left)))
    top = max(0, min(height - 1, int(top)))
    right = max(left + 1, min(width - 1, int(right)))
    bottom = max(top + 1, min(height - 1, int(bottom)))
    return left, top, right, bottom


def _fit_label(text: str, *, font: ImageFont.ImageFont, max_width: int) -> str:
    if font.getlength(text) <= max_width:
        return text
    suffix = "..."
    remaining = text
    while remaining and font.getlength(remaining + suffix) > max_width:
        remaining = remaining[:-1]
    return remaining + suffix if remaining else suffix
