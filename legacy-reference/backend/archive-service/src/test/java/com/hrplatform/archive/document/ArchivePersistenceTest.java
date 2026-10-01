package com.hrplatform.archive.document;

import com.hrplatform.archive.api.ArchiveDtos;
import com.hrplatform.archive.api.DataScopeDeniedException;
import com.hrplatform.archive.employee.Employee;
import com.hrplatform.archive.employee.EmployeeMapper;
import com.hrplatform.archive.ocr.OcrClient;
import com.hrplatform.archive.ocr.OcrSourceData;
import com.hrplatform.archive.ocr.OcrTaskData;
import com.hrplatform.archive.storage.ObjectStorage;
import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ArchivePersistenceTest {
    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private ArchiveService archiveService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ObjectStorage objectStorage;

    @MockBean
    private OcrClient ocrClient;

    @Test
    void uploadPersistsFileDocumentAndImmutableVersionRows() throws IOException {
        Employee employee = new Employee();
        employee.setEmployeeNo("IT-" + UUID.randomUUID());
        employee.setName("集成测试员工");
        employee.setStatus("ACTIVE");
        employeeMapper.insert(employee);
        byte[] content = png();
        when(objectStorage.put(any(), any(), any())).thenAnswer(invocation ->
                new ObjectStorage.StoredObject(
                        invocation.getArgument(0), invocation.getArgument(1), content.length,
                        "a".repeat(64), content));

        ArchiveDtos.DocumentUploadData result = archiveService.uploadDocument(
                employee.getId(), "employee_profile", null,
                new MockMultipartFile("file", "profile.png", "image/png", content), 9L,
                DataScope.all(0L));

        assertThat(result.versionId()).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_object WHERE id = ? AND sha256 = ?",
                Integer.class, jdbcTemplate.queryForObject(
                        "SELECT file_object_id FROM archive_version WHERE id = ?",
                        Long.class, result.versionId()), "a".repeat(64))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM archive_document WHERE id = ?",
                Integer.class, result.documentId())).isEqualTo(1);
    }

    @Test
    void queriesDocumentVersionsAndDownloadsMetadataThroughTheArchiveSchema() throws IOException {
        Employee employee = createEmployee(9201L);
        byte[] content = png();
        when(objectStorage.put(any(), any(), any())).thenAnswer(invocation ->
                new ObjectStorage.StoredObject(invocation.getArgument(0), invocation.getArgument(1), content.length,
                        "b".repeat(64), content));

        ArchiveDtos.DocumentUploadData uploaded = archiveService.uploadDocument(
                employee.getId(), "employee_profile", "入职材料",
                new MockMultipartFile("file", "original profile.png", "image/png", content), 9L,
                DataScope.all(0L));

        List<ArchiveDtos.DocumentData> documents = archiveService.listDocuments(
                employee.getId(), DataScope.department(9201L, 12L));
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).id()).isEqualTo(uploaded.documentId());
        assertThat(documents.get(0).latestVersionId()).isEqualTo(uploaded.versionId());
        assertThat(documents.get(0).latestFileName()).isEqualTo("original profile.png");
        assertThat(documents.get(0).latestContentType()).isEqualTo("image/png");
        assertThat(documents.get(0).latestSize()).isEqualTo((long) content.length);

        List<ArchiveDtos.VersionData> versions = archiveService.listVersions(
                uploaded.documentId(), DataScope.department(9201L, 12L));
        assertThat(versions).singleElement().satisfies(version -> {
            assertThat(version.id()).isEqualTo(uploaded.versionId());
            assertThat(version.versionNo()).isEqualTo(1);
            assertThat(version.sha256()).isEqualTo("b".repeat(64));
        });

        when(objectStorage.get(uploaded.objectKey())).thenReturn(
                new ObjectStorage.StoredObject(uploaded.objectKey(), "image/png", content.length,
                        "b".repeat(64), content));
        ArchiveDtos.DownloadData download = archiveService.downloadVersion(
                uploaded.versionId(), DataScope.department(9201L, 12L));
        assertThat(download.fileName()).isEqualTo("original profile.png");
        assertThat(download.contentType()).isEqualTo("image/png");
        assertThat(download.content()).containsExactly(content);
        verify(objectStorage).get(uploaded.objectKey());
    }

    @Test
    void departmentScopeDoesNotExposeAnotherEmployeesDocuments() throws IOException {
        Employee employee = createEmployee(9202L);
        byte[] content = png();
        String objectKey = "employee/other/" + UUID.randomUUID() + "/profile.png";
        when(objectStorage.put(any(), any(), any())).thenReturn(
                new ObjectStorage.StoredObject(objectKey, "image/png", content.length,
                        "c".repeat(64), content));

        ArchiveDtos.DocumentUploadData uploaded = archiveService.uploadDocument(
                employee.getId(), "employee_profile", null,
                new MockMultipartFile("file", "profile.png", "image/png", content), 9L,
                DataScope.all(0L));

        assertThatThrownBy(() -> archiveService.listDocuments(
                employee.getId(), DataScope.department(9203L, 13L)))
                .isInstanceOf(DataScopeDeniedException.class);
        assertThatThrownBy(() -> archiveService.listVersions(
                uploaded.documentId(), DataScope.department(9203L, 13L)))
                .isInstanceOf(DataScopeDeniedException.class);
    }

    @Test
    void archivedDocumentCannotBeDownloadedOrUsedForDetectionPreview() throws IOException {
        Employee employee = createEmployee(9204L);
        byte[] content = png();
        String objectKey = "employee/archived/" + UUID.randomUUID() + "/profile.png";
        when(objectStorage.put(any(), any(), any())).thenReturn(
                new ObjectStorage.StoredObject(objectKey, "image/png",
                        content.length, "d".repeat(64), content));
        when(objectStorage.get(objectKey)).thenReturn(
                new ObjectStorage.StoredObject(objectKey, "image/png",
                        content.length, "d".repeat(64), content));

        ArchiveDtos.DocumentUploadData uploaded = archiveService.uploadDocument(
                employee.getId(), "employee_profile", null,
                new MockMultipartFile("file", "profile.png", "image/png", content), 9L,
                DataScope.all(0L));
        OcrTaskData ocrTask = new OcrTaskData();
        ocrTask.setTaskId("archive-status-task-" + UUID.randomUUID());
        ocrTask.setStatus("SUCCEEDED");
        when(ocrClient.createTask(any(OcrSourceData.class), any())).thenReturn(ocrTask);
        ArchiveDtos.OcrResultData ocrResult = archiveService.runOcr(
                uploaded.versionId(), "employee_profile", 9L, DataScope.all(0L));

        jdbcTemplate.update("UPDATE archive_document SET status = 'ARCHIVED' WHERE id = ?",
                uploaded.documentId());

        assertThatThrownBy(() -> archiveService.downloadVersion(
                uploaded.versionId(), DataScope.all(0L)))
                .isInstanceOf(DataScopeDeniedException.class);
        assertThatThrownBy(() -> archiveService.detectionPreview(
                ocrResult.bindingId(), DataScope.all(0L)))
                .isInstanceOf(DataScopeDeniedException.class);
    }

    private Employee createEmployee(long departmentId) {
        Employee employee = new Employee();
        employee.setEmployeeNo("RESOURCE-" + UUID.randomUUID());
        employee.setName("档案资源测试员工");
        employee.setDepartmentId(departmentId);
        employee.setStatus("ACTIVE");
        employeeMapper.insert(employee);
        return employee;
    }

    private byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
