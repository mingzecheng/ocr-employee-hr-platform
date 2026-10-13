package com.hrplatform.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OperationLogServiceTest {
    @Mock
    private OperationLogMapper mapper;

    @Test
    void recordPersistsStructuredAuditEntry() {
        OperationLogService service = new OperationLogService(mapper);
        service.record(9L, "DOWNLOAD", "ARCHIVE_VERSION", 3L, "SUCCESS");
        verify(mapper).insert(any(OperationLogRecord.class));
    }

    @Test
    void recordPersistsNonSensitiveBeforeAndAfterSummaries() {
        OperationLogService service = new OperationLogService(mapper);
        service.record(9L, "APPROVE", "HR_REQUEST", 3L, "SUCCESS",
                "type=TRANSFER;status=PENDING_HR_APPROVAL;node=HR;version=2;handover=NONE",
                "type=TRANSFER;status=APPROVED;node=NONE;version=3;handover=NONE");

        var record = forClass(OperationLogRecord.class);
        verify(mapper).insert(record.capture());
        org.assertj.core.api.Assertions.assertThat(record.getValue().beforeSummary())
                .contains("status=PENDING_HR_APPROVAL");
        org.assertj.core.api.Assertions.assertThat(record.getValue().afterSummary())
                .contains("status=APPROVED");
    }
}
