package com.hrplatform.access;

import com.hrplatform.archive.ArchiveMapper;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.eq;

@ExtendWith(MockitoExtension.class)
class ArchiveAccessServiceTest {
    @Mock
    private AccessMapper mapper;
    @Mock
    private ArchiveMapper archiveMapper;

    @Test
    void createReadsGeneratedApplicationIdByApplicationNumber() {
        when(archiveMapper.findOcrSourceByVersionIdWithScope(3L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(new ArchiveOcrSource(3L, "key", "file.png", "image/png", 10L, "hash",
                        "profile", 7L, 9L));
        when(mapper.insertApplication(any())).thenReturn(1);
        when(mapper.findByApplicationNo(any())).thenAnswer(invocation -> new ArchiveAccessApplication(
                22L, invocation.getArgument(0), 20L, null, "查看档案", "READ", LocalDateTime.now(),
                LocalDateTime.now().plusDays(2), "DRAFT", 0, LocalDateTime.now()));
        when(mapper.insertItem(any())).thenReturn(1);
        AccessActor actor = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, null, null));

        ArchiveAccessApplication created = new ArchiveAccessService(mapper, archiveMapper).create(
                new AccessCreateRequest("查看档案", "READ", LocalDateTime.now(),
                        LocalDateTime.now().plusDays(2), List.of(3L)), actor);

        org.assertj.core.api.Assertions.assertThat(created.id()).isEqualTo(22L);
    }

    @Test
    void omittedStartTimeIsStoredAtDatabaseSecondPrecision() {
        when(archiveMapper.findOcrSourceByVersionIdWithScope(3L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(new ArchiveOcrSource(3L, "key", "file.png", "image/png", 10L, "hash",
                        "profile", 7L, 9L));
        when(mapper.insertApplication(any())).thenReturn(1);
        when(mapper.findByApplicationNo(any())).thenAnswer(invocation -> new ArchiveAccessApplication(
                22L, invocation.getArgument(0), 20L, null, "查看档案", "READ", LocalDateTime.now(),
                LocalDateTime.now().plusDays(2), "DRAFT", 0, LocalDateTime.now()));
        when(mapper.insertItem(any())).thenReturn(1);
        AccessActor actor = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, null, null));

        new ArchiveAccessService(mapper, archiveMapper).create(
                new AccessCreateRequest("查看档案", "READ", null,
                        LocalDateTime.now().plusDays(2), List.of(3L)), actor);

        ArgumentCaptor<ArchiveAccessApplication> application = ArgumentCaptor.forClass(ArchiveAccessApplication.class);
        verify(mapper).insertApplication(application.capture());
        assertThat(application.getValue().startAt().getNano()).isZero();
    }

    @Test
    void borrowIsBlockedWhenArchiveOwnerIsOnLeave() {
        com.hrplatform.employee.EmployeeMapper employeeMapper = mock(com.hrplatform.employee.EmployeeMapper.class);
        when(archiveMapper.findOcrSourceByVersionIdWithScope(3L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(new ArchiveOcrSource(3L, "key", "file.png", "image/png", 10L, "hash",
                        "profile", 7L, 9L));
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(new com.hrplatform.employee.Employee(7L, "EMP-007", "演示员工", null,
                        9L, 20L, com.hrplatform.employee.EmployeeStatus.ON_LEAVE, null, null));
        AccessActor actor = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, null, null));

        assertThatThrownBy(() -> new ArchiveAccessService(mapper, archiveMapper, null, employeeMapper).create(
                        new AccessCreateRequest("借出档案", "BORROW", LocalDateTime.now(),
                                LocalDateTime.now().plusDays(2), List.of(3L)), actor))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("封存");

        verify(mapper, never()).insertApplication(any());
    }

