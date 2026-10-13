#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
from io import BytesIO
from pathlib import Path
from typing import NamedTuple
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from uuid import uuid4

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont, ImageOps


class ClaritySample(NamedTuple):
    clarity: str
    layout: str
    content_type: str
    filename: str
    content: bytes
    expected_fields: dict[str, str]


RECORDS = (
    ("张三", "EMP-001"),
    ("Lin Xiao", "EMP-104"),
    ("Wang Min", "EMP-205"),
    ("Chen An", "EMP-307"),
)
LAYOUTS = ("PLAIN", "BORDERED", "SKEWED")


def _encode_image(image: Image.Image, *, image_format: str, quality: int | None = None) -> bytes:
    output = BytesIO()
    options = {"format": image_format}
    if quality is not None:
        options.update(quality=quality, optimize=True, subsampling=2)
    image.save(output, **options)
    return output.getvalue()


def _record_variants(source: Image.Image) -> list[tuple[str, Image.Image]]:
    records: list[tuple[str, str, Image.Image]] = []
    for employee_name, employee_id in RECORDS:
        image = source.copy()
        if employee_name != RECORDS[0][0]:
            draw = ImageDraw.Draw(image)
            width, height = image.size
            draw.rectangle(
                (int(width * 0.29), int(height * 0.12), int(width * 0.68), int(height * 0.39)),
                fill="white",
            )
            name_font = ImageFont.load_default(size=max(22, int(height * 0.15)))
            draw.text((int(width * 0.30), int(height * 0.18)), employee_name, fill="black", font=name_font)
            draw.rectangle(
                (int(width * 0.29), int(height * 0.43), int(width * 0.68), int(height * 0.68)),
                fill="white",
            )
            font = ImageFont.load_default(size=max(18, int(height * 0.125)))
            draw.text((int(width * 0.30), int(height * 0.48)), employee_id, fill="black", font=font)
        records.append((employee_name, employee_id, image))
    return records


def _layout_variants(source: Image.Image) -> list[tuple[str, Image.Image]]:
    bordered = ImageOps.expand(source, border=(28, 24), fill="white")
    draw = ImageDraw.Draw(bordered)
    width, height = bordered.size
    draw.rectangle((10, 10, width - 11, height - 11), outline="#707070", width=2)
    draw.line((10, int(height * 0.45), width - 11, int(height * 0.45)), fill="#B0B0B0", width=2)
    skewed = source.rotate(1.2, resample=Image.Resampling.BICUBIC, expand=True, fillcolor="white")
    return [("PLAIN", source.copy()), ("BORDERED", bordered), ("SKEWED", skewed)]


def _clarity_image(image: Image.Image, clarity: str, variant_index: int) -> tuple[str, str, bytes]:
    if clarity == "HIGH":
        enhanced = image.copy()
        if variant_index == 1:
            enhanced = ImageEnhance.Contrast(enhanced).enhance(1.08)
        elif variant_index == 2:
            enhanced = ImageEnhance.Sharpness(enhanced).enhance(1.2)
        return "image/png", "png", _encode_image(enhanced, image_format="PNG")

    if clarity == "MEDIUM":
        scales = (0.84, 0.72, 0.62)
        qualities = (72, 62, 52)
        image_format = "JPEG"
        quality = qualities[variant_index]
    else:
        scales = (0.50, 0.40, 0.32)
        qualities = (34, 22, 14)
        image_format = "JPEG"
        quality = qualities[variant_index]

    scale = scales[variant_index]
    resized = image.resize(
        (max(1, round(image.width * scale)), max(1, round(image.height * scale))),
        Image.Resampling.LANCZOS,
    )
    if clarity == "LOW":
        resized = ImageOps.grayscale(resized).filter(
            ImageFilter.GaussianBlur(radius=(0.25, 0.5, 0.8)[variant_index])
        ).convert("RGB")
    return "image/jpeg", "jpg", _encode_image(resized, image_format=image_format, quality=quality)


