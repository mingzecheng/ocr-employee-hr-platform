from __future__ import annotations

import re
from collections.abc import Callable

from .models import FieldResult, TextBlock


class EmployeeProfileFieldExtractor:
    """从员工档案 OCR 文本块中抽取带标签的基础字段。"""

    _labeled_value = re.compile(r"^\s*(?P<label>[^:：]+?)\s*[:：]\s*(?P<value>.+?)\s*$")

    def __init__(self, *, low_confidence_threshold: float = 0.85) -> None:
        if not 0 <= low_confidence_threshold <= 1:
            raise ValueError("low_confidence_threshold must be between zero and one")
        self._low_confidence_threshold = low_confidence_threshold
        self._rules: dict[str, tuple[str, Callable[[str], bool], str]] = {
            "员工姓名": ("employee_name", self._is_name, "员工姓名格式错误"),
            "姓名": ("employee_name", self._is_name, "员工姓名格式错误"),
            "员工编号": ("employee_id", self._is_employee_id, "员工编号格式错误"),
            "工号": ("employee_id", self._is_employee_id, "员工编号格式错误"),
            "身份证号": ("id_number", self._is_id_number, "身份证号格式错误"),
            "身份证号码": ("id_number", self._is_id_number, "身份证号格式错误"),
            "联系电话": ("phone", self._is_phone, "联系电话格式错误"),
            "手机号码": ("phone", self._is_phone, "联系电话格式错误"),
            "手机号": ("phone", self._is_phone, "联系电话格式错误"),
        }
        self._validators = {
            field_code: (validator, failure_message)
            for field_code, validator, failure_message in self._rules.values()
        }

    def extract(self, text_blocks: list[TextBlock]) -> list[FieldResult]:
        fields: list[FieldResult] = []
        extracted_codes: set[str] = set()
        for block in text_blocks:
            matched = self._labeled_value.match(block.text)
            if matched is None:
                continue
            rule = self._rules.get(matched.group("label").strip())
            if rule is None:
                continue
            field_code, _, _ = rule
            if field_code in extracted_codes:
                continue
            value = matched.group("value").strip()
            validation_passed, validation_message = self.validate(field_code, value)
            review_required = (
                block.confidence < self._low_confidence_threshold or not validation_passed
            )
            fields.append(
                FieldResult(
                    fieldCode=field_code,
                    value=value,
                    confidence=block.confidence,
                    bbox=block.bbox,
                    pageNo=block.page_no,
                    validationStatus="PASSED" if validation_passed else "FAILED",
                    reviewRequired=review_required,
                    validationMessage=validation_message,
                )
            )
            extracted_codes.add(field_code)
        return fields

    def validate(self, field_code: str, value: str) -> tuple[bool, str | None]:
        validator, failure_message = self._validators[field_code]
        if validator(value):
            return True, None
        return False, failure_message

    @staticmethod
    def _is_name(value: str) -> bool:
        return bool(re.fullmatch(r"[\u4e00-\u9fffA-Za-z· ]+", value))

    @staticmethod
    def _is_employee_id(value: str) -> bool:
        return bool(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{1,31}", value))

    @staticmethod
    def _is_id_number(value: str) -> bool:
        return bool(re.fullmatch(r"\d{17}[\dXx]", value))

    @staticmethod
    def _is_phone(value: str) -> bool:
        return bool(re.fullmatch(r"1[3-9]\d{9}", value))
