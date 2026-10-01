package com.hrplatform.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
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
}
