package com.hrplatform.archive;

import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.employee.Employee;
import com.hrplatform.employee.EmployeeMapper;
import com.hrplatform.employee.EmployeeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveServiceTest {
    @Mock
    private ArchiveMapper mapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private ObjectStorage storage;
    @Mock
    private OperationLogService operationLogService;

    @Test
    void unsupportedFileTypeIsRejectedBeforeStorageWrite() {
        ArchiveService service = newService();

        assertThatThrownBy(() -> service.createVersion(7L, "employee_profile", "profile.pdf", "application/pdf",
                        "not-image".getBytes(), new DataScope(DataScope.Type.ALL, 1L, null, null), 1L))
                .isInstanceOf(InvalidArchiveFileException.class);
    }

    @Test
    void newVersionDoesNotOverwritePreviousVersion() {
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name())).thenReturn(
                new Employee(7L, "EMP-007", "测试员工", null, 1L, 2L, EmployeeStatus.ACTIVE, null, null));
        when(mapper.nextVersionNumber(9L)).thenReturn(2);
        when(mapper.insertDocument(any(ArchiveDocument.class))).thenReturn(new ArchiveDocument(9L, 7L, "employee_profile", "员工档案"));
        when(mapper.insertFile(any(FileObject.class))).thenReturn(new FileObject(30L, "archive/7/2/profile.png", "profile.png", "image/png", 68L, "hash"));
        when(mapper.insertVersion(any(ArchiveVersion.class))).thenReturn(new ArchiveVersion(
                20L, 9L, 2, 30L, "profile.png", "image/png", 68L, "hash", "UPLOADED", true,
                LocalDateTime.now(), 1L, "initial"));

        ArchiveVersion result = newService().createVersion(7L, "employee_profile", "profile.png", "image/png",
                validPng(), new DataScope(DataScope.Type.ALL, 1L, null, null), 1L);

        assertThat(result.versionNo()).isEqualTo(2);
        assertThat(result.isCurrent()).isTrue();
    }

    private ArchiveService newService() {
        return new ArchiveService(mapper, employeeMapper, storage, operationLogService);
    }

    private byte[] validPng() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    }
}
