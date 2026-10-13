package com.hrplatform.ocr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcrServiceTest {
    @Mock
    private OcrClient client;
    @Mock
    private OcrBindingMapper mapper;

    @Test
    void repeatedTriggerReturnsExistingBindingWithoutCallingOcr() {
        OcrBinding existing = new OcrBinding(1L, 5L, "task-1", "SUCCEEDED", "paddle", 20L, false, null, LocalDateTime.now());
        when(mapper.findByVersionId(5L)).thenReturn(existing);

        OcrBinding result = new OcrService(client, mapper, new BusinessFieldMapper(0.85))
                .trigger(new OcrTrigger(5L, "archive/5/profile.png", "profile.png", "image/png", 100L, "hash", "employee_profile", 9L));

        assertThat(result).isEqualTo(existing);
        verifyNoInteractions(client);
    }

    @Test
    void successfulTriggerPersistsTaskAndReviewFlag() {
        when(mapper.findByVersionId(5L)).thenReturn(null,
                new OcrBinding(2L, 5L, "task-2", "SUCCEEDED", "paddle-3", 42L, true, null, LocalDateTime.now()));
        when(client.create(any(OcrCreateRequest.class))).thenReturn(new OcrTaskData(
                "task-2", "SUCCEEDED", "paddle-3", 42L,
                List.of(new OcrTextBlock("员工编号：EMP-002", 0.70, List.of(1, 2, 3, 4), 1)),
                null, null
        ));
        when(mapper.insert(any(OcrBinding.class))).thenReturn(1);

        OcrBinding result = new OcrService(client, mapper, new BusinessFieldMapper(0.85))
                .trigger(new OcrTrigger(5L, "archive/5/profile.png", "profile.png", "image/png", 100L, "hash", "employee_profile", 9L));

        assertThat(result.taskId()).isEqualTo("task-2");
        assertThat(result.reviewRequired()).isTrue();
    }

    @Test
    void unavailableTaskServiceDoesNotReportStoredSuccessAsPassed() {
        OcrBinding binding = new OcrBinding(3L, 5L, "task-3", "SUCCEEDED", "paddle-3", 42L,
                false, null, LocalDateTime.now(), "employee_profile");
        when(mapper.findByVersionIdWithScope(5L, null, null, "ALL")).thenReturn(binding);
        when(client.getTask("task-3")).thenThrow(new OcrClientException("OCR service unavailable"));

        OcrResult result = new OcrService(client, mapper, new BusinessFieldMapper(0.85))
                .getResult(5L, new com.hrplatform.common.security.DataScope(
                        com.hrplatform.common.security.DataScope.Type.ALL, 9L, null, null));

        assertThat(result.status()).isEqualTo("UNAVAILABLE");
        assertThat(result.errorCode()).isEqualTo("OCR_RESULT_UNAVAILABLE");
        assertThat(result.reviewRequired()).isTrue();
    }
}
