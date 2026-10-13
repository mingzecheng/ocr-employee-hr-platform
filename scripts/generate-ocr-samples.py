#!/usr/bin/env python3
"""生成 OCR 实验用脱敏合成样本集（论文第 6 章实验素材）。

设计要点
--------
* 全部样本由 PIL 程序化渲染，不使用任何真实证件照片，也不手工 PS。
* 4 种版式：身份证正面 / 员工入职登记表 / 简历摘要页 / 劳动合同首页。
* 3 档清晰度：high（原始渲染）/ medium（轻模糊 + JPEG）/ low（强模糊 + 噪声 + 亮度对比度下降）。
* 对约 40% 样本随机叠加成像扰动：±5°/±10° 旋转、半透明阴影、单字段遮挡。
* 所有姓名、证件号、手机号、邮箱均为写死的脱敏化名与测试号段，不含真实个人信息。

每个样本输出两份文件：
* ``<out>/png/<sample_id>.png|.jpg``：渲染后的图像字节。
* ``<out>/ground-truth/<sample_id>.json``：字段真值 + 退化元数据 + 图像 sha256。

用法
----
    python scripts/generate-ocr-samples.py
    python scripts/generate-ocr-samples.py --seed 20261013 --out scripts/samples
"""
from __future__ import annotations

import argparse
import hashlib
import json
import random
from dataclasses import dataclass, field
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

# --------------------------------------------------------------------------------------
# 脱敏化名数据：全部为写死的明显测试值，不对应任何真实个人。
# --------------------------------------------------------------------------------------
PERSONAS: list[dict] = [
    {
        "name": "张三甲", "gender": "男", "ethnicity": "汉",
        "birth_date": "1990年01月01日", "address": "北京市朝阳区示范路1号院",
        "id_number": "110101199001011234", "employee_id": "EMP-001",
        "phone": "13800138000", "email": "zhangsanjia@example.test",
        "emergency_contact": "赵五 13900139000",
    },
    {
        "name": "李四乙", "gender": "女", "ethnicity": "汉",
        "birth_date": "1985年05月05日", "address": "上海市徐汇区样板路2号",
        "id_number": "310104198505056789", "employee_id": "EMP-002",
        "phone": "13900139000", "email": "lisiyi@example.test",
        "emergency_contact": "钱六 13800138000",
    },
    {
        "name": "王五福", "gender": "男", "ethnicity": "回",
        "birth_date": "1992年03月03日", "address": "深圳市南山区示范道3号",
        "id_number": "440305199203034567", "employee_id": "EMP-003",
        "phone": "13700137000", "email": "wangwufu@example.test",
        "emergency_contact": "孙七 13700137000",
    },
    {
        "name": "赵六丁", "gender": "女", "ethnicity": "汉",
        "birth_date": "1994年12月12日", "address": "南京市鼓楼区样板巷4号",
        "id_number": "320106199412128901", "employee_id": "EMP-004",
        "phone": "13600136000", "email": "zhaoliuding@example.test",
        "emergency_contact": "周八 13600136000",
    },
]

# 版式定义：field_code -> (标签, 取值 lambda(persona))
LAYOUTS: dict[str, dict] = {
    "idcard_front": {
        "title": "居民身份证（正面 · 合成脱敏样本）",
        "fields": [
            ("name", "姓名", lambda p: p["name"]),
            ("gender", "性别", lambda p: p["gender"]),
            ("ethnicity", "民族", lambda p: p["ethnicity"]),
            ("birth_date", "出生", lambda p: p["birth_date"]),
            ("address", "住址", lambda p: p["address"]),
            ("id_number", "公民身份号码", lambda p: p["id_number"]),
        ],
    },
    "onboarding_form": {
        "title": "员工入职登记表（合成脱敏样本）",
        "fields": [
            ("name", "姓名", lambda p: p["name"]),
            ("employee_id", "工号", lambda p: p["employee_id"]),
            ("department", "部门", lambda p: "人力资源部"),
            ("position", "岗位", lambda p: "软件工程师"),
            ("phone", "手机号", lambda p: p["phone"]),
            ("email", "邮箱", lambda p: p["email"]),
            ("education", "学历", lambda p: "本科"),
            ("hire_date", "入职日期", lambda p: "2024-07-01"),
        ],
    },
    "resume_summary": {
        "title": "简历 / 档案摘要页（合成脱敏样本）",
        "fields": [
            ("name", "姓名", lambda p: p["name"]),
            ("school", "毕业院校", lambda p: "示范大学"),
            ("major", "专业", lambda p: "计算机科学与技术"),
            ("years", "工作年限", lambda p: "5年"),
            ("emergency_contact", "紧急联系人", lambda p: p["emergency_contact"]),
        ],
    },
    "contract_first_page": {
        "title": "劳动合同 首页（合成脱敏样本）",
        "fields": [
            ("party_a", "甲方", lambda p: "示范企业有限公司"),
            ("party_b", "乙方", lambda p: p["name"]),
            ("term", "合同期限", lambda p: "2024-07-01 至 2027-06-30"),
            ("position", "岗位", lambda p: "软件工程师"),
            ("salary", "薪资", lambda p: "人民币12000元/月"),
            ("sign_date", "签订日期", lambda p: "2024-07-01"),
        ],
    },
}

