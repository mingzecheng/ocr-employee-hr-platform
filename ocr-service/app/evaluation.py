from __future__ import annotations

import re
from collections.abc import Callable
from collections import defaultdict
from dataclasses import dataclass

from .models import OcrEvaluationGroup, OcrEvaluationResult, OcrEvaluationSample, OcrTask


class EvaluationTaskNotFoundError(ValueError):
    """评测请求引用了不存在的 OCR 任务。"""

    def __init__(self, task_id: str) -> None:
        super().__init__(task_id)
        self.task_id = task_id


@dataclass
class _EvaluationCounters:
    sample_count: int = 0
    expected_field_count: int = 0
    detected_field_count: int = 0
    correct_field_count: int = 0
    failed_task_count: int = 0
    total_processing_duration_ms: float = 0.0

    def add(self, *, task: OcrTask, sample: OcrEvaluationSample) -> None:
        self.sample_count += 1
        self.total_processing_duration_ms += task.processing_duration_ms
        if task.status == "FAILED":
            self.failed_task_count += 1

        detected_fields = {field.field_code: field.value for field in task.fields}
        for field_code, expected_value in sample.expected_fields.items():
            self.expected_field_count += 1
            actual_value = detected_fields.get(field_code)
            if actual_value is None:
                continue
            self.detected_field_count += 1
            if _normalize_field_value(field_code, actual_value) == _normalize_field_value(
                field_code,
                expected_value,
            ):
                self.correct_field_count += 1

    def result(self, *, clarity: str) -> OcrEvaluationGroup:
        return OcrEvaluationGroup(
            clarity=clarity,
            sampleCount=self.sample_count,
            expectedFieldCount=self.expected_field_count,
            detectedFieldCount=self.detected_field_count,
            correctFieldCount=self.correct_field_count,
            fieldDetectionRate=_ratio(self.detected_field_count, self.expected_field_count),
            fieldAccuracy=_ratio(self.correct_field_count, self.expected_field_count),
            failedTaskCount=self.failed_task_count,
            failureRate=_ratio(self.failed_task_count, self.sample_count),
            averageProcessingDurationMs=_average(
                self.total_processing_duration_ms,
                self.sample_count,
            ),
        )


def evaluate_samples(
    *,
    samples: list[OcrEvaluationSample],
    task_lookup: Callable[[str], OcrTask | None],
) -> OcrEvaluationResult:
    """将即时提交的脱敏真值与已保存 OCR 结果比较，不在本地保存真值。"""

    total = _EvaluationCounters()
    grouped: dict[str, _EvaluationCounters] = defaultdict(_EvaluationCounters)
    for sample in samples:
        task = task_lookup(sample.task_id)
        if task is None:
            raise EvaluationTaskNotFoundError(sample.task_id)
        total.add(task=task, sample=sample)
        grouped[sample.clarity].add(task=task, sample=sample)

    by_clarity = [
        grouped[clarity].result(clarity=clarity)
        for clarity in ("HIGH", "MEDIUM", "LOW")
        if clarity in grouped
    ]
    summary = total.result(clarity="HIGH")
    return OcrEvaluationResult(
        sampleCount=summary.sample_count,
        expectedFieldCount=summary.expected_field_count,
        detectedFieldCount=summary.detected_field_count,
        correctFieldCount=summary.correct_field_count,
        fieldDetectionRate=summary.field_detection_rate,
        fieldAccuracy=summary.field_accuracy,
        failedTaskCount=summary.failed_task_count,
        failureRate=summary.failure_rate,
        averageProcessingDurationMs=summary.average_processing_duration_ms,
        byClarity=by_clarity,
    )


def _normalize_field_value(field_code: str, value: str) -> str:
    normalized = value.strip()
    if field_code in {"employee_id", "id_number", "phone"}:
        return re.sub(r"[\s-]+", "", normalized).upper()
    return re.sub(r"\s+", "", normalized).casefold()


def _ratio(numerator: int, denominator: int) -> float:
    return numerator / denominator if denominator else 0.0


def _average(total: float, count: int) -> float:
    return total / count if count else 0.0
