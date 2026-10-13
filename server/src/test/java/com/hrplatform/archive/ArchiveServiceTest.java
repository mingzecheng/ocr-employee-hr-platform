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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

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
    @Mock
    private ArchiveReadAuthorization readAuthorization;

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
        when(mapper.findDocumentForUpdate(7L, "employee_profile")).thenReturn(null,
                new ArchiveDocument(9L, 7L, "employee_profile", "员工档案"));
        when(mapper.insertDocument(any(ArchiveDocument.class))).thenReturn(1);
        when(mapper.insertFile(any(FileObject.class))).thenReturn(1);
        when(mapper.findFileByObjectKey(any())).thenReturn(
                new FileObject(30L, "archive/7/2/profile.png", "profile.png", "image/png", 68L, "hash"));
        when(mapper.insertVersion(any(ArchiveVersion.class))).thenReturn(1);
        when(mapper.findVersionByDocumentAndNumber(9L, 2)).thenReturn(new ArchiveVersion(
                20L, 9L, 2, 30L, "profile.png", "image/png", 68L, "hash", "UPLOADED", true,
                LocalDateTime.now(), 1L, "initial"));

        ArchiveVersion result = newService().createVersion(7L, "employee_profile", "profile.png", "image/png",
                validPng(), new DataScope(DataScope.Type.ALL, 1L, null, null), 1L);

        assertThat(result.versionNo()).isEqualTo(2);
        assertThat(result.isCurrent()).isTrue();
    }

    @Test
    void failedMetadataWriteDeletesUploadedObject() {
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name())).thenReturn(
                new Employee(7L, "EMP-007", "测试员工", null, 1L, 2L, EmployeeStatus.ACTIVE, null, null));
        when(mapper.findDocumentForUpdate(7L, "employee_profile"))
                .thenReturn(new ArchiveDocument(9L, 7L, "employee_profile", "员工档案"));
        when(mapper.nextVersionNumber(9L)).thenReturn(2);
        when(mapper.insertFile(any(FileObject.class))).thenReturn(0);

        assertThatThrownBy(() -> newService().createVersion(7L, "employee_profile", "profile.png", "image/png",
                        validPng(), new DataScope(DataScope.Type.ALL, 1L, null, null), 1L))
                .isInstanceOf(IllegalStateException.class);

        verify(storage).delete(any(String.class));
    }

    @Test
    void listDocumentsAppliesEmployeeScopeAndPagination() {
        when(employeeMapper.findByIdWithScope(7L, 7L, 10L, "EMPLOYEE"))
                .thenReturn(new Employee(7L, "DEMO-42", "演示员工", null, 10L, 8L,
                        EmployeeStatus.ACTIVE, null, null));
        when(mapper.listDocuments(7L, 10L, "EMPLOYEE", 0, 10))
                .thenReturn(List.of(new ArchiveDocument(3L, 7L, "employee_profile", "员工档案")));
        when(mapper.countDocuments(7L, 10L, "EMPLOYEE")).thenReturn(1L);

        ArchiveDocumentPage page = newService().listDocuments(7L, 1, 10,
                new DataScope(DataScope.Type.EMPLOYEE, 1L, 7L, 10L));

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).containsExactly(new ArchiveDocument(3L, 7L, "employee_profile", "员工档案"));
    }

    @Test
    void successfulDownloadWritesOperationLog() {
        DataScope scope = new DataScope(DataScope.Type.DEPARTMENT, 45L, null, 10L);
        when(mapper.findVersionByIdWithScope(20L, null, 10L, DataScope.Type.DEPARTMENT.name())).thenReturn(
                new ArchiveVersion(20L, 9L, 1, 30L, "profile.png", "image/png", 68L, "hash",
                        "UPLOADED", true, LocalDateTime.now(), 12L, "initial"));
        when(readAuthorization.allowsRead(20L, 12L, scope)).thenReturn(true);
        when(mapper.findObjectKeyByVersionId(20L)).thenReturn("archive/7/profile.png");
        when(storage.get("archive/7/profile.png")).thenReturn(new byte[]{1, 2, 3});

        ArchiveDownload download = newService().download(20L, scope);

        assertThat(download.content()).containsExactly(1, 2, 3);
        verify(operationLogService).record(45L, "DOWNLOAD", "ARCHIVE_VERSION", 20L, "SUCCESS");
    }

    @Test
    void departmentManagerCannotDownloadWithoutAnActiveArchiveGrant() {
        when(mapper.findVersionByIdWithScope(20L, null, 10L, DataScope.Type.DEPARTMENT.name())).thenReturn(
                new ArchiveVersion(20L, 9L, 1, 30L, "profile.png", "image/png", 68L, "hash",
                        "UPLOADED", true, LocalDateTime.now(), 12L, "initial"));

        assertThatThrownBy(() -> newService().download(20L,
                        new DataScope(DataScope.Type.DEPARTMENT, 45L, null, 10L)))
                .isInstanceOf(ArchiveReadDeniedException.class);

        verify(storage, never()).get("archive/7/profile.png");
    }

    private ArchiveService newService() {
        return new ArchiveService(mapper, employeeMapper, storage, operationLogService, readAuthorization);
    }

    private byte[] validPng() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    }
}