def clarity_samples(source_path: Path) -> list[ClaritySample]:
    with Image.open(source_path) as source:
        original = ImageOps.exif_transpose(source).convert("RGB")
    samples: list[ClaritySample] = []
    for clarity in ("HIGH", "MEDIUM", "LOW"):
        for layout, layout_image in _layout_variants(original):
            for record_index, (employee_name, employee_id, image) in enumerate(_record_variants(layout_image)):
                content_type, extension, content = _clarity_image(image, clarity, record_index % 3)
                sample_id = f"{clarity.lower()}-{layout.lower()}-{employee_id.lower()}"
                samples.append(ClaritySample(
                    clarity=clarity,
                    layout=layout,
                    content_type=content_type,
                    filename=f"{sample_id}.{extension}",
                    content=content,
                    expected_fields={"employee_name": employee_name, "employee_id": employee_id},
                ))
    return samples


def _post_json(base_url: str, path: str, body: bytes, content_type: str, timeout: int) -> dict:
    request = Request(
        f"{base_url.rstrip('/')}{path}",
        data=body,
        headers={
            "Content-Type": content_type,
            "X-OCR-Internal-Token": os.getenv("OCR_INTERNAL_TOKEN", "local-ocr-internal-token"),
        },
        method="POST",
    )
    try:
        with urlopen(request, timeout=timeout) as response:
            result = json.loads(response.read())
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"OCR API {path} returned HTTP {error.code}: {detail}") from error
    except URLError as error:
        raise RuntimeError(f"OCR API {path} is unavailable: {error.reason}") from error
    if str(result.get("code")) != "0":
        raise RuntimeError(f"OCR API {path} returned {result.get('code')}: {result.get('message')}")
    return result["data"]


def _create_task(base_url: str, clarity: str, layout: str, content_type: str, filename: str,
                 content: bytes, timeout: int) -> dict:
    boundary = f"ocr-clarity-{uuid4().hex}"
    body = b"".join(
        [
            f"--{boundary}\r\nContent-Disposition: form-data; name=\"documentType\"\r\n\r\nemployee_profile\r\n".encode(),
            (
                f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
                f"filename=\"{filename}\"\r\nContent-Type: {content_type}\r\n\r\n"
            ).encode(),
            content,
            f"\r\n--{boundary}--\r\n".encode(),
        ]
    )
    task = _post_json(
        base_url,
        "/api/ocr/tasks",
        body,
        f"multipart/form-data; boundary={boundary}",
        timeout,
    )
    return {
        "taskId": task["taskId"],
        "clarity": clarity,
        "layout": layout,
        "sampleId": Path(filename).stem,
        "status": task["status"],
    }


def run_evaluation(base_url: str, source_path: Path, timeout: int) -> dict:
    samples = clarity_samples(source_path)
    tasks = [
        _create_task(base_url, sample.clarity, sample.layout, sample.content_type, sample.filename,
                     sample.content, timeout)
        for sample in samples
    ]
    evaluation = _post_json(
        base_url,
        "/api/ocr/evaluations",
        json.dumps(
            {
                "samples": [
                    {
                        "taskId": task["taskId"],
                        "clarity": task["clarity"],
                        "expectedFields": sample.expected_fields,
                    }
                    for task, sample in zip(tasks, samples, strict=True)
                ]
            },
            ensure_ascii=False,
        ).encode("utf-8"),
        "application/json",
        timeout,
    )
    return {
        "dataset": "synthetic-platform-derived",
        "sourceImage": source_path.name,
        "sampleCount": len(tasks),
        "sampleCountPerClarity": {clarity: 12 for clarity in ("HIGH", "MEDIUM", "LOW")},
        "sampleCountPerLayout": {layout: 12 for layout in LAYOUTS},
        "limitations": "Thirty-six synthetic samples derive from one base image, cover only name and employee ID, and do not represent real enterprise scans.",
        "tasks": tasks,
        "metrics": evaluation,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate PaddleOCR on 36 synthetic image-clarity samples.")
    parser.add_argument(
        "--base-url",
        default=os.getenv("OCR_BASE_URL", "http://127.0.0.1:8000"),
        help="OCR service base URL",
    )
    parser.add_argument(
        "--source-image",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "ocr-service/tests/fixtures/platform-smoke.png",
        help="Synthetic source image containing 张三 and EMP-001",
    )
    parser.add_argument("--timeout", type=int, default=180, help="Per-request timeout in seconds")
    args = parser.parse_args()
    if not args.source_image.is_file():
        parser.error(f"source image not found: {args.source_image}")
    print(json.dumps(run_evaluation(args.base_url, args.source_image, args.timeout), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
