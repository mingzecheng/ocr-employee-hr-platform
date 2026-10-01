package com.hrplatform.ocr;

import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcrRevisionTest {
    @Mock
    private OcrClient client;
    @Mock
    private OcrBindingMapper mapper;

    @Test
    void fieldRevisionKeepsPreviousValueAndValidatesCorrectedValue() {
        OcrBinding binding = new OcrBinding(7L, 5L, "task-7", "SUCCEEDED", "paddle-3", 12L,
                false, null, LocalDateTime.now(), "employee_profile");
        when(mapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name())).thenReturn(binding);
        when(client.getTask("task-7")).thenReturn(new OcrTaskData(
                "task-7", "SUCCEEDED", "paddle-3", 12L,
                List.of(new OcrTextBlock("员工编号：EMP-001", 0.91, List.of(1, 2, 3, 4), 1)),
                List.of(new OcrRawField("employee_id", "EMP-001", 0.91)), null));
        when(client.correctField("task-7", "employee_id", "EMP-002", "9", "录入修正"))
                .thenReturn(new OcrTaskData(
                        "task-7", "SUCCEEDED", "paddle-3", 12L,
                        List.of(new OcrTextBlock("员工编号：EMP-001", 0.91, List.of(1, 2, 3, 4), 1)),
                        List.of(new OcrRawField("employee_id", "EMP-002", 1.0)), null));
        when(mapper.insertRevision(any(OcrFieldRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OcrResult result = new OcrService(client, mapper, new BusinessFieldMapper(0.85))
                .reviseField(7L, "employee_id", "EMP-002", "录入修正", 9L,
                        new DataScope(DataScope.Type.ALL, 9L, null, null));

        assertThat(result.fields()).singleElement().satisfies(field -> {
            assertThat(field.fieldCode()).isEqualTo("employee_id");
            assertThat(field.value()).isEqualTo("EMP-002");
            assertThat(field.validationStatus()).isEqualTo("PASSED");
        });
        verify(mapper).insertRevision(any(OcrFieldRevision.class));
    }
}
