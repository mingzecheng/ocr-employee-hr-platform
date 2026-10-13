package com.hrplatform.ocr;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessFieldMapperTest {
    @Test
    void transferFormMapsLabelsAndMarksInvalidDateForReview() {
        List<OcrTextBlock> blocks = List.of(
                new OcrTextBlock("姓名：张三", 0.98, List.of(1, 2, 3, 4), 1),
                new OcrTextBlock("员工编号：EMP-001", 0.97, List.of(5, 6, 7, 8), 1),
                new OcrTextBlock("原部门：研发部", 0.96, List.of(9, 10, 11, 12), 1),
                new OcrTextBlock("新部门：人事部", 0.96, List.of(13, 14, 15, 16), 1),
                new OcrTextBlock("生效日期：2026/10/01", 0.92, List.of(17, 18, 19, 20), 1)
        );

        List<BusinessField> fields = new BusinessFieldMapper(0.85).map("transfer_form", blocks);

        assertThat(fields).extracting(BusinessField::fieldCode)
                .containsExactly("employee_name", "employee_id", "from_department", "to_department", "effective_date");
        BusinessField date = fields.get(4);
        assertThat(date.validationStatus()).isEqualTo("FAILED");
        assertThat(date.reviewRequired()).isTrue();
    }

    @Test
    void lowConfidenceEmployeeProfileFieldRequiresReview() {
        List<BusinessField> fields = new BusinessFieldMapper(0.85).map("employee_profile", List.of(
                new OcrTextBlock("员工编号：EMP-001", 0.70, List.of(1, 2, 3, 4), 1)
        ));

        assertThat(fields.get(0).reviewRequired()).isTrue();
        assertThat(fields.get(0).validationStatus()).isEqualTo("PASSED");
    }

    @Test
    void historicalEqualsSeparatedOcrLabelsStillMapToBusinessFields() {
        List<BusinessField> fields = new BusinessFieldMapper(0.85).map("employee_profile", List.of(
                new OcrTextBlock("employee_name=张三", 0.97, List.of(1, 2, 3, 4), 1),
                new OcrTextBlock("employee_id=EMP-001", 0.96, List.of(5, 6, 7, 8), 1)
        ));

        assertThat(fields).extracting(BusinessField::fieldCode)
                .containsExactly("employee_name", "employee_id");
        assertThat(fields).extracting(BusinessField::value)
                .containsExactly("张三", "EMP-001");
    }

    @Test
    void archivedHrFormDocumentTypesMapTheirSpecificFields() {
        List<OcrTextBlock> blocks = List.of(
                new OcrTextBlock("入职日期：2026-10-01", 0.96, List.of(1, 2, 3, 4), 1),
                new OcrTextBlock("原部门：研发部", 0.96, List.of(5, 6, 7, 8), 1),
                new OcrTextBlock("离职日期：2026-10-01", 0.96, List.of(9, 10, 11, 12), 1)
        );

        assertThat(new BusinessFieldMapper(0.85).map("onboarding", blocks))
                .extracting(BusinessField::fieldCode).contains("hire_date");
        assertThat(new BusinessFieldMapper(0.85).map("transfer", blocks))
                .extracting(BusinessField::fieldCode).contains("from_department");
        assertThat(new BusinessFieldMapper(0.85).map("offboarding", blocks))
                .extracting(BusinessField::fieldCode).contains("leave_date");
    }
}
