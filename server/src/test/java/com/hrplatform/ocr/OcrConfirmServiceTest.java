package com.hrplatform.ocr;

import com.hrplatform.archive.ArchiveCatalog;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.employee.Employee;
import com.hrplatform.employee.EmployeeMapper;
import com.hrplatform.employee.EmployeeProfileMutation;
import com.hrplatform.employee.EmployeeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcrConfirmServiceTest {

    @Mock
    private OcrBindingMapper bindingMapper;
    @Mock
    private OcrClient ocrClient;
    @Mock
    private ArchiveCatalog archiveCatalog;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private OperationLogService operationLogService;

    private static final DataScope ALL = new DataScope(DataScope.Type.ALL, null, null, null);

    @BeforeEach
    void stubConfirmUpdate() {
        // Mockito int 默认返回 0；lenient 因为部分测试用例不应走到这里
        org.mockito.Mockito.lenient().when(bindingMapper.updateConfirmStatus(anyLong(),
                        org.mockito.ArgumentMatchers.anyString(), anyLong(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
    }

    private OcrConfirmService service() {
        return new OcrConfirmService(bindingMapper, ocrClient, new BusinessFieldMapper(0.85),
                archiveCatalog, employeeMapper, operationLogService);
    }

    private OcrBinding binding(Long id, String status, String confirmStatus) {
        return new OcrBinding(id, 50L, "task-" + id, status, "paddle", 10L, false, null,
                LocalDateTime.now(), "employee_profile", null, null, null, null, null, null,
                null, LocalDateTime.now(), confirmStatus, null, null, null);
    }

    private Employee employee(Long id, String name, String phone) {
        return new Employee(id, "EMP-001", name, phone, 10L, 20L, EmployeeStatus.ACTIVE, null, null);
    }

    private OcrTaskData taskWith(String fieldCode, String value, double confidence) {
        return new OcrTaskData("task-1", "SUCCEEDED", "paddle", 10L,
                List.of(),
                List.of(new OcrRawField(fieldCode, value, confidence)),
                null);
    }

    @Test
    void confirmHappyPathBackfillsNameAndPhoneAndAudits() {
        when(bindingMapper.findByIdWithScope(7L, null, null, "ALL"))
                .thenReturn(binding(7L, "SUCCEEDED", "PENDING"));
        when(ocrClient.getTask("task-7")).thenReturn(new OcrTaskData("task-7", "SUCCEEDED", "paddle", 10L,
                List.of(),
                List.of(
                        new OcrRawField("employee_name", "李四", 0.95),
                        new OcrRawField("phone", "13912345678", 0.92)),
                null));
        when(bindingMapper.listRevisions(7L)).thenReturn(List.of());
        when(archiveCatalog.findOcrSourceByVersionIdWithScope(50L, null, null, "ALL"))
                .thenReturn(new ArchiveOcrSource(50L, "archive/50/a.png", "a.png", "image/png",
                        1L, "hash", "employee_profile", 99L, 10L, 1L));
        when(employeeMapper.lockById(99L)).thenReturn(employee(99L, "张三", "13800138000"));
        when(employeeMapper.updateProfile(any(EmployeeProfileMutation.class))).thenReturn(1);

        OcrConfirmResult result = service().confirm(7L, 3L, "已核对", ALL);

        assertThat(result.confirmStatus()).isEqualTo("CONFIRMED");
        assertThat(result.employeeUpdated()).isTrue();
        ArgumentCaptor<EmployeeProfileMutation> mutation = ArgumentCaptor.forClass(EmployeeProfileMutation.class);
        verify(employeeMapper).updateProfile(mutation.capture());
        assertThat(mutation.getValue().id()).isEqualTo(99L);
        assertThat(mutation.getValue().name()).isEqualTo("李四");
        assertThat(mutation.getValue().phone()).isEqualTo("13912345678");
        verify(bindingMapper).updateConfirmStatus(eq(7L), eq("CONFIRMED"), eq(3L),
                any(LocalDateTime.class), eq("已核对"));
        verify(operationLogService).record(eq(3L), eq("OCR_CONFIRM"), eq("OCR_BINDING"), eq(7L),
                eq("SUCCESS"), any(), any());
    }

    @Test
    void confirmIdempotentWhenAlreadyConfirmed() {
        when(bindingMapper.findByIdWithScope(7L, null, null, "ALL"))
                .thenReturn(binding(7L, "SUCCEEDED", "CONFIRMED"));

        OcrConfirmResult result = service().confirm(7L, 3L, "重复", ALL);

        assertThat(result.confirmStatus()).isEqualTo("CONFIRMED");
        assertThat(result.employeeUpdated()).isFalse();
        verify(ocrClient, never()).getTask(any());
        verify(employeeMapper, never()).updateProfile(any(EmployeeProfileMutation.class));
        verify(bindingMapper, never()).updateConfirmStatus(anyLong(), any(), any(), any(), any());
        verify(operationLogService, never()).record(anyLong(), any(), any(), anyLong(), any());
    }

    @Test
    void confirmBlockedWhenBindingRejected() {
        when(bindingMapper.findByIdWithScope(7L, null, null, "ALL"))
                .thenReturn(binding(7L, "SUCCEEDED", "REJECTED"));

        assertThatThrownBy(() -> service().confirm(7L, 3L, "n", ALL))
                .isInstanceOf(IllegalStateException.class);

        verify(employeeMapper, never()).updateProfile(any(EmployeeProfileMutation.class));
        verify(bindingMapper, never()).updateConfirmStatus(anyLong(), any(), any(), any(), any());
    }

    @Test
    void confirmBlockedWhenOcrTaskFailed() {
        when(bindingMapper.findByIdWithScope(7L, null, null, "ALL"))
                .thenReturn(binding(7L, "FAILED", "PENDING"));

        assertThatThrownBy(() -> service().confirm(7L, 3L, "n", ALL))
                .isInstanceOf(IllegalStateException.class);

        verify(employeeMapper, never()).updateProfile(any(EmployeeProfileMutation.class));
    }

    @Test
    void confirmBlockedWhenLowConfidenceFieldNotRevised() {
        when(bindingMapper.findByIdWithScope(7L, null, null, "ALL"))
                .thenReturn(binding(7L, "SUCCEEDED", "PENDING"));
        // phone 置信度 0.7 < 0.8，且没有修订记录
        when(ocrClient.getTask("task-7")).thenReturn(taskWith("phone", "13912345678", 0.7));
        when(bindingMapper.listRevisions(7L)).thenReturn(List.of());

        assertThatThrownBy(() -> service().confirm(7L, 3L, "n", ALL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("phone");

        verify(employeeMapper, never()).updateProfile(any(EmployeeProfileMutation.class));
        verify(bindingMapper, never()).updateConfirmStatus(anyLong(), any(), any(), any(), any());
    }

    @Test
    void confirmAllowsLowConfidenceFieldAfterRevision() {
        when(bindingMapper.findByIdWithScope(7L, null, null, "ALL"))
                .thenReturn(binding(7L, "SUCCEEDED", "PENDING"));
        // phone 低置信度但已被人工修订为 13900000000（通过 correctField 返回）
        when(ocrClient.getTask("task-7")).thenReturn(
                new OcrTaskData("task-7", "SUCCEEDED", "paddle", 10L, List.of(),
                        List.of(new OcrRawField("phone", "13900000000", 1.0)), null));
        when(bindingMapper.listRevisions(7L)).thenReturn(List.of(
                new OcrFieldRevision(1L, 7L, "phone", "13912345678", "13900000000", "低置信度修正",
                        3L, LocalDateTime.now())));
        when(archiveCatalog.findOcrSourceByVersionIdWithScope(50L, null, null, "ALL"))
                .thenReturn(new ArchiveOcrSource(50L, "archive/50/a.png", "a.png", "image/png",
                        1L, "hash", "employee_profile", 99L, 10L, 1L));
        when(employeeMapper.lockById(99L)).thenReturn(employee(99L, "张三", "13800138000"));
        when(employeeMapper.updateProfile(any(EmployeeProfileMutation.class))).thenReturn(1);

        OcrConfirmResult result = service().confirm(7L, 3L, "已修订", ALL);

        assertThat(result.confirmStatus()).isEqualTo("CONFIRMED");
        verify(employeeMapper).updateProfile(any(EmployeeProfileMutation.class));
    }

    @Test
    void confirmSkipsEmployeeUpdateWhenOcrValuesIdentical() {
        when(bindingMapper.findByIdWithScope(7L, null, null, "ALL"))
                .thenReturn(binding(7L, "SUCCEEDED", "PENDING"));
        when(ocrClient.getTask("task-7")).thenReturn(
                new OcrTaskData("task-7", "SUCCEEDED", "paddle", 10L, List.of(),
                        List.of(
                                new OcrRawField("employee_name", "张三", 0.95),
                                new OcrRawField("phone", "13800138000", 0.95)),
                        null));
        when(bindingMapper.listRevisions(7L)).thenReturn(List.of());
        when(archiveCatalog.findOcrSourceByVersionIdWithScope(50L, null, null, "ALL"))
                .thenReturn(new ArchiveOcrSource(50L, "archive/50/a.png", "a.png", "image/png",
                        1L, "hash", "employee_profile", 99L, 10L, 1L));
        when(employeeMapper.lockById(99L)).thenReturn(employee(99L, "张三", "13800138000"));

        OcrConfirmResult result = service().confirm(7L, 3L, "一致", ALL);

        assertThat(result.confirmStatus()).isEqualTo("CONFIRMED");
        assertThat(result.employeeUpdated()).isFalse();
        verify(employeeMapper, never()).updateProfile(any(EmployeeProfileMutation.class));
        // 但仍要写 confirm_status 与审计
        verify(bindingMapper).updateConfirmStatus(eq(7L), eq("CONFIRMED"), eq(3L), any(), eq("一致"));
    }

    @Test
    void idNumberFieldIsNeverBackfilledToEmployee() {
        when(bindingMapper.findByIdWithScope(7L, null, null, "ALL"))
                .thenReturn(binding(7L, "SUCCEEDED", "PENDING"));
        when(ocrClient.getTask("task-7")).thenReturn(
                new OcrTaskData("task-7", "SUCCEEDED", "paddle", 10L, List.of(),
                        List.of(
                                new OcrRawField("employee_name", "李四", 0.95),
                                new OcrRawField("id_number", "110101199001011234", 0.95)),
                        null));
        when(bindingMapper.listRevisions(7L)).thenReturn(List.of());
        when(archiveCatalog.findOcrSourceByVersionIdWithScope(50L, null, null, "ALL"))
                .thenReturn(new ArchiveOcrSource(50L, "archive/50/a.png", "a.png", "image/png",
                        1L, "hash", "employee_profile", 99L, 10L, 1L));
        when(employeeMapper.lockById(99L)).thenReturn(employee(99L, "张三", "13800138000"));
        when(employeeMapper.updateProfile(any(EmployeeProfileMutation.class))).thenReturn(1);

        service().confirm(7L, 3L, "ok", ALL);

        ArgumentCaptor<EmployeeProfileMutation> mutation = ArgumentCaptor.forClass(EmployeeProfileMutation.class);
        verify(employeeMapper).updateProfile(mutation.capture());
        // 只更新姓名，电话保持原值，身份证号不回写
        assertThat(mutation.getValue().name()).isEqualTo("李四");
        assertThat(mutation.getValue().phone()).isEqualTo("13800138000");
    }

    @Test
    void rejectSetsRejectedStatusAndAudits() {
        when(bindingMapper.findByIdWithScope(7L, null, null, "ALL"))
                .thenReturn(binding(7L, "SUCCEEDED", "PENDING"));

        OcrConfirmResult result = service().reject(7L, 3L, "图片模糊", ALL);

        assertThat(result.confirmStatus()).isEqualTo("REJECTED");
        verify(bindingMapper).updateConfirmStatus(eq(7L), eq("REJECTED"), eq(3L), any(), eq("图片模糊"));
        verify(operationLogService).record(eq(3L), eq("OCR_REJECT"), eq("OCR_BINDING"), eq(7L),
                eq("SUCCESS"), any(), any());
        verify(employeeMapper, never()).updateProfile(any(EmployeeProfileMutation.class));
    }

    @Test
    void rejectIdempotentWhenAlreadyRejected() {
        when(bindingMapper.findByIdWithScope(7L, null, null, "ALL"))
                .thenReturn(binding(7L, "SUCCEEDED", "REJECTED"));

        OcrConfirmResult result = service().reject(7L, 3L, "重复", ALL);

        assertThat(result.confirmStatus()).isEqualTo("REJECTED");
        verify(bindingMapper, never()).updateConfirmStatus(anyLong(), any(), any(), any(), any());
    }

    @Test
    void rejectBlockedWhenAlreadyConfirmed() {
        when(bindingMapper.findByIdWithScope(7L, null, null, "ALL"))
                .thenReturn(binding(7L, "SUCCEEDED", "CONFIRMED"));

        assertThatThrownBy(() -> service().reject(7L, 3L, "n", ALL))
                .isInstanceOf(IllegalStateException.class);

        verify(bindingMapper, never()).updateConfirmStatus(anyLong(), any(), any(), any(), any());
    }

    @Test
    void unknownBindingThrowsNotFound() {
        when(bindingMapper.findByIdWithScope(404L, null, null, "ALL")).thenReturn(null);

        assertThatThrownBy(() -> service().confirm(404L, 3L, "n", ALL))
                .isInstanceOf(OcrBindingNotFoundException.class);
    }
}
