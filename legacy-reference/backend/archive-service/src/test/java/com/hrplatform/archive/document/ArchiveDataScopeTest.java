package com.hrplatform.archive.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.archive.api.DataScopeDeniedException;
import com.hrplatform.archive.employee.EmployeeMapper;
import com.hrplatform.archive.ocr.OcrBindingMapper;
import com.hrplatform.archive.ocr.OcrClient;
import com.hrplatform.archive.storage.ObjectStorage;
import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchiveDataScopeTest {

    @Test
    void archiveResourceFromAnotherDepartmentIsDeniedBeforeFileAccess() {
        EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
        ArchiveService service = new ArchiveService(
                employeeMapper, mock(ArchiveMapper.class), mock(OcrBindingMapper.class),
                mock(ObjectStorage.class), mock(OcrClient.class), new ObjectMapper(),
                mock(StringRedisTemplate.class), 10 * 1024 * 1024);
        when(employeeMapper.findByIdInScope(7L, "DEPARTMENT", null, 12L)).thenReturn(null);

        assertThatThrownBy(() -> service.uploadDocument(7L, "profile", null,
                null, 9L, DataScope.department(12L, 2L)))
                .isInstanceOf(DataScopeDeniedException.class);

        verify(employeeMapper).findByIdInScope(7L, "DEPARTMENT", null, 12L);
    }
}