CLARITIES = ("high", "medium", "low")
FONT_CANDIDATES = [
    "/System/Library/Fonts/Hiragino Sans GB.ttc",
    "/System/Library/Fonts/STHeiti Light.ttc",
    "/System/Library/Fonts/Supplemental/Songti.ttc",
    "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
]


def _load_font(size: int) -> ImageFont.FreeTypeFont:
    for path in FONT_CANDIDATES:
        try:
            return ImageFont.truetype(path, size=size)
        except OSError:
            continue
    return ImageFont.load_default(size=size)


@dataclass
class Rendered:
    image: Image.Image
    value_boxes: dict[str, tuple[int, int, int, int]] = field(default_factory=dict)


def _render_layout(layout_key: str, persona: dict) -> Rendered:
    layout = LAYOUTS[layout_key]
    width, height = 1000, 700
    image = Image.new("RGB", (width, height), "white")
    draw = ImageDraw.Draw(image)

    title_font = _load_font(34)
    label_font = _load_font(26)
    value_font = _load_font(26)

    # 卡片边框与标题
    draw.rectangle((20, 20, width - 20, height - 20), outline="#888888", width=3)
    draw.text((50, 45), layout["title"], fill="#222222", font=title_font)
    draw.line((50, 100, width - 50, 100), fill="#BBBBBB", width=2)

    value_boxes: dict[str, tuple[int, int, int, int]] = {}
    row_y = 140
    label_x = 80
    value_x = 300
    step = 62
    for field_code, label, value_fn in layout["fields"]:
        value = value_fn(persona)
        draw.text((label_x, row_y), f"{label}：", fill="#333333", font=label_font)
        draw.text((value_x, row_y), value, fill="black", font=value_font)
        # 记录取值区域（粗略，供遮挡使用）
        value_w = draw.textlength(value, font=value_font)
        box = (value_x - 6, row_y - 4, value_x + int(value_w) + 10, row_y + 38)
        value_boxes[field_code] = box
        # 浅色下划线分隔
        draw.line((label_x, row_y + 46, width - 60, row_y + 46), fill="#E5E5E5", width=1)
        row_y += step

    return Rendered(image=image, value_boxes=value_boxes)


def _apply_perturbations(
    image: Image.Image,
    value_boxes: dict[str, tuple[int, int, int, int]],
    rng: random.Random,
) -> tuple[Image.Image, dict]:
    """随机施加一种成像扰动，返回扰动后的图像与扰动描述。"""
    meta = {"rotate_deg": 0, "shadow": False, "occlusion": False, "occluded_field": None}
    choice = rng.choice(["none", "none", "rotate", "shadow", "occlusion"])

    if choice == "rotate":
        deg = rng.choice([-10, -5, 5, 10])
        image = image.rotate(deg, resample=Image.Resampling.BICUBIC, expand=True, fillcolor="white")
        meta["rotate_deg"] = deg
    elif choice == "shadow":
        overlay = Image.new("RGBA", image.size, (0, 0, 0, 0))
        od = ImageDraw.Draw(overlay)
        # 左半侧半透明暗块，模拟拍摄光照不均
        od.rectangle((0, 0, int(image.width * 0.45), image.height), fill=(60, 60, 60, 90))
        image = Image.alpha_composite(image.convert("RGBA"), overlay).convert("RGB")
        meta["shadow"] = True
    elif choice == "occlusion":
        draw = ImageDraw.Draw(image)
        field_code = rng.choice(list(value_boxes.keys()))
        box = value_boxes[field_code]
        # 白色（模拟手指/印章遮挡）覆盖取值区域
        draw.rectangle(box, fill="white", outline="#DDDDDD")
        meta["occlusion"] = True
        meta["occluded_field"] = field_code

    return image, meta


