from app.extraction import EmployeeProfileFieldExtractor
from app.models import TextBlock


def test_employee_profile_extracts_labeled_fields_with_validation_and_review_flags():
    extractor = EmployeeProfileFieldExtractor(low_confidence_threshold=0.85)

    fields = extractor.extract(
        [
            TextBlock(
                pageNo=1,
                text="员工姓名：张三",
                confidence=0.98,
                bbox=[10, 20, 260, 60],
            ),
            TextBlock(
                pageNo=1,
                text="员工编号：EMP-001",
                confidence=0.96,
                bbox=[10, 80, 300, 120],
            ),
            TextBlock(
                pageNo=1,
                text="身份证号：11010519491231002X",
                confidence=0.65,
                bbox=[10, 140, 520, 180],
            ),
            TextBlock(
                pageNo=1,
                text="联系电话：123",
                confidence=0.99,
                bbox=[10, 200, 260, 240],
            ),
        ]
    )

    assert [field.field_code for field in fields] == [
        "employee_name",
        "employee_id",
        "id_number",
        "phone",
    ]
    assert [field.value for field in fields] == [
        "张三",
        "EMP-001",
        "11010519491231002X",
        "123",
    ]
    assert [field.validation_status for field in fields] == [
        "PASSED",
        "PASSED",
        "PASSED",
        "FAILED",
    ]
    assert [field.review_required for field in fields] == [False, False, True, True]
