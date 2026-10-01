package com.hrplatform.archive;

import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.employee.EmployeeMapper;
import com.hrplatform.employee.DataScopeDeniedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveDataScopeTest {
    @Mock
    private ArchiveMapper mapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private ObjectStorage storage;
    @Mock
    private OperationLogService operationLogService;

    @Test
    void downloadRejectsVersionOutsideDepartmentScope() {
        when(mapper.findVersionByIdWithScope(9L, null, 20L, DataScope.Type.DEPARTMENT.name())).thenReturn(null);

        assertThatThrownBy(() -> new ArchiveService(mapper, employeeMapper, storage, operationLogService)
                .download(9L, new DataScope(DataScope.Type.DEPARTMENT, 3L, null, 20L)))
                .isInstanceOf(InvalidArchiveFileException.class);
    }

    @Test
    void documentListRejectsEmployeeOutsideDepartmentScope() {
        when(employeeMapper.findByIdWithScope(7L, null, 20L, DataScope.Type.DEPARTMENT.name())).thenReturn(null);

        assertThatThrownBy(() -> new ArchiveService(mapper, employeeMapper, storage, operationLogService)
                .listDocuments(7L, 1, 20, new DataScope(DataScope.Type.DEPARTMENT, 3L, null, 20L)))
                .isInstanceOf(DataScopeDeniedException.class);
    }
}
