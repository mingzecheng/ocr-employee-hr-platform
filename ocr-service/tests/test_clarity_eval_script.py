from __future__ import annotations

import importlib.util
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT_PATH = ROOT / "scripts/evaluate-ocr-clarity.py"
FIXTURE_PATH = ROOT / "ocr-service/tests/fixtures/platform-smoke.png"
SPEC = importlib.util.spec_from_file_location("evaluate_ocr_clarity", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
evaluation_script = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(evaluation_script)


def test_clarity_dataset_covers_multiple_records_layouts_and_quality_groups():
    samples = evaluation_script.clarity_samples(FIXTURE_PATH)

    assert len(samples) == 36
    assert Counter(sample.clarity for sample in samples) == {
        "HIGH": 12,
        "MEDIUM": 12,
        "LOW": 12,
    }
    assert {
        sample.expected_fields["employee_id"] for sample in samples
    } == {"EMP-001", "EMP-104", "EMP-205", "EMP-307"}
    assert {
        sample.expected_fields["employee_name"] for sample in samples
    } == {"张三", "Lin Xiao", "Wang Min", "Chen An"}
    assert {sample.layout for sample in samples} == {"PLAIN", "BORDERED", "SKEWED"}


def test_evaluation_payload_uses_sample_specific_ground_truth(monkeypatch):
    captured: dict = {}

    def create_task(_base_url, clarity, layout, _content_type, filename, _content, _timeout):
        sample_id = Path(filename).stem
        return {"taskId": sample_id, "clarity": clarity, "layout": layout,
                "sampleId": sample_id, "status": "SUCCEEDED"}

    def post_json(_base_url, path, body, _content_type, _timeout):
        assert path == "/api/ocr/evaluations"
        captured.update(evaluation_script.json.loads(body))
        return {"sampleCount": 36, "byClarity": []}

    monkeypatch.setattr(evaluation_script, "_create_task", create_task)
    monkeypatch.setattr(evaluation_script, "_post_json", post_json)

    result = evaluation_script.run_evaluation("http://ocr.test", FIXTURE_PATH, timeout=1)

    assert len(captured["samples"]) == 36
    assert {sample["expectedFields"]["employee_id"] for sample in captured["samples"]} == {
        "EMP-001", "EMP-104", "EMP-205", "EMP-307"
    }
    assert result["sampleCountPerClarity"] == {"HIGH": 12, "MEDIUM": 12, "LOW": 12}
    assert result["sampleCountPerLayout"] == {"PLAIN": 12, "BORDERED": 12, "SKEWED": 12}
    assert {task["layout"] for task in result["tasks"]} == {"PLAIN", "BORDERED", "SKEWED"}