    @Test
    void readIsAllowedEvenWhenArchiveOwnerIsOnLeave() {
        com.hrplatform.employee.EmployeeMapper employeeMapper = mock(com.hrplatform.employee.EmployeeMapper.class);
        when(archiveMapper.findOcrSourceByVersionIdWithScope(3L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(new ArchiveOcrSource(3L, "key", "file.png", "image/png", 10L, "hash",
                        "profile", 7L, 9L));
        when(mapper.insertApplication(any())).thenReturn(1);
        when(mapper.findByApplicationNo(any())).thenAnswer(invocation -> new ArchiveAccessApplication(
                22L, invocation.getArgument(0), 20L, null, "查阅档案", "READ", LocalDateTime.now(),
                LocalDateTime.now().plusDays(2), "DRAFT", 0, LocalDateTime.now()));
        when(mapper.insertItem(any())).thenReturn(1);
        AccessActor actor = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, null, null));

        ArchiveAccessApplication created = new ArchiveAccessService(mapper, archiveMapper, null, employeeMapper)
                .create(new AccessCreateRequest("查阅档案", "READ", LocalDateTime.now(),
                        LocalDateTime.now().plusDays(2), List.of(3L)), actor);

        org.assertj.core.api.Assertions.assertThat(created.id()).isEqualTo(22L);
    }

    @Test
    void departmentManagerCannotSubmitAnotherUsersDraft() {
        ArchiveAccessApplication application = new ArchiveAccessApplication(8L, "ACC-8", 55L, 9L,
                "查看档案", "READ", LocalDateTime.now(), LocalDateTime.now().plusDays(2),
                "DRAFT", 0, LocalDateTime.now());
        when(mapper.findApplication(8L, null, 9L, DataScope.Type.DEPARTMENT.name()))
                .thenReturn(application);
        AccessActor manager = new AccessActor(20L, Set.of("DEPT_MANAGER"),
                new DataScope(DataScope.Type.DEPARTMENT, 20L, null, 9L));

        assertThatThrownBy(() -> new ArchiveAccessService(mapper, archiveMapper).submit(8L, manager))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("申请人");

        verify(mapper, never()).updateState(any());
    }

