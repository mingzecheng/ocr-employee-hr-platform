package com.hrplatform.archive.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.archive.api.ArchiveDtos;
import com.hrplatform.archive.api.DataScopeDeniedException;
import com.hrplatform.archive.employee.Employee;
import com.hrplatform.archive.employee.EmployeeMapper;
import com.hrplatform.archive.ocr.OcrBinding;
import com.hrplatform.archive.ocr.OcrBindingMapper;
import com.hrplatform.archive.ocr.OcrClient;
import com.hrplatform.archive.ocr.OcrSourceData;
import com.hrplatform.archive.ocr.OcrTaskData;
import com.hrplatform.archive.storage.ObjectStorage;
import com.hrplatform.common.security.DataScope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ArchiveServiceTest {
    private final EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
    private final OcrBindingMapper bindingMapper = mock(OcrBindingMapper.class);
    private final ObjectStorage objectStorage = mock(ObjectStorage.class);
    private final OcrClient ocrClient = mock(OcrClient.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ArchiveService service = new ArchiveService(
            employeeMapper, archiveMapper, bindingMapper, objectStorage, ocrClient,
            new ObjectMapper(), redis, 10 * 1024 * 1024);

    @Test
    void repeatedOcrOnOneVersionReusesTheExistingBinding() {
        ArchiveVersion version = new ArchiveVersion();
        version.setId(21L);
        version.setFileObjectId(41L);
        FileObject file = new FileObject();
        file.setObjectKey("archive/7/object.png");
        file.setContentType("image/png");
        version.setFileObject(file);
        when(archiveMapper.findVersionByIdInScope(21L, "ALL", null, null)).thenReturn(version);

        OcrBinding existing = new OcrBinding();
        existing.setId(31L);
        existing.setVersionId(21L);
        existing.setOcrTaskId("task-1");
        existing.setStatus("SUCCEEDED");
        when(bindingMapper.findByVersionIdInScope(21L, "ALL", null, null)).thenReturn(existing);
        OcrTaskData task = new OcrTaskData();
        task.setTaskId("task-1");
        task.setStatus("SUCCEEDED");
        when(ocrClient.getTask("task-1")).thenReturn(task);

        ArchiveDtos.OcrResultData result = service.runOcr(21L, "employee_profile", 9L,
                DataScope.all(0L));

        assertThat(result.bindingId()).isEqualTo(31L);
        verify(ocrClient, never()).createTask(any(), any(), any(), any());
        verify(bindingMapper, never()).insert(any());
    }

    @Test
    void failedOcrBindingCanBeRetriedForTheSameVersion() {
        ArchiveVersion version = version(21L, "archive/7/object.png", "profile.png", "image/png");
        when(archiveMapper.findVersionByIdInScope(21L, "ALL", null, null)).thenReturn(version);

        OcrBinding failed = new OcrBinding();
        failed.setId(31L);
        failed.setVersionId(21L);
        failed.setOcrTaskId("failed-task");
        failed.setStatus("FAILED");
        when(bindingMapper.findByVersionIdInScope(21L, "ALL", null, null)).thenReturn(failed);

        OcrTaskData retriedTask = new OcrTaskData();
        retriedTask.setTaskId("retried-task");
        retriedTask.setStatus("SUCCEEDED");
        when(ocrClient.createTask(any(OcrSourceData.class), eq("employee_profile")))
                .thenReturn(retriedTask);

        ArchiveDtos.OcrResultData result = service.runOcr(21L, "employee_profile", 9L,
                DataScope.all(0L));

        assertThat(result.taskId()).isEqualTo("retried-task");
        verify(bindingMapper).deleteByVersionId(21L);
        verify(bindingMapper).insert(any(OcrBinding.class));
        verify(objectStorage, never()).get(any());
    }

    @Test
    void listsDocumentsOnlyThroughTheCurrentScope() {
        DataScope scope = DataScope.department(12L, 9L);
        ArchiveDtos.DocumentData document = new ArchiveDtos.DocumentData(
                21L, 7L, "EMPLOYEE_PROFILE", "profile.png", "ACTIVE", 31L, 1,
                "profile.png", "image/png", 128L, LocalDateTime.of(2026, 9, 18, 14, 0));
        when(employeeMapper.findByIdInScope(7L, "DEPARTMENT", null, 12L)).thenReturn(new Employee());
        when(archiveMapper.listDocumentsByEmployeeInScope(7L, "DEPARTMENT", null, 12L))
                .thenReturn(List.of(document));

        assertThat(service.listDocuments(7L, scope)).containsExactly(document);
        verify(archiveMapper).listDocumentsByEmployeeInScope(7L, "DEPARTMENT", null, 12L);
    }

    @Test
    void listsVersionsOnlyThroughTheCurrentScope() {
        DataScope scope = DataScope.employee(7L, 9L);
        ArchiveDtos.VersionData version = new ArchiveDtos.VersionData(
                31L, 21L, 1, "DRAFT", "profile.png", "image/png", 128L,
                "a".repeat(64), 9L, LocalDateTime.of(2026, 9, 18, 14, 0));
        when(archiveMapper.listVersionsByDocumentInScope(21L, "EMPLOYEE", 7L, null))
                .thenReturn(List.of(version));

        assertThat(service.listVersions(21L, scope)).containsExactly(version);
        verify(archiveMapper).listVersionsByDocumentInScope(21L, "EMPLOYEE", 7L, null);
    }

    @Test
    void downloadsAnInScopeVersionFromObjectStorage() {
        ArchiveVersion version = version(31L, "employee/7/profile.png", "profile.png", "image/png");
        when(archiveMapper.findVersionByIdInScope(31L, "ALL", null, null)).thenReturn(version);
        when(objectStorage.get("employee/7/profile.png")).thenReturn(
                new ObjectStorage.StoredObject("employee/7/profile.png", "image/png", 3,
                        "abc", new byte[]{1, 2, 3}));

        ArchiveDtos.DownloadData result = service.downloadVersion(31L, DataScope.all(0L));

        assertThat(result.fileName()).isEqualTo("profile.png");
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.content()).containsExactly(1, 2, 3);
        verify(objectStorage).get("employee/7/profile.png");
    }

    @Test
    void refusesOutOfScopeVersionBeforeObjectStorage() {
        when(archiveMapper.findVersionByIdInScope(31L, "EMPLOYEE", 7L, null)).thenReturn(null);

        assertThatThrownBy(() -> service.downloadVersion(31L, DataScope.employee(7L, 9L)))
                .isInstanceOf(DataScopeDeniedException.class);
        verifyNoInteractions(objectStorage);
    }

    private ArchiveVersion version(long id, String objectKey, String originalName, String contentType) {
        ArchiveVersion version = new ArchiveVersion();
        version.setId(id);
        FileObject file = new FileObject();
        file.setObjectKey(objectKey);
        file.setOriginalName(originalName);
        file.setContentType(contentType);
        version.setFileObject(file);
        return version;
    }
}
