#!/usr/bin/env python3
"""OCR 实验评测脚本（论文第 6 章实验）。

读取 ``generate-ocr-samples.py`` 产出的样本（PNG/JPEG + ground truth JSON），
对每个样本调用 OCR，按字段级与样本级计算 9 类指标，并输出：

* 每个样本的原始 OCR 输出、真值、字段判定与端到端耗时（原始 JSON）。
* 按 版式 × 清晰度（及退化扰动）分组的指标汇总 Markdown 表。
* 失败案例清单：错误分类 + sample_id + 错误字段 + OCR 值 vs 真值。

两种运行模式
------------
* 真实模式：``OCR_BASE_URL``（默认 http://127.0.0.1:8000）可达时，上传图片到
  ``POST /api/ocr/tasks``，读取返回的 ``fields`` / ``textBlocks``。
* Mock 模式：设置 ``OCR_MOCK=1``（或服务不可达）时，使用本地带噪声的 seeded 模拟引擎，
  不依赖真实 OCR 模型，用于在未安装 PaddleOCR 时验证评测管道可跑通。

用法
----
    python scripts/generate-ocr-samples.py
    OCR_MOCK=1 python scripts/evaluate-ocr-experiment.py
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import random
import re
import statistics
import time
from collections import defaultdict
from io import BytesIO
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from uuid import uuid4

# --------------------------------------------------------------------------------------
# 字段格式规则：用于“格式校验失败”错误分类。
# --------------------------------------------------------------------------------------
def _looks_valid(field_code: str, value: str) -> bool:
    v = value.strip()
    if field_code in ("id_number",):
        return bool(re.fullmatch(r"\d{17}[\dXx]", v))
    if field_code in ("phone",):
        return bool(re.fullmatch(r"1\d{10}", v))
    if field_code in ("email",):
        return "@" in v and "." in v.split("@")[-1]
    if field_code in ("employee_id",):
        return bool(re.fullmatch(r"EMP-\d{3}", v))
    # 其余字段没有硬性格式约束，视为通过
    return True


# --------------------------------------------------------------------------------------
# 归一化编辑距离（Levenshtein），无第三方依赖。
# --------------------------------------------------------------------------------------
def _edit_ratio(a: str, b: str) -> float:
    a, b = a.strip(), b.strip()
    if not a and not b:
        return 1.0
    if not a or not b:
        return 0.0
    la, lb = len(a), len(b)
    prev = list(range(lb + 1))
    for i in range(1, la + 1):
        cur = [i] + [0] * lb
        for j in range(1, lb + 1):
            cost = 0 if a[i - 1] == b[j - 1] else 1
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
        prev = cur
    dist = prev[lb]
    return 1.0 - dist / max(la, lb)


# --------------------------------------------------------------------------------------
# Mock 引擎：按清晰度/退化施加确定性噪声，模拟真实 OCR 的典型错误。
# --------------------------------------------------------------------------------------
_CONFUSABLE = {"0": "O", "O": "0", "1": "l", "l": "1", "8": "B", "B": "8",
               "5": "S", "S": "5", "6": "G", "G": "6"}
_SURNAMES = ["张", "李", "王", "赵", "钱", "孙", "周", "吴"]


def _corrupt(value: str, rng: random.Random) -> str:
    if not value:
        return value
    chars = list(value)
    n_changes = rng.choice([1, 1, 2])
    for _ in range(n_changes):
        idx = rng.randrange(len(chars))
        c = chars[idx]
        if c in _CONFUSABLE:
            chars[idx] = _CONFUSABLE[c]
        elif "一" <= c <= "鿿" and rng.random() < 0.5:
            # 中文字段：替换为常见姓氏或删字，模拟误识
            if rng.random() < 0.5:
                chars[idx] = rng.choice(_SURNAMES)
            else:
                chars.pop(idx)
                break
        elif c.isdigit():
            chars[idx] = str(rng.randrange(10))
        else:
            chars[idx] = c.swapcase() if c.isalpha() else c
    return "".join(chars)


def mock_recognize(sample: dict, image_bytes: bytes) -> dict:
    """返回与真实 /api/ocr/tasks 同构的 {status, fields:[{fieldCode,value,confidence,validationStatus}]}。"""
    seed = int(hashlib.sha256(sample["sample_id"].encode()).hexdigest(), 16) % (2**32)
    rng = random.Random(seed)
    clarity = sample["degradation"]["clarity"]
    deg = sample["degradation"]

    det_p = {"high": 0.99, "medium": 0.93, "low": 0.80}[clarity]
    exact_p = {"high": 0.95, "medium": 0.78, "low": 0.50}[clarity]
    conf_mean = {"high": 0.95, "medium": 0.82, "low": 0.64}[clarity]
    fail_p = {"high": 0.0, "medium": 0.02, "low": 0.08}[clarity]
    if deg["occlusion"]:
        fail_p += 0.08
    if deg["rotate_deg"]:
        fail_p += 0.02

    t0 = time.perf_counter()
    failed = rng.random() < fail_p
    fields_out: list[dict] = []
    if not failed:
        gt = sample["fields"]
        other_values = list(gt.values())
        for code, truth in gt.items():
            field_det_p = det_p
            if deg["occlusion"] and deg["occluded_field"] == code:
                field_det_p *= 0.35  # 被遮挡字段更难检出
            if rng.random() > field_det_p:
                continue  # 字段未检出
            wrong = rng.random() > exact_p
            value = truth
            if wrong:
                err_kind = rng.choices(
                    ["char", "mapping", "format"], weights=[0.7, 0.15, 0.15], k=1
                )[0]
                if err_kind == "mapping" and len(other_values) > 1:
                    # 映射错误：取到了别的字段的值
                    value = rng.choice([v for v in other_values if v != truth] or [truth])
                elif err_kind == "format":
                    if code == "id_number":
                        value = truth[:17] + ("X" if rng.random() < 0.5 else str(rng.randrange(10)))
                    elif code == "phone":
                        value = truth[:10]
                    elif code == "email":
                        value = truth.replace("@", "_")
                    else:
                        value = _corrupt(truth, rng)
                else:
                    value = _corrupt(truth, rng)
            conf = rng.gauss(conf_mean, {"high": 0.04, "medium": 0.08, "low": 0.12}[clarity])
            if deg["occlusion"] and deg["occluded_field"] == code:
                conf -= 0.15
            if wrong:
                conf -= 0.12
            conf = max(0.20, min(0.99, conf))
            fields_out.append({
                "fieldCode": code,
                "value": value,
                "confidence": round(conf, 3),
                "validationStatus": "PASSED" if _looks_valid(code, value) else "FAILED",
            })
    latency_ms = (time.perf_counter() - t0) * 1000 + {
        "high": rng.gauss(1500, 120), "medium": rng.gauss(1100, 120), "low": rng.gauss(700, 120)
    }[clarity]

    return {
        "status": "FAILED" if failed else "SUCCEEDED",
        "fields": fields_out,
        "latency_ms": round(latency_ms, 2),
        "engine": "mock",
    }


# --------------------------------------------------------------------------------------
# 真实 OCR 服务调用。
# --------------------------------------------------------------------------------------
def api_recognize(sample: dict, image_bytes: bytes, base_url: str, token: str, timeout: int) -> dict:
    boundary = f"ocr-exp-{uuid4().hex}"
    filename = sample["image_file"].split("/")[-1]
    ctype = sample["content_type"]
    body = b"".join([
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"documentType\"\r\n\r\nemployee_profile\r\n".encode(),
        (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
         f"filename=\"{filename}\"\r\nContent-Type: {ctype}\r\n\r\n").encode(),
        image_bytes,
        f"\r\n--{boundary}--\r\n".encode(),
    ])
    request = Request(
        f"{base_url.rstrip('/')}/api/ocr/tasks",
        data=body,
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "X-OCR-Internal-Token": token,
        },
        method="POST",
    )
    t0 = time.perf_counter()
    try:
        with urlopen(request, timeout=timeout) as resp:
            result = json.loads(resp.read())
    except (HTTPError, URLError, OSError) as exc:
        return {"status": "FAILED", "fields": [], "latency_ms": round((time.perf_counter() - t0) * 1000, 2),
                "engine": "api", "error": str(exc)}
    latency_ms = (time.perf_counter() - t0) * 1000
    data = result.get("data") or {}
    return {
        "status": data.get("status", "FAILED"),
        "fields": data.get("fields") or [],
        "latency_ms": round(latency_ms, 2),
        "engine": "api",
    }


# --------------------------------------------------------------------------------------
# 字段判定与错误分类。
# --------------------------------------------------------------------------------------
def judge_field(code: str, truth: str, pred: dict | None) -> dict:
    if pred is None:
        return {"detected": False, "exact": False, "ratio": 0.0, "confidence": 0.0,
                "category": "missed", "pred_value": None, "valid_format": False}
    value = (pred.get("value") or "").strip()
    conf = float(pred.get("confidence", 0.0))
    exact = value == truth.strip()
    ratio = _edit_ratio(value, truth)
    valid = _looks_valid(code, value)
    if exact:
        category = "ok"
    elif not valid:
        category = "format"  # ③ 格式校验失败
    elif ratio >= 0.5:
        category = "char"    # ① 字符误识
    else:
        category = "mapping" # ② 版面/字段映射错误（值跑到了别的字段）
    return {"detected": True, "exact": exact, "ratio": round(ratio, 3),
            "confidence": round(conf, 3), "category": category,
            "pred_value": value, "valid_format": valid}


# --------------------------------------------------------------------------------------
# 汇总。
# --------------------------------------------------------------------------------------
def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    s = sorted(values)
    k = (len(s) - 1) * pct
    f = int(k)
    c = min(f + 1, len(s) - 1)
    return round(s[f] + (s[c] - s[f]) * (k - f), 2)


def evaluate(samples_dir: Path, mock: bool, base_url: str, token: str, timeout: int) -> dict:
    index = json.loads((samples_dir / "index.json").read_text(encoding="utf-8"))
    records: list[dict] = []
    for meta in index["samples"]:
        sid = meta["sample_id"]
        gt = json.loads((samples_dir / "ground-truth" / f"{sid}.json").read_text(encoding="utf-8"))
        image_bytes = (samples_dir / gt["image_file"]).read_bytes()
        # 图像哈希校验
        actual_hash = hashlib.sha256(image_bytes).hexdigest()
        hash_ok = actual_hash == gt["hash"]

        if mock:
            ocr = mock_recognize(gt, image_bytes)
        else:
            ocr = api_recognize(gt, image_bytes, base_url, token, timeout)

        pred_by_code = {f.get("fieldCode"): f for f in ocr.get("fields", [])}
        field_verdicts = {}
        for code, truth in gt["fields"].items():
            field_verdicts[code] = judge_field(code, truth, pred_by_code.get(code))

        records.append({
            "sample_id": sid,
            "layout": gt["layout"],
            "degradation": gt["degradation"],
            "status": ocr["status"],
            "hash_ok": hash_ok,
            "latency_ms": ocr["latency_ms"],
            "engine": ocr.get("engine"),
            "ground_truth": gt["fields"],
            "ocr_fields": ocr.get("fields", []),
            "field_verdicts": field_verdicts,
        })

    return _aggregate(records, mock)


def _aggregate(records: list[dict], mock: bool) -> dict:
    def empty_group():
        return {"samples": 0, "failed": 0, "exp_fields": 0, "detected": 0,
                "exact": 0, "edit_ok": 0, "manual_revision_samples": 0,
                "conf_correct": {"ge09": 0, "mid": 0, "lt07": 0},
                "conf_total": {"ge09": 0, "mid": 0, "lt07": 0},
                "lowconf_correct": 0, "lowconf_total": 0,
                "errors": {"char": 0, "mapping": 0, "format": 0, "missed": 0},
                "latencies": []}

    groups = defaultdict(empty_group)

    for r in records:
        key = (r["layout"], r["degradation"]["clarity"])
        g = groups[key]
        g["samples"] += 1
        g["latencies"].append(r["latency_ms"])
        if r["status"] == "FAILED":
            g["failed"] += 1
        need_revision = False
        for code, v in r["field_verdicts"].items():
            g["exp_fields"] += 1
            if v["detected"]:
                g["detected"] += 1
            if v["exact"]:
                g["exact"] += 1
            if v["ratio"] >= 0.9:
                g["edit_ok"] += 1
            if not v["exact"]:
                need_revision = True
                g["errors"][v["category"]] = g["errors"].get(v["category"], 0) + 1
            # 置信度分层（仅统计检出字段）
            if v["detected"]:
                c = v["confidence"]
                bin_key = "ge09" if c >= 0.9 else ("lt07" if c < 0.7 else "mid")
                g["conf_total"][bin_key] += 1
                if v["exact"]:
                    g["conf_correct"][bin_key] += 1
            # 低置信度召回：真值正确但置信度低
            if v["exact"]:
                g["lowconf_total"] += 1
                if v["confidence"] < 0.7:
                    g["lowconf_correct"] += 1
        if need_revision and r["status"] != "FAILED":
            g["manual_revision_samples"] += 1

    def ratios(g):
        exp = g["exp_fields"] or 1
        suc = g["samples"] - g["failed"]
        return {
            "samples": g["samples"], "failed": g["failed"],
            "exp_fields": g["exp_fields"],
            "field_detection_rate": round(g["detected"] / exp, 4),
            "field_exact_accuracy": round(g["exact"] / exp, 4),
            "field_edit_accuracy": round(g["edit_ok"] / exp, 4),
            "failure_rate": round(g["failed"] / g["samples"], 4),
            "manual_revision_rate": round(g["manual_revision_samples"] / max(suc, 1), 4),
            "errors": {k: g["errors"].get(k, 0) for k in ("char", "mapping", "format", "missed")},
            "confidence_strata": {
                bin: {
                    "total": g["conf_total"][bin],
                    "accuracy": round(g["conf_correct"][bin] / g["conf_total"][bin], 4)
                    if g["conf_total"][bin] else None,
                } for bin in ("ge09", "mid", "lt07")
            },
            "low_confidence_correct_recall": round(g["lowconf_correct"] / g["lowconf_total"], 4)
            if g["lowconf_total"] else None,
            "latency_p50_ms": percentile(g["latencies"], 0.5),
            "latency_p95_ms": percentile(g["latencies"], 0.95),
        }

    table = [
        {"layout": layout, "clarity": clarity, **ratios(g)}
        for (layout, clarity), g in sorted(groups.items())
    ]

    # 总体（按清晰度汇总）
    by_clarity = defaultdict(empty_group)
    for r in records:
        g = by_clarity[r["degradation"]["clarity"]]
        g["samples"] += 1
        g["latencies"].append(r["latency_ms"])
        if r["status"] == "FAILED":
            g["failed"] += 1
        need_rev = False
        for code, v in r["field_verdicts"].items():
            g["exp_fields"] += 1
            if v["detected"]: g["detected"] += 1
            if v["exact"]: g["exact"] += 1
            if v["ratio"] >= 0.9: g["edit_ok"] += 1
            if not v["exact"]:
                need_rev = True
                g["errors"][v["category"]] = g["errors"].get(v["category"], 0) + 1
            if v["detected"]:
                c = v["confidence"]
                bk = "ge09" if c >= 0.9 else ("lt07" if c < 0.7 else "mid")
                g["conf_total"][bk] += 1
                if v["exact"]: g["conf_correct"][bk] += 1
            if v["exact"]:
                g["lowconf_total"] += 1
                if v["confidence"] < 0.7: g["lowconf_correct"] += 1
        if need_rev and r["status"] != "FAILED":
            g["manual_revision_samples"] += 1
    overall = [{"clarity": k, **ratios(v)} for k, v in sorted(by_clarity.items())]

    # 失败案例清单（字段级错误）
    failures = []
    for r in records:
        for code, v in r["field_verdicts"].items():
            if v["category"] != "ok":
                failures.append({
                    "sample_id": r["sample_id"], "layout": r["layout"],
                    "clarity": r["degradation"]["clarity"],
                    "field": code, "category": v["category"],
                    "ocr_value": v["pred_value"], "ground_truth": r["ground_truth"][code],
                    "confidence": v["confidence"],
                })

    return {
        "mode": "mock" if mock else "api",
        "engine_hint": "mock-seeded-noise" if mock else base_url,
        "sample_count": len(records),
        "by_layout_clarity": table,
        "overall_by_clarity": overall,
        "field_failures": failures,
        "records": records,
    }


def _md_table(rows: list[dict], cols: list[tuple[str, str]]) -> str:
    head = "| " + " | ".join(label for _k, label in cols) + " |"
    sep = "| " + " | ".join("---:" for _ in cols) + " |"
    lines = [head, sep]
    for r in rows:
        cells = []
        for k, _label in cols:
            cells.append(str(r.get(k, "")))
        lines.append("| " + " | ".join(cells) + " |")
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate OCR on the synthetic experiment sample set.")
    root = Path(__file__).resolve().parent
    parser.add_argument("--samples", type=Path, default=root / "samples")
    parser.add_argument("--out-dir", type=Path, default=root / "experiment-results")
    parser.add_argument("--base-url", default=os.getenv("OCR_BASE_URL", "http://127.0.0.1:8000"))
    parser.add_argument("--token", default=os.getenv("OCR_INTERNAL_TOKEN", "local-ocr-internal-token"))
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument(
        "--mock", action="store_true",
        default=os.getenv("OCR_MOCK", "") == "1",
        help="使用本地 seeded mock 引擎（也可由 OCR_MOCK=1 触发）",
    )
    args = parser.parse_args()

    if not (args.samples / "index.json").is_file():
        parser.error(f"samples not found: {args.samples}. Run generate-ocr-samples.py first.")

    result = evaluate(args.samples, args.mock, args.base_url, args.token, args.timeout)

    args.out_dir.mkdir(parents=True, exist_ok=True)
    ts = time.strftime("%Y%m%d-%H%M%S")
    raw_path = args.out_dir / f"run-{ts}.json"
    raw_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

    # 控制台汇总
    print(f"[mode={result['mode']}] samples={result['sample_count']} -> {raw_path}")
    print("\n## Overall by clarity")
    print(_md_table(result["overall_by_clarity"], [
        ("clarity", "清晰度"), ("samples", "样本"), ("failure_rate", "失败率"),
        ("field_detection_rate", "检出率"), ("field_exact_accuracy", "字段准确率"),
        ("field_edit_accuracy", "编辑距离准确率"), ("manual_revision_rate", "人工修订率"),
        ("latency_p50_ms", "P50 ms"), ("latency_p95_ms", "P95 ms"),
    ]))
    print("\n## By layout x clarity (top-level accuracy)")
    print(_md_table(result["by_layout_clarity"], [
        ("layout", "版式"), ("clarity", "清晰度"), ("samples", "样本"),
        ("field_exact_accuracy", "字段准确率"), ("field_detection_rate", "检出率"),
        ("failure_rate", "失败率"),
    ]))
    print(f"\nfield-level failure cases: {len(result['field_failures'])}")


if __name__ == "__main__":
    main()