    @Test
    void successfulSubmitIsRecordedAfterStateChange() {
        ArchiveAccessApplication application = new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L,
                "查看档案", "READ", LocalDateTime.now(), LocalDateTime.now().plusDays(2),
                "DRAFT", 0, LocalDateTime.now());
        when(mapper.findApplication(8L, 200L, 9L, DataScope.Type.ALL.name())).thenReturn(application);
        when(mapper.updateState(any())).thenReturn(1);
        OperationLogService operationLogService = mock(OperationLogService.class);
        AccessActor applicant = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, 200L, 9L));

        ArchiveAccessApplication updated = new ArchiveAccessService(mapper, archiveMapper, operationLogService)
                .submit(8L, applicant);

        org.assertj.core.api.Assertions.assertThat(updated.status()).isEqualTo("PENDING_APPROVAL");
        verify(operationLogService).record(20L, "SUBMIT", "ARCHIVE_ACCESS_APPLICATION", 8L, "SUCCESS",
                "status=DRAFT;useType=READ;version=0", "status=PENDING_APPROVAL;useType=READ;version=1");
    }

    @Test
    void hrRejectionStoresTheRequiredReasonAndChangesApplicationState() {
        ArchiveAccessApplication application = new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L,
                "查看档案", "READ", LocalDateTime.now(), LocalDateTime.now().plusDays(2),
                "PENDING_APPROVAL", 1, LocalDateTime.now());
        when(mapper.findApplication(eq(8L), any(), eq(9L), eq(DataScope.Type.ALL.name()))).thenReturn(application);
        when(mapper.updateState(any())).thenReturn(1);
        when(mapper.insertDecision(any())).thenReturn(1);
        OperationLogService audit = mock(OperationLogService.class);
        AccessActor hr = new AccessActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, 300L, 9L));

        ArchiveAccessApplication rejected = new ArchiveAccessService(mapper, archiveMapper, audit)
                .reject(8L, "  用途超出授权范围  ", hr);

        assertThat(rejected.status()).isEqualTo("REJECTED");
        ArgumentCaptor<ArchiveAccessApplication> updated = ArgumentCaptor.forClass(ArchiveAccessApplication.class);
        verify(mapper).updateState(updated.capture());
        assertThat(updated.getValue().status()).isEqualTo("REJECTED");
        ArgumentCaptor<ArchiveAccessDecision> decision = ArgumentCaptor.forClass(ArchiveAccessDecision.class);
        verify(mapper).insertDecision(decision.capture());
        assertThat(decision.getValue().decidedByUserId()).isEqualTo(30L);
        assertThat(decision.getValue().decision()).isEqualTo("REJECTED");
        assertThat(decision.getValue().decisionNote()).isEqualTo("用途超出授权范围");
        verify(audit).record(30L, "REJECT", "ARCHIVE_ACCESS_APPLICATION", 8L, "SUCCESS",
                "status=PENDING_APPROVAL;useType=READ;version=1", "status=REJECTED;useType=READ;version=2");
    }

    @Test
    void rejectionRequiresHrRoleAndNonblankReason() {
        ArchiveAccessApplication application = new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L,
                "查看档案", "READ", LocalDateTime.now(), LocalDateTime.now().plusDays(2),
                "PENDING_APPROVAL", 1, LocalDateTime.now());
        when(mapper.findApplication(eq(8L), any(), eq(9L), eq(DataScope.Type.ALL.name()))).thenReturn(application);
        AccessActor employee = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, 200L, 9L));
        AccessActor hr = new AccessActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, 300L, 9L));
        ArchiveAccessService service = new ArchiveAccessService(mapper, archiveMapper);

        assertThatThrownBy(() -> service.reject(8L, "拒绝", employee))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("人事管理员");
        assertThatThrownBy(() -> service.reject(8L, "   ", hr))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("不能为空");

        verify(mapper, never()).updateState(any());
        verify(mapper, never()).insertDecision(any());
    }

    @Test
    void hrApprovalAlsoCreatesAnAuditableDecisionRecord() {
        ArchiveAccessApplication application = new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L,
                "查看档案", "READ", LocalDateTime.now(), LocalDateTime.now().plusDays(2),
                "PENDING_APPROVAL", 1, LocalDateTime.now());
        when(mapper.findApplication(eq(8L), any(), eq(9L), eq(DataScope.Type.ALL.name()))).thenReturn(application);
        when(mapper.updateState(any())).thenReturn(1);
        when(mapper.insertDecision(any())).thenReturn(1);
        AccessActor hr = new AccessActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, 300L, 9L));

        assertThat(new ArchiveAccessService(mapper, archiveMapper).approve(8L, hr).status())
                .isEqualTo("APPROVED");
        ArgumentCaptor<ArchiveAccessDecision> decision = ArgumentCaptor.forClass(ArchiveAccessDecision.class);
        verify(mapper).insertDecision(decision.capture());
        assertThat(decision.getValue().decision()).isEqualTo("APPROVED");
        assertThat(decision.getValue().decisionNote()).isNull();
    }

    @Test
    void creatingApplicationRejectsVersionOutsideActorScope() {
        when(archiveMapper.findOcrSourceByVersionIdWithScope(3L, null, 9L, DataScope.Type.DEPARTMENT.name()))
                .thenReturn(null);
        AccessActor actor = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.DEPARTMENT, 20L, null, 9L));
        ArchiveAccessService service = new ArchiveAccessService(mapper, archiveMapper);

        assertThatThrownBy(() -> service.create(new AccessCreateRequest("查阅", "READ",
                        LocalDateTime.now(), LocalDateTime.now().plusDays(2), List.of(3L)), actor))
                .isInstanceOf(ArchiveAccessStateException.class);
    }

    @Test
    void createRejectsAccessWindowWithEndBeforeStart() {
        LocalDateTime now = LocalDateTime.now();
        AccessActor actor = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, 200L, 9L));

        assertThatThrownBy(() -> new ArchiveAccessService(mapper, archiveMapper).create(
                        new AccessCreateRequest("查看档案", "READ", now.plusDays(3), now.plusDays(2), List.of(3L)),
                        actor))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("起始");
    }

    @Test
    void useIsRejectedBeforeAccessWindowStarts() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.findApplication(8L, 200L, 9L, DataScope.Type.ALL.name())).thenReturn(
                new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L, "查看档案", "READ",
                        now.plusDays(1), now.plusDays(2), "APPROVED", 1, now));
        AccessActor applicant = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, 200L, 9L));

        assertThatThrownBy(() -> new ArchiveAccessService(mapper, archiveMapper).use(8L, applicant))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("尚未开始");

        verify(mapper, never()).updateState(any());
    }

    @Test
    void useIsRejectedAfterAccessWindowExpires() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.findApplication(8L, 200L, 9L, DataScope.Type.ALL.name())).thenReturn(
                new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L, "查看档案", "READ",
                        now.minusDays(2), now.minusSeconds(1), "APPROVED", 1, now.minusDays(3)));
        AccessActor applicant = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, 200L, 9L));

        assertThatThrownBy(() -> new ArchiveAccessService(mapper, archiveMapper).use(8L, applicant))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("已过期");

        verify(mapper, never()).updateState(any());
    }

    @Test
    void expiredApplicationCannotStartUse() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.findApplication(8L, 200L, 9L, DataScope.Type.ALL.name())).thenReturn(
                new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L, "查看档案", "READ",
                        now.minusDays(2), now.minusSeconds(1), "EXPIRED", 2, now.minusDays(3)));
        AccessActor applicant = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, 200L, 9L));

        assertThatThrownBy(() -> new ArchiveAccessService(mapper, archiveMapper).use(8L, applicant))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("已过期");

        verify(mapper, never()).insertUse(any());
        verify(mapper, never()).updateState(any());
    }

    @Test
    void scheduledExpiryMarksOnlyRowsStillApprovedAndAuditsSuccessfulChanges() {
        when(mapper.listExpiredApprovedIds(any(), eq(100))).thenReturn(List.of(8L, 9L));
        when(mapper.markApprovedExpired(eq(8L), any())).thenReturn(1);
        when(mapper.markApprovedExpired(eq(9L), any())).thenReturn(0);
        OperationLogService audit = mock(OperationLogService.class);

        int expired = new ArchiveAccessService(mapper, archiveMapper, audit).expireUnusedApprovedApplications();

        assertThat(expired).isEqualTo(1);
        verify(audit).record(null, "EXPIRE_UNUSED", "ARCHIVE_ACCESS_APPLICATION", 8L, "SUCCESS",
                "status=APPROVED", "status=EXPIRED");
        verify(audit, never()).record(null, "EXPIRE_UNUSED", "ARCHIVE_ACCESS_APPLICATION", 9L, "SUCCESS",
                "status=APPROVED", "status=EXPIRED");
    }

    @Test
    void handoverCannotBeReturnedWithoutAReceivingEmployee() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.findApplication(8L, 200L, 9L, DataScope.Type.ALL.name())).thenReturn(
                new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L, "移交档案", "HANDOVER",
                        now.minusHours(1), now.plusHours(1), "IN_USE", 2, now.minusDays(1)));
        when(mapper.findActiveUse(8L)).thenReturn(
                new ArchiveUseRecord(12L, 8L, 20L, now.minusMinutes(5), null, null, null, null, null));
        AccessActor applicant = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, 200L, 9L));

        assertThatThrownBy(() -> new ArchiveAccessService(mapper, archiveMapper)
                .returnAccess(8L, applicant, false, null))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("接收员工");

        verify(mapper, never()).updateState(any());
        verify(mapper, never()).closeUse(any());
    }

    @Test
    void handoverRejectsInactiveOrOutOfDepartmentRecipient() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.findApplication(8L, 200L, 9L, DataScope.Type.ALL.name())).thenReturn(
                new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L, "移交档案", "HANDOVER",
                        now.minusHours(1), now.plusHours(1), "IN_USE", 2, now.minusDays(1)));
        when(mapper.findActiveUse(8L)).thenReturn(
                new ArchiveUseRecord(12L, 8L, 20L, now.minusMinutes(5), null, null, null, null, null));
        when(mapper.isActiveEmployeeInDepartment(72L, 9L)).thenReturn(false);
        AccessActor applicant = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, 200L, 9L));

        assertThatThrownBy(() -> new ArchiveAccessService(mapper, archiveMapper)
                .returnAccess(8L, applicant, false, "交接", 72L))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("不属于申请部门");

        verify(mapper, never()).updateState(any());
        verify(mapper, never()).closeUse(any());
    }

    @Test
    void abnormalReturnCannotClaimAReceivingEmployee() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.findApplication(8L, 200L, 9L, DataScope.Type.ALL.name())).thenReturn(
                new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L, "移交档案", "HANDOVER",
                        now.minusHours(1), now.plusHours(1), "IN_USE", 2, now.minusDays(1)));
        when(mapper.findActiveUse(8L)).thenReturn(
                new ArchiveUseRecord(12L, 8L, 20L, now.minusMinutes(5), null, null, null, null, null));
        AccessActor applicant = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, 200L, 9L));

        assertThatThrownBy(() -> new ArchiveAccessService(mapper, archiveMapper)
                .returnAccess(8L, applicant, true, "发现缺页", 72L))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("异常归还不能登记接收员工");

        verify(mapper, never()).updateState(any());
        verify(mapper, never()).closeUse(any());
    }

    @Test
    void abnormalReturnRequiresAnExplanationBeforeClosingTheUseRecord() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.findApplication(8L, 200L, 9L, DataScope.Type.ALL.name())).thenReturn(
                new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L, "借阅档案", "BORROW",
                        now.minusHours(1), now.plusHours(1), "IN_USE", 2, now.minusDays(1)));
        when(mapper.findActiveUse(8L)).thenReturn(
                new ArchiveUseRecord(12L, 8L, 20L, now.minusMinutes(5), null, null, null, null, null));
        AccessActor applicant = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, 200L, 9L));

        assertThatThrownBy(() -> new ArchiveAccessService(mapper, archiveMapper)
                .returnAccess(8L, applicant, true, "  "))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("异常说明");

        verify(mapper, never()).updateState(any());
        verify(mapper, never()).closeUse(any());
    }

    @Test
    void abnormalResolutionRequiresAnExplanation() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.findApplication(8L, 200L, 9L, DataScope.Type.ALL.name())).thenReturn(
                new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L, "借阅档案", "BORROW",
                        now.minusHours(2), now.minusSeconds(1), "ABNORMAL", 3, now.minusDays(1)));
        AccessActor hr = new AccessActor(20L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 20L, 200L, 9L));

        assertThatThrownBy(() -> new ArchiveAccessService(mapper, archiveMapper)
                .resolveAbnormal(8L, hr, "  "))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("异常处理说明");

        verify(mapper, never()).updateState(any());
    }

    @Test
    void abnormalCannotBeResolvedWhileAnArchiveUseRecordIsStillActive() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.findApplication(8L, null, null, DataScope.Type.ALL.name())).thenReturn(
                new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L, "借阅档案", "BORROW",
                        now.minusHours(2), now.minusSeconds(1), "ABNORMAL", 3, now.minusDays(1)));
        when(mapper.findActiveUse(8L)).thenReturn(
                new ArchiveUseRecord(12L, 8L, 20L, now.minusHours(1), null, null, null, null, null));
        AccessActor hr = new AccessActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        assertThatThrownBy(() -> new ArchiveAccessService(mapper, archiveMapper)
                .resolveAbnormal(8L, hr, "档案仍未交还"))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("登记档案归还");

        verify(mapper, never()).closeUse(any());
        verify(mapper, never()).updateState(any());
    }

    @Test
    void successfulAbnormalResolutionPersistsActorTimeAndTrimmedExplanation() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.findApplication(8L, null, null, DataScope.Type.ALL.name())).thenReturn(
                new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L, "借阅档案", "BORROW",
                        now.minusHours(2), now.minusSeconds(1), "ABNORMAL", 3, now.minusDays(1)));
        when(mapper.updateState(any())).thenReturn(1);
        when(mapper.insertResolution(any())).thenReturn(1);
        AccessActor hr = new AccessActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        ArchiveAccessApplication resolved = new ArchiveAccessService(mapper, archiveMapper)
                .resolveAbnormal(8L, hr, "  已补齐材料并完成复核  ");

        ArgumentCaptor<ArchiveAccessResolution> resolution = ArgumentCaptor.forClass(ArchiveAccessResolution.class);
        verify(mapper).insertResolution(resolution.capture());
        assertThat(resolved.status()).isEqualTo("RETURNED");
        assertThat(resolution.getValue().applicationId()).isEqualTo(8L);
        assertThat(resolution.getValue().resolvedByUserId()).isEqualTo(30L);
        assertThat(resolution.getValue().resolutionNote()).isEqualTo("已补齐材料并完成复核");
        assertThat(resolution.getValue().resolvedAt()).isNotNull();
    }

    @Test
    void abnormalResolutionCannotExceedStorageLength() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.findApplication(8L, null, null, DataScope.Type.ALL.name())).thenReturn(
                new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L, "借阅档案", "BORROW",
                        now.minusHours(2), now.minusSeconds(1), "ABNORMAL", 3, now.minusDays(1)));
        AccessActor hr = new AccessActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        assertThatThrownBy(() -> new ArchiveAccessService(mapper, archiveMapper)
                .resolveAbnormal(8L, hr, "a".repeat(513)))
                .isInstanceOf(ArchiveAccessStateException.class)
                .hasMessageContaining("512");

        verify(mapper, never()).updateState(any());
        verify(mapper, never()).insertResolution(any());
    }

    @Test
    void successfulHandoverRecordsReceivingEmployeeAndReturnOperator() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.findApplication(8L, 200L, 9L, DataScope.Type.ALL.name())).thenReturn(
                new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L, "移交档案", "HANDOVER",
                        now.minusHours(1), now.plusHours(1), "IN_USE", 2, now.minusDays(1)));
        when(mapper.findActiveUse(8L)).thenReturn(
                new ArchiveUseRecord(12L, 8L, 20L, now.minusMinutes(5), null, null, null, null, null));
        when(mapper.isActiveEmployeeInDepartment(72L, 9L)).thenReturn(true);
        when(mapper.updateState(any())).thenReturn(1);
        when(mapper.closeUse(any())).thenReturn(1);
        AccessActor applicant = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, 200L, 9L));

        ArchiveAccessApplication returned = new ArchiveAccessService(mapper, archiveMapper)
                .returnAccess(8L, applicant, false, "已当面移交", 72L);

        ArgumentCaptor<ArchiveUseRecord> record = ArgumentCaptor.forClass(ArchiveUseRecord.class);
        verify(mapper).closeUse(record.capture());
        assertThat(returned.status()).isEqualTo("RETURNED");
        assertThat(record.getValue().returnStatus()).isEqualTo("HANDED_OVER");
        assertThat(record.getValue().returnedByUserId()).isEqualTo(20L);
        assertThat(record.getValue().handoverToEmployeeId()).isEqualTo(72L);
    }

    @Test
    void overdueHandoverIsAbnormalAndDoesNotRecordRecipient() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.findApplication(8L, 200L, 9L, DataScope.Type.ALL.name())).thenReturn(
                new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L, "移交档案", "HANDOVER",
                        now.minusHours(2), now.minusSeconds(1), "IN_USE", 2, now.minusDays(1)));
        when(mapper.findActiveUse(8L)).thenReturn(
                new ArchiveUseRecord(12L, 8L, 20L, now.minusMinutes(5), null, null, null, null, null));
        when(mapper.updateState(any())).thenReturn(1);
        when(mapper.closeUse(any())).thenReturn(1);
        AccessActor applicant = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, 200L, 9L));

        ArchiveAccessApplication returned = new ArchiveAccessService(mapper, archiveMapper)
                .returnAccess(8L, applicant, false, "逾期后完成移交", 72L);

        ArgumentCaptor<ArchiveUseRecord> record = ArgumentCaptor.forClass(ArchiveUseRecord.class);
        verify(mapper).closeUse(record.capture());
        assertThat(returned.status()).isEqualTo("ABNORMAL");
        assertThat(record.getValue().returnStatus()).isEqualTo("ABNORMAL");
        assertThat(record.getValue().handoverToEmployeeId()).isNull();
    }

    @Test
    void overdueUseCanBeReturnedAfterSchedulerMarksApplicationAbnormal() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.findApplication(8L, 200L, 9L, DataScope.Type.ALL.name())).thenReturn(
                new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L, "查看档案", "READ",
                        now.minusHours(2), now.minusSeconds(1), "ABNORMAL", 3, now.minusDays(1)));
        when(mapper.findActiveUse(8L)).thenReturn(
                new ArchiveUseRecord(12L, 8L, 20L, now.minusMinutes(5), null, null, null, null, null));
        when(mapper.updateState(any())).thenReturn(1);
        when(mapper.closeUse(any())).thenReturn(1);
        AccessActor applicant = new AccessActor(20L, Set.of("EMPLOYEE"),
                new DataScope(DataScope.Type.ALL, 20L, 200L, 9L));

        ArchiveAccessApplication returned = new ArchiveAccessService(mapper, archiveMapper)
                .returnAccess(8L, applicant, false, "逾期归还");

        assertThat(returned.status()).isEqualTo("ABNORMAL");
        verify(mapper).closeUse(any());
    }

    @Test
    void overdueSweepAuditsOnlyApplicationsThatTransitioned() {
        when(mapper.listOverdueInUseIds(any(), eq(100))).thenReturn(List.of(8L, 9L));
        when(mapper.markOverdueInUse(eq(8L), any())).thenReturn(1);
        when(mapper.markOverdueInUse(eq(9L), any())).thenReturn(0);
        OperationLogService operationLogService = mock(OperationLogService.class);

        int changed = new ArchiveAccessService(mapper, archiveMapper, operationLogService)
                .expireOverdueInUseApplications();

        assertThat(changed).isEqualTo(1);
        verify(operationLogService).record(null, "OVERDUE", "ARCHIVE_ACCESS_APPLICATION", 8L, "SUCCESS",
                "status=IN_USE", "status=OVERDUE");
        verify(operationLogService, never()).record(null, "OVERDUE", "ARCHIVE_ACCESS_APPLICATION", 9L, "SUCCESS",
                "status=IN_USE", "status=OVERDUE");
    }

    @Test
    void normalReturnRecordsOperatorWithoutHandoverRecipient() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.findApplication(8L, 200L, 9L, DataScope.Type.ALL.name())).thenReturn(
                new ArchiveAccessApplication(8L, "ACC-8", 20L, 9L, "借阅档案", "BORROW",
                        now.minusHours(1), now.plusHours(1), "IN_USE", 2, now.minusDays(1)));
        when(mapper.findActiveUse(8L)).thenReturn(
                new ArchiveUseRecord(12L, 8L, 30L, now.minusMinutes(5), null, null, null, null, null));
        when(mapper.updateState(any())).thenReturn(1);
        when(mapper.closeUse(any())).thenReturn(1);
        AccessActor hr = new AccessActor(20L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 20L, 200L, 9L));

        new ArchiveAccessService(mapper, archiveMapper).returnAccess(8L, hr, false, "归还完好");

        ArgumentCaptor<ArchiveUseRecord> record = ArgumentCaptor.forClass(ArchiveUseRecord.class);
        verify(mapper).closeUse(record.capture());
        assertThat(record.getValue().returnStatus()).isEqualTo("RETURNED");
        assertThat(record.getValue().returnedByUserId()).isEqualTo(20L);
        assertThat(record.getValue().handoverToEmployeeId()).isNull();
    }
}
