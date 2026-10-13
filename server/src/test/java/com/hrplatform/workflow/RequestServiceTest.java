package com.hrplatform.workflow;

import com.hrplatform.common.security.DataScope;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.archive.ArchiveCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {
    @Mock
    private RequestMapper mapper;
    @Mock
    private com.hrplatform.employee.EmployeeMapper employeeMapper;
    @Mock
    private ArchiveCatalog archiveCatalog;

    @Test
    void departmentAndHrApprovalUpdatesRequestAndEmployee() {
        HrRequest draft = new HrRequest(10L, "HR-001", "ONBOARDING", 7L, 9L, 9L, 20L, 9L, 2L,
                "{}", "DRAFT", null, 0, LocalDateTime.now(), null, null,
                null, null, null, null, java.time.LocalDate.now(), "FIXED_TERM", null, null, null, null);
        when(mapper.findByIdWithScope(10L, null, 9L, DataScope.Type.DEPARTMENT.name())).thenReturn(draft);
        when(mapper.updateState(any())).thenReturn(1);
        when(mapper.markEmployeeChangeApplied(org.mockito.ArgumentMatchers.eq(10L), any())).thenReturn(1);
        when(mapper.findByIdWithScope(10L, null, null, DataScope.Type.ALL.name())).thenReturn(
                new HrRequest(10L, "HR-001", "ONBOARDING", 7L, 9L, 9L, 20L, 9L, 2L,
                        "{}", "PENDING_HR_APPROVAL", "HR", 1, draft.createdAt(), null, null,
                        null, null, null, null, java.time.LocalDate.now(), "FIXED_TERM", null, null, null, null));
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.PENDING_ONBOARD, null, null));
        when(employeeMapper.updateStatusAndOrganization(any())).thenReturn(1);

        RequestService service = new RequestService(mapper, employeeMapper);
        RequestActor manager = new RequestActor(9L, Set.of("DEPT_MANAGER"),
                new DataScope(DataScope.Type.DEPARTMENT, 9L, null, 9L));
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        HrRequest submitted = service.submit(10L, manager);
        assertThat(submitted.status()).isEqualTo("PENDING_DEPT_APPROVAL");
        when(mapper.findByIdWithScope(10L, null, 9L, DataScope.Type.DEPARTMENT.name())).thenReturn(
                submitted);
        HrRequest pendingHr = service.approve(10L, manager, "同意");
        assertThat(pendingHr.status()).isEqualTo("PENDING_HR_APPROVAL");
        HrRequest approved = service.approve(10L, hr, "确认");
        assertThat(approved.status()).isEqualTo("APPROVED");
        verify(employeeMapper).insertStatusHistory(org.mockito.ArgumentMatchers.argThat(
                history -> Long.valueOf(30L).equals(history.operatorId())));
    }

    @Test
    void leaveRequestCanBeCreatedForAnEmployeeInActorScope() {
        when(employeeMapper.findByIdWithScope(7L, null, 9L, DataScope.Type.DEPARTMENT.name()))
                .thenReturn(new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.ACTIVE, null, null));
        when(mapper.insert(any())).thenReturn(1);
        when(mapper.insertLeaveDetail(any())).thenReturn(1);
        when(mapper.findByRequestNo(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation ->
                new HrRequest(50L, invocation.getArgument(0), "LEAVE", 7L, null, 9L, 20L, 20L, null,
                        "{}", "DRAFT", null, 0, LocalDateTime.now(), null, null, "ANNUAL",
                        java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(2), "年度休假"));
        RequestService service = new RequestService(mapper, employeeMapper);
        RequestActor manager = new RequestActor(20L, Set.of("DEPT_MANAGER"),
                new DataScope(DataScope.Type.DEPARTMENT, 20L, null, 9L));

        HrRequest created = service.create(new RequestCreateRequest.Leave(7L, null, "ANNUAL",
                java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(2), "年度休假"), manager);

        assertThat(created.requestType()).isEqualTo("LEAVE");
    }

    @Test
    void requestCannotReferenceAnArchiveVersionOwnedByAnotherEmployee() {
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.ACTIVE, null, null));
        when(archiveCatalog.findOcrSourceByVersionIdWithScope(31L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(new ArchiveOcrSource(31L, "object-key", "source.pdf", "application/pdf", 128,
                        "sha256", "CONTRACT", 8L, 9L, 30L));
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new RequestService(mapper, employeeMapper, null, null,
                                new EmployeeChangeService(mapper, employeeMapper), archiveCatalog).create(
                                new RequestCreateRequest.Leave(7L, 31L, "ANNUAL", java.time.LocalDate.now(),
                                        java.time.LocalDate.now().plusDays(1), "年度休假"), hr))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不属于所选员工");

        verify(mapper, never()).insert(any());
    }

    @Test
    void requestCannotReferenceAnArchiveVersionOutsideActorScope() {
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.ACTIVE, null, null));
        when(archiveCatalog.findOcrSourceByVersionIdWithScope(31L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(null);
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new RequestService(mapper, employeeMapper, null, null,
                                new EmployeeChangeService(mapper, employeeMapper), archiveCatalog).create(
                                new RequestCreateRequest.Leave(7L, 31L, "ANNUAL", java.time.LocalDate.now(),
                                        java.time.LocalDate.now().plusDays(1), "年度休假"), hr))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("来源档案版本");

        verify(mapper, never()).insert(any());
    }

    @Test
    void inactiveEmployeeCannotReceiveANewLeaveRequest() {
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.INACTIVE, null, java.time.LocalDate.now()));
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RequestService(mapper, employeeMapper).create(
                        new RequestCreateRequest.Leave(7L, null, "ANNUAL", java.time.LocalDate.now(),
                                java.time.LocalDate.now().plusDays(2), "年度休假"), hr))
                .isInstanceOf(RequestStateException.class)
                .hasMessageContaining("在职");

        verify(mapper, never()).insert(any());
    }

    @Test
    void leaveApprovalDoesNotChangeEmployeeEmploymentState() {
        HrRequest leave = new HrRequest(12L, "HR-LEAVE-001", "LEAVE", 7L, null, 9L, 20L, 7L, null,
                "{}", "PENDING_HR_APPROVAL", "HR", 1, LocalDateTime.now(), LocalDateTime.now(), null,
                "ANNUAL", java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(1), "休假");
        when(mapper.findByIdWithScope(12L, null, null, DataScope.Type.ALL.name())).thenReturn(leave);
        when(mapper.updateState(any())).thenReturn(1);
        RequestService service = new RequestService(mapper, employeeMapper);
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        HrRequest approved = service.approve(12L, hr, "同意");

        assertThat(approved.status()).isEqualTo("APPROVED");
        verify(employeeMapper, never()).updateStatusAndOrganization(any());
    }

    @Test
    void outOfScopeEmployeeCannotReceiveARequestFromDepartmentActor() {
        RequestService service = new RequestService(mapper, employeeMapper);
        RequestActor manager = new RequestActor(20L, Set.of("DEPT_MANAGER"),
                new DataScope(DataScope.Type.DEPARTMENT, 20L, null, 9L));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(
                new RequestCreateRequest.Transfer(77L, null, 10L, 30L, java.time.LocalDate.now(), "原因"), manager))
                .isInstanceOf(RequestStateException.class)
                .hasMessageContaining("无权")
                .hasMessageContaining("员工");
    }

    @Test
    void rejectionRequiresANonBlankReason() {
        RequestActor manager = new RequestActor(20L, Set.of("DEPT_MANAGER"),
                new DataScope(DataScope.Type.DEPARTMENT, 20L, null, 9L));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new RequestService(mapper, employeeMapper).reject(15L, manager, " \t "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("驳回原因");

        verify(mapper, never()).updateState(any());
        verify(mapper, never()).insertApproval(any());
    }

    @Test
    void rejectionStoresTrimmedReason() {
        HrRequest pending = new HrRequest(15L, "HR-015", "TRANSFER", 7L, 10L, 9L, 20L, 30L, null,
                "{}", "PENDING_DEPT_APPROVAL", "DEPARTMENT", 1, LocalDateTime.now(),
                LocalDateTime.now(), null, null, null, null, null, java.time.LocalDate.now(), null,
                null, null, null, "岗位调整");
        when(mapper.findByIdWithScope(15L, null, 9L, DataScope.Type.DEPARTMENT.name())).thenReturn(pending);
        when(mapper.updateState(any())).thenReturn(1);
        RequestActor manager = new RequestActor(20L, Set.of("DEPT_MANAGER"),
                new DataScope(DataScope.Type.DEPARTMENT, 20L, null, 9L));

        new RequestService(mapper, employeeMapper).reject(15L, manager, "  岗位不匹配  ");

        verify(mapper).insertApproval(org.mockito.ArgumentMatchers.argThat(
                record -> "岗位不匹配".equals(record.comment())));
    }

    @Test
    void rejectionReasonCannotExceed512Characters() {
        RequestActor manager = new RequestActor(20L, Set.of("DEPT_MANAGER"),
                new DataScope(DataScope.Type.DEPARTMENT, 20L, null, 9L));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new RequestService(mapper, employeeMapper).reject(15L, manager, "x".repeat(513)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("512");

        verify(mapper, never()).updateState(any());
        verify(mapper, never()).insertApproval(any());
    }

    @Test
    void departmentManagerWithHrRoleStillCannotApproveAnotherDepartment() {
        HrRequest request = new HrRequest(15L, "HR-015", "TRANSFER", 7L, 10L, 9L, 20L, 30L, null,
                "{}", "PENDING_DEPT_APPROVAL", "DEPARTMENT", 1, LocalDateTime.now(),
                LocalDateTime.now(), null, null, null, null, null, java.time.LocalDate.now(), null,
                null, null, null, "岗位调整");
        when(mapper.findByIdWithScope(15L, null, 10L, DataScope.Type.ALL.name())).thenReturn(request);
        RequestActor crossDepartmentManager = new RequestActor(40L, Set.of("HR_ADMIN", "DEPT_MANAGER"),
                new DataScope(DataScope.Type.ALL, 40L, null, 10L));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RequestService(mapper, employeeMapper)
                        .approve(15L, crossDepartmentManager, "审批"))
                .isInstanceOf(RequestStateException.class)
                .hasMessageContaining("其他部门");

        verify(mapper, never()).updateState(any());
    }

    @Test
    void transferCannotBeCreatedForInactiveEmployee() {
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name())).thenReturn(
                new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.INACTIVE, null, java.time.LocalDate.now()));
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RequestService(mapper, employeeMapper).create(
                        new RequestCreateRequest.Transfer(7L, null, 10L, 30L,
                                java.time.LocalDate.now().plusDays(1), "岗位调整"), hr))
                .isInstanceOf(RequestStateException.class)
                .hasMessageContaining("在职");

        verify(mapper, never()).insert(any());
    }

    @Test
    void onboardingCannotBeCreatedForAnAlreadyActiveEmployee() {
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name())).thenReturn(
                new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.ACTIVE, null, null));
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RequestService(mapper, employeeMapper).create(
                        new RequestCreateRequest.Onboarding(7L, null, 9L, 20L,
                                java.time.LocalDate.now(), "FIXED_TERM"), hr))
                .isInstanceOf(RequestStateException.class)
                .hasMessageContaining("待入职");

        verify(mapper, never()).insert(any());
    }

    @Test
    void offboardingCannotBeCreatedForAnInactiveEmployee() {
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name())).thenReturn(
                new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.INACTIVE, null, java.time.LocalDate.now()));
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RequestService(mapper, employeeMapper).create(
                        new RequestCreateRequest.Offboarding(7L, null,
                                java.time.LocalDate.now().plusDays(1), "负责人", "离职"), hr))
                .isInstanceOf(RequestStateException.class)
                .hasMessageContaining("在职");

        verify(mapper, never()).insert(any());
    }

    @Test
    void offboardingCannotBeCreatedWhileEmployeeIsOnLeave() {
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name())).thenReturn(
                new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.ON_LEAVE, null, null));
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RequestService(mapper, employeeMapper).create(
                        new RequestCreateRequest.Offboarding(7L, null,
                                java.time.LocalDate.now().plusDays(1), "负责人", "离职"), hr))
                .isInstanceOf(RequestStateException.class)
                .hasMessageContaining("在职");

        verify(mapper, never()).insert(any());
    }

    @Test
    void transferCannotBeCreatedWhileEmployeeIsOnLeave() {
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name())).thenReturn(
                new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.ON_LEAVE, null, null));
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RequestService(mapper, employeeMapper).create(
                        new RequestCreateRequest.Transfer(7L, null, 10L, 30L,
                                java.time.LocalDate.now().plusDays(1), "岗位调整"), hr))
                .isInstanceOf(RequestStateException.class)
                .hasMessageContaining("在职");

        verify(mapper, never()).insert(any());
    }

    @Test
    void approvedOnboardingDoesNotOverwriteStatusChangedAfterSubmission() {
        HrRequest pendingHr = new HrRequest(18L, "HR-018", "ONBOARDING", 7L, 9L, 9L, 20L, 30L, null,
                "{}", "PENDING_HR_APPROVAL", "HR", 1, LocalDateTime.now(), LocalDateTime.now(), null,
                null, null, null, null, java.time.LocalDate.now(), "FIXED_TERM", null, null, null, null);
        when(mapper.findByIdWithScope(18L, null, null, DataScope.Type.ALL.name())).thenReturn(pendingHr);
        when(mapper.updateState(any())).thenReturn(1);
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name())).thenReturn(
                new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.ACTIVE, null, null));
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new RequestService(mapper, employeeMapper).approve(18L, hr, "确认"))
                .isInstanceOf(RequestStateException.class)
                .hasMessageContaining("状态已变化");

        verify(employeeMapper, never()).updateStatusAndOrganization(any());
        verify(mapper, never()).markEmployeeChangeApplied(org.mockito.ArgumentMatchers.eq(18L), any());
    }

    @Test
    void futureOnboardingApprovalDefersEmployeeMutationUntilEffectiveDate() {
        java.time.LocalDate effectiveDate = java.time.LocalDate.now().plusDays(5);
        HrRequest pendingHr = new HrRequest(10L, "HR-001", "ONBOARDING", 7L, 9L, 9L, 20L, 9L, 2L,
                "{}", "PENDING_HR_APPROVAL", "HR", 1, LocalDateTime.now(), LocalDateTime.now(), null,
                null, null, null, null, effectiveDate, "FIXED_TERM", null, null, null, null);
        when(mapper.findByIdWithScope(10L, null, null, DataScope.Type.ALL.name())).thenReturn(pendingHr);
        when(mapper.updateState(any())).thenReturn(1);
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        HrRequest approved = new RequestService(mapper, employeeMapper).approve(10L, hr, "确认");

        assertThat(approved.status()).isEqualTo("APPROVED");
        verify(employeeMapper, never()).updateStatusAndOrganization(any());
    }

    @Test
    void transferApprovalPreservesCurrentEmploymentStatus() {
        LocalDateTime now = LocalDateTime.now();
        HrRequest pendingHr = new HrRequest(16L, "HR-016", "TRANSFER", 7L, 10L, 9L, 30L, 20L, null,
                "{}", "PENDING_HR_APPROVAL", "HR", 1, now, now, null,
                null, null, null, null, java.time.LocalDate.now(), null, null, null, null, "岗位调整");
        when(mapper.findByIdWithScope(16L, null, null, DataScope.Type.ALL.name())).thenReturn(pendingHr);
        when(mapper.updateState(any())).thenReturn(1);
        when(mapper.markEmployeeChangeApplied(org.mockito.ArgumentMatchers.eq(16L), any())).thenReturn(1);
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name())).thenReturn(
                new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.ACTIVE, null, null));
        when(employeeMapper.updateStatusAndOrganization(any())).thenReturn(1);
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        new RequestService(mapper, employeeMapper).approve(16L, hr, "确认");

        verify(employeeMapper).updateStatusAndOrganization(org.mockito.ArgumentMatchers.argThat(
                mutation -> mutation.status() == com.hrplatform.employee.EmployeeStatus.ACTIVE
                        && Long.valueOf(10L).equals(mutation.departmentId())
                        && Long.valueOf(30L).equals(mutation.positionId())));
        verify(employeeMapper).insertStatusHistory(org.mockito.ArgumentMatchers.argThat(
                history -> Long.valueOf(30L).equals(history.operatorId())));
    }

    @Test
    void scheduledChangeRunsWithNullOperatorAndSystemAudit() {
        LocalDateTime now = LocalDateTime.now();
        HrRequest due = new HrRequest(17L, "HR-017", "TRANSFER", 7L, 10L, 9L, 30L, 20L, null,
                "{}", "APPROVED", null, 2, now.minusDays(2), now.minusDays(2), now.minusDays(1),
                null, null, null, null, java.time.LocalDate.now().minusDays(1), null, null, null,
                null, "岗位调整", null);
        com.hrplatform.audit.OperationLogService operationLogService =
                org.mockito.Mockito.mock(com.hrplatform.audit.OperationLogService.class);
        when(mapper.lockDueEmployeeChange(17L, java.time.LocalDate.now())).thenReturn(17L);
        when(mapper.findByIdWithScope(17L, null, null, DataScope.Type.ALL.name())).thenReturn(due);
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name())).thenReturn(
                new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.ACTIVE, null, null));
        when(employeeMapper.updateStatusAndOrganization(any())).thenReturn(1);
        when(mapper.markEmployeeChangeApplied(org.mockito.ArgumentMatchers.eq(17L), any())).thenReturn(1);

        boolean applied = new RequestService(mapper, employeeMapper, operationLogService)
                .applyDueEmployeeChange(17L);

        assertThat(applied).isTrue();
        verify(employeeMapper).insertStatusHistory(org.mockito.ArgumentMatchers.argThat(
                history -> history.operatorId() == null));
        verify(operationLogService).record(null, "APPLY_EMPLOYEE_CHANGE", "HR_REQUEST", 17L, "SUCCESS");
    }

    @Test
    void offboardingRequesterCannotMarkHandoverComplete() {
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.ACTIVE, null, null));
        when(mapper.insert(any())).thenReturn(1);
        when(mapper.insertOffboardingDetail(any())).thenReturn(1);
        when(mapper.findByRequestNo(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation ->
                new HrRequest(50L, invocation.getArgument(0), "OFFBOARDING", 7L, null, 9L, 20L, 30L,
                        null, "{}", "DRAFT", null, 0, LocalDateTime.now(), null, null,
                        null, null, null, null));
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        new RequestService(mapper, employeeMapper).create(new RequestCreateRequest.Offboarding(7L, null,
                java.time.LocalDate.now().plusDays(30), "负责人", "离职交接"), hr);

        verify(mapper).insertOffboardingDetail(org.mockito.ArgumentMatchers.argThat(
                detail -> "PENDING".equals(detail.handoverStatus())));
    }

    @Test
    void approvedOnboardingAppliesRequestedHireDate() {
        LocalDateTime now = LocalDateTime.now();
        java.time.LocalDate effectiveDate = java.time.LocalDate.now();
        HrRequest pendingHr = new HrRequest(10L, "HR-001", "ONBOARDING", 7L, 9L, 9L, 20L, 9L, 2L,
                "{}", "PENDING_HR_APPROVAL", "HR", 1, now, now, null,
                null, null, null, null, effectiveDate, "FIXED_TERM", null, null, null, null);
        when(mapper.findByIdWithScope(10L, null, null, DataScope.Type.ALL.name())).thenReturn(pendingHr);
        when(mapper.updateState(any())).thenReturn(1);
        when(mapper.markEmployeeChangeApplied(org.mockito.ArgumentMatchers.eq(10L), any())).thenReturn(1);
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.PENDING_ONBOARD, null, null));
        when(employeeMapper.updateStatusAndOrganization(any())).thenReturn(1);
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        new RequestService(mapper, employeeMapper).approve(10L, hr, "确认");

        verify(employeeMapper).updateStatusAndOrganization(org.mockito.ArgumentMatchers.argThat(
                mutation -> effectiveDate.equals(mutation.hireDate())));
    }
}