def _apply_clarity(image: Image.Image, clarity: str) -> tuple[bytes, str]:
    """按清晰度档返回（字节, 扩展名）。"""
    if clarity == "high":
        buf = BytesIO()
        image.save(buf, format="PNG")
        return buf.getvalue(), "png"

    if clarity == "medium":
        degraded = image.filter(ImageFilter.GaussianBlur(radius=1.0))
        quality = 75
    else:  # low
        degraded = image.filter(ImageFilter.GaussianBlur(radius=2.2))
        degraded = ImageEnhance.Brightness(degraded).enhance(0.70)
        degraded = ImageEnhance.Contrast(degraded).enhance(0.80)
        quality = 50

    buf = BytesIO()
    degraded.save(buf, format="JPEG", quality=quality, optimize=True)
    return buf.getvalue(), "jpg"


def generate(out_dir: Path, seed: int, personas_per_layout: int = 2) -> dict:
    rng = random.Random(seed)
    png_dir = out_dir / "png"
    gt_dir = out_dir / "ground-truth"
    png_dir.mkdir(parents=True, exist_ok=True)
    gt_dir.mkdir(parents=True, exist_ok=True)

    index: list[dict] = []
    sample_no = 0
    for layout_key in LAYOUTS:
        for clarity in CLARITIES:
            for p_idx in range(personas_per_layout):
                persona = PERSONAS[(hash(layout_key) + p_idx) % len(PERSONAS)]
                rendered = _render_layout(layout_key, persona)
                perturbed_image, perturb_meta = _apply_perturbations(
                    rendered.image, rendered.value_boxes, rng
                )
                content, ext = _apply_clarity(perturbed_image, clarity)
                sample_no += 1
                sample_id = f"{layout_key}_{clarity}_{sample_no:03d}"
                image_name = f"{sample_id}.{ext}"
                (png_dir / image_name).write_bytes(content)
                sha = hashlib.sha256(content).hexdigest()

                # 真值字段：取版式定义里的原始取值（遮挡只影响图像，不影响真值）
                fields = {fc: fn(persona) for fc, _label, fn in LAYOUTS[layout_key]["fields"]}

                gt = {
                    "sample_id": sample_id,
                    "layout": layout_key,
                    "degradation": {
                        "clarity": clarity,
                        "rotate_deg": perturb_meta["rotate_deg"],
                        "shadow": perturb_meta["shadow"],
                        "occlusion": perturb_meta["occlusion"],
                        "occluded_field": perturb_meta["occluded_field"],
                    },
                    "fields": fields,
                    "source": "synthetic",
                    "image_file": f"png/{image_name}",
                    "content_type": "image/png" if ext == "png" else "image/jpeg",
                    "hash": sha,
                }
                (gt_dir / f"{sample_id}.json").write_text(
                    json.dumps(gt, ensure_ascii=False, indent=2), encoding="utf-8"
                )
                index.append({
                    "sample_id": sample_id, "layout": layout_key, "clarity": clarity,
                    "rotate_deg": perturb_meta["rotate_deg"], "shadow": perturb_meta["shadow"],
                    "occlusion": perturb_meta["occlusion"], "fields": len(fields),
                })

    manifest = {
        "seed": seed,
        "sample_count": len(index),
        "layouts": list(LAYOUTS.keys()),
        "clarities": list(CLARITIES),
        "personas_per_layout_per_clarity": personas_per_layout,
        "desensitization_note": (
            "All names, ID numbers, phone numbers and emails are fixed synthetic "
            "test values; no real personal information is used."
        ),
        "samples": index,
    }
    (out_dir / "index.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate synthetic OCR experiment samples.")
    root = Path(__file__).resolve().parent
    parser.add_argument("--out", type=Path, default=root / "samples", help="输出目录")
    parser.add_argument("--seed", type=int, default=20261013, help="随机种子（可复现）")
    parser.add_argument(
        "--personas", type=int, default=2,
        help="每个版式×清晰度组合使用的化名数量（默认 2，4×3×2=24 基础样本）",
    )
    args = parser.parse_args()
    manifest = generate(args.out, args.seed, args.personas)
    print(json.dumps({
        "out": str(args.out),
        "sample_count": manifest["sample_count"],
        "layouts": manifest["layouts"],
        "clarities": manifest["clarities"],
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
