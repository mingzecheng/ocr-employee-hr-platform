package com.hrplatform.access;

import com.hrplatform.archive.ArchiveCatalog;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.employee.Employee;
import com.hrplatform.employee.EmployeeMapper;
import com.hrplatform.employee.EmployeeStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ArchiveAccessService {
    private static final int OVERDUE_BATCH_SIZE = 100;
    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AccessMapper mapper;
    private final ArchiveCatalog archiveCatalog;
    private final OperationLogService operationLogService;
    private final EmployeeMapper employeeMapper;

    @Autowired
    public ArchiveAccessService(AccessMapper mapper, ArchiveCatalog archiveCatalog,
                                OperationLogService operationLogService, EmployeeMapper employeeMapper) {
        this.mapper = mapper;
        this.archiveCatalog = archiveCatalog;
        this.operationLogService = operationLogService;
        this.employeeMapper = employeeMapper;
    }

    public ArchiveAccessService(AccessMapper mapper, ArchiveCatalog archiveCatalog) {
        this(mapper, archiveCatalog, null, null);
    }

    public ArchiveAccessService(AccessMapper mapper, ArchiveCatalog archiveCatalog,
                                OperationLogService operationLogService) {
        this(mapper, archiveCatalog, operationLogService, null);
    }

    @Transactional
    public ArchiveAccessApplication create(AccessCreateRequest request, AccessActor actor) {
        validateCreateRequest(request);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startAt = request.startAt() == null ? now.truncatedTo(ChronoUnit.SECONDS) : request.startAt();
        if (request.dueAt() == null || !request.dueAt().isAfter(now)) {
            throw new ArchiveAccessStateException("归还截止时间必须晚于当前时间");
        }
        if (request.dueAt().isBefore(startAt)) {
            throw new ArchiveAccessStateException("归还截止时间不能早于授权起始时间");
        }
        if (actor.scope().type() == DataScope.Type.NONE) {
            throw new ArchiveAccessStateException("当前用户没有档案访问范围");
        }
        for (Long versionId : request.versionIds()) {
            ArchiveOcrSource source = archiveCatalog.findOcrSourceByVersionIdWithScope(versionId,
                    actor.scope().employeeId(), actor.scope().departmentId(), actor.scope().type().name());
            if (source == null) {
                throw new ArchiveAccessStateException("档案版本不存在或无权申请: " + versionId);
            }
            requireOwnerNotOnLeaveForPhysicalCheckout(request.useType(), source);
        }
        ArchiveAccessApplication application = new ArchiveAccessApplication(null,
                "ACC-" + NO_TIME.format(now) + "-" + UUID.randomUUID().toString().substring(0, 8),
                actor.userId(), actor.scope().departmentId(), request.purpose().trim(), request.useType().trim().toUpperCase(Locale.ROOT),
                startAt, request.dueAt(), "DRAFT", 0, now);
        if (mapper.insertApplication(application) != 1) {
            throw new ArchiveAccessStateException("档案访问申请创建失败");
        }
        ArchiveAccessApplication result = mapper.findByApplicationNo(application.applicationNo());
        if (result == null) {
            throw new ArchiveAccessStateException("档案访问申请已写入但无法读取");
        }
        for (Long versionId : request.versionIds()) {
            ArchiveOcrSource source = archiveCatalog.findOcrSourceByVersionIdWithScope(versionId,
                    actor.scope().employeeId(), actor.scope().departmentId(), actor.scope().type().name());
            if (mapper.insertItem(new ArchiveAccessItem(null, result.id(), versionId,
                    source == null ? null : source.employeeId(),
                    source == null ? null : source.departmentId(), "REQUESTED")) != 1) {
                throw new ArchiveAccessStateException("档案访问申请明细创建失败: " + versionId);
            }
        }
        record(actor.userId(), "CREATE", result.id(), null, result);
        return result;
    }

    private void validateCreateRequest(AccessCreateRequest request) {
        if (request == null || request.purpose() == null || request.purpose().isBlank()) {
            throw new IllegalArgumentException("申请用途不能为空");
        }
        if (request.purpose().trim().length() > 512) {
            throw new IllegalArgumentException("申请用途不能超过 512 个字符");
        }
        String useType = request.useType() == null ? "" : request.useType().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("READ", "BORROW", "HANDOVER").contains(useType)) {
            throw new IllegalArgumentException("使用方式只能是 READ、BORROW 或 HANDOVER");
        }
        if (request.versionIds() == null || request.versionIds().isEmpty()) {
            throw new IllegalArgumentException("至少选择一个档案版本");
        }
        if (request.versionIds().size() > 200) {
            throw new IllegalArgumentException("单个授权申请最多包含 200 个档案版本");
        }
        if (request.versionIds().stream().anyMatch(versionId -> versionId == null || versionId <= 0)) {
            throw new IllegalArgumentException("档案版本 ID 必须为正数");
        }
        if (new HashSet<>(request.versionIds()).size() != request.versionIds().size()) {
            throw new IllegalArgumentException("授权申请不能包含重复档案版本");
        }
    }

    /**
     * 请假中（ON_LEAVE）员工的人事档案已封存：禁止实物外借（BORROW）与移交（HANDOVER），
     * 系统内查阅（READ）不受影响。employeeMapper 未注入时（如轻量单测）跳过该校验。
     */
    private void requireOwnerNotOnLeaveForPhysicalCheckout(String useType, ArchiveOcrSource source) {
        if (employeeMapper == null || source.employeeId() == null) {
            return;
        }
        String normalized = useType == null ? "" : useType.trim().toUpperCase(Locale.ROOT);
        if (!"BORROW".equals(normalized) && !"HANDOVER".equals(normalized)) {
            return;
        }
        Employee owner = employeeMapper.findByIdWithScope(source.employeeId(), null, null, DataScope.Type.ALL.name());
        if (owner != null && owner.status() == EmployeeStatus.ON_LEAVE) {
            throw new ArchiveAccessStateException("请假中员工的档案已封存，暂不可借出");
        }
    }

    @Transactional
    public ArchiveAccessApplication submit(Long id, AccessActor actor) {
        ArchiveAccessApplication application = require(id, actor);
        requireApplicant(application, actor);
        ArchiveAccessApplication updated = transition(application, ArchiveAccessStateMachine.submit(application.status()));
        record(actor.userId(), "SUBMIT", id, application, updated);
        return updated;
    }

    @Transactional
    public ArchiveAccessApplication approve(Long id, AccessActor actor) {
        ArchiveAccessApplication application = require(id, actor);
        if (!actor.roles().contains("HR_ADMIN")) {
            throw new ArchiveAccessStateException("只有人事管理员可以审批档案访问");
        }
        ArchiveAccessApplication updated = transition(application, ArchiveAccessStateMachine.approve(application.status()));
        insertDecision(id, actor.userId(), "APPROVED", null);
        record(actor.userId(), "APPROVE", id, application, updated);
        return updated;
    }

    @Transactional
    public ArchiveAccessApplication reject(Long id, String reason, AccessActor actor) {
        ArchiveAccessApplication application = require(id, actor);
        if (!actor.roles().contains("HR_ADMIN")) {
            throw new ArchiveAccessStateException("只有人事管理员可以驳回档案访问");
        }
        String note = reason == null ? "" : reason.trim();
        if (note.isBlank()) {
            throw new ArchiveAccessStateException("驳回原因不能为空");
        }
        if (note.length() > 512) {
            throw new ArchiveAccessStateException("驳回原因不能超过 512 个字符");
        }
        ArchiveAccessApplication updated = transition(application,
                ArchiveAccessStateMachine.reject(application.status()));
        insertDecision(id, actor.userId(), "REJECTED", note);
        record(actor.userId(), "REJECT", id, application, updated);
        return updated;
    }

    @Transactional
    public ArchiveAccessApplication use(Long id, AccessActor actor) {
        ArchiveAccessApplication application = require(id, actor);
        requireApplicantOrHr(application, actor);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(application.startAt())) {
            throw new ArchiveAccessStateException("档案授权尚未开始");
        }
        if (now.isAfter(application.dueAt())) {
            throw new ArchiveAccessStateException("档案授权已过期");
        }
        ArchiveAccessApplication updated = transition(application, ArchiveAccessStateMachine.use(application.status()));
        if (mapper.insertUse(new ArchiveUseRecord(null, id, actor.userId(), now, null, null, null,
                null, null)) != 1) {
            throw new ArchiveAccessStateException("档案使用记录创建失败");
        }
        record(actor.userId(), "USE", id, application, updated);
        return updated;
    }

    @Transactional
    public ArchiveAccessApplication returnAccess(Long id, AccessActor actor, boolean abnormal, String remark) {
        return returnAccess(id, actor, abnormal, remark, null);
    }

    @Transactional
    public ArchiveAccessApplication returnAccess(Long id, AccessActor actor, boolean abnormal, String remark,
                                                 Long handoverToEmployeeId) {
        ArchiveAccessApplication application = require(id, actor);
        requireApplicantOrHr(application, actor);
        ArchiveUseRecord active = mapper.findActiveUse(id);
        if (active == null) {
            throw new ArchiveAccessStateException("档案没有进行中的使用记录");
        }
        boolean overdue = LocalDateTime.now().isAfter(application.dueAt());
        boolean effectiveAbnormal = abnormal || overdue;
        if (effectiveAbnormal && (remark == null || remark.isBlank())) {
            throw new ArchiveAccessStateException("异常归还必须填写异常说明");
        }
        boolean handover = "HANDOVER".equals(application.useType());
        if (handover && !effectiveAbnormal && handoverToEmployeeId == null) {
            throw new ArchiveAccessStateException("移交档案必须指定接收员工");
        }
        if (!handover && handoverToEmployeeId != null) {
            throw new ArchiveAccessStateException("只有移交申请可以指定接收员工");
        }
        if (abnormal && handoverToEmployeeId != null) {
            throw new ArchiveAccessStateException("异常归还不能登记接收员工");
        }
        if (!effectiveAbnormal && handoverToEmployeeId != null
                && !mapper.isActiveEmployeeInDepartment(handoverToEmployeeId, application.departmentId())) {
            throw new ArchiveAccessStateException("接收员工不存在、未在职或不属于申请部门");
        }
        ArchiveAccessApplication updated = transition(application,
                ArchiveAccessStateMachine.returnState(application.status(), effectiveAbnormal));
        if (mapper.closeUse(new ArchiveUseRecord(active.id(), id, active.operatorId(), active.usedAt(),
                LocalDateTime.now(), effectiveAbnormal ? "ABNORMAL" : handover ? "HANDED_OVER" : "RETURNED",
                remark, actor.userId(), effectiveAbnormal ? null : handoverToEmployeeId)) != 1) {
            throw new ArchiveAccessStateException("档案使用记录已被其他操作关闭，请刷新后重试");
        }
        record(actor.userId(), "RETURN", id, application, updated);
        return updated;
    }

    @Transactional
    public ArchiveAccessApplication resolveAbnormal(Long id, AccessActor actor, String remark) {
        ArchiveAccessApplication application = require(id, actor);
        if (!actor.roles().contains("HR_ADMIN")) {
            throw new ArchiveAccessStateException("只有人事管理员可以关闭档案访问异常");
        }
        ArchiveAccessStateMachine.resolveAbnormal(application.status());
        ArchiveUseRecord active = mapper.findActiveUse(id);
        if (active != null) {
            throw new ArchiveAccessStateException("请先登记档案归还，再由人事关闭异常");
        }
        String resolutionRemark = remark == null ? "" : remark.trim();
        if (resolutionRemark.isBlank()) {
            throw new ArchiveAccessStateException("异常处理说明不能为空");
        }
        if (resolutionRemark.length() > 512) {
            throw new ArchiveAccessStateException("异常处理说明不能超过 512 个字符");
        }
        ArchiveAccessApplication updated = transition(application, "RETURNED");
        ArchiveAccessResolution resolution = new ArchiveAccessResolution(null, id, actor.userId(), null,
                resolutionRemark, LocalDateTime.now());
        if (mapper.insertResolution(resolution) != 1) {
            throw new ArchiveAccessStateException("档案访问异常处理记录保存失败");
        }
        record(actor.userId(), "RESOLVE_ABNORMAL", id, application, updated);
        return updated;
    }

    @Transactional
    public int expireOverdueInUseApplications() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> overdueIds = mapper.listOverdueInUseIds(now, OVERDUE_BATCH_SIZE);
        int changed = 0;
        for (Long applicationId : overdueIds) {
            if (mapper.markOverdueInUse(applicationId, now) == 1) {
                changed++;
                record(null, "OVERDUE", applicationId, "status=IN_USE", "status=OVERDUE");
            }
        }
        return changed;
    }

    @Transactional
    public int expireUnusedApprovedApplications() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> expiredIds = mapper.listExpiredApprovedIds(now, OVERDUE_BATCH_SIZE);
        int changed = 0;
        for (Long applicationId : expiredIds) {
            if (mapper.markApprovedExpired(applicationId, now) == 1) {
                changed++;
                record(null, "EXPIRE_UNUSED", applicationId, "status=APPROVED", "status=EXPIRED");
            }
        }
        return changed;
    }

    @Transactional(readOnly = true)
    public ArchiveAccessDetail detail(Long id, AccessActor actor) {
        ArchiveAccessApplication application = require(id, actor);
        ArchiveAccessDisplayContext display = mapper.findDisplayContext(id);
        return new ArchiveAccessDetail(application,
                display == null ? null : display.applicantName(),
                display == null ? null : display.departmentName(),
                mapper.listDetailItems(id), mapper.listUseDetails(id), mapper.listResolutions(id),
                mapper.listDecisions(id));
    }

    @Transactional(readOnly = true)
    public AccessPage list(String status, Long applicantId, int page, int pageSize, AccessActor actor) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        DataScope scope = actor.scope();
        Long scopedApplicant = scope.type() == DataScope.Type.EMPLOYEE ? actor.userId() : applicantId;
        Long scopedDepartment = scope.type() == DataScope.Type.DEPARTMENT ? scope.departmentId() : null;
        return new AccessPage(mapper.listApplications(scopedApplicant, status, scopedDepartment,
                scope.type().name(), scope.employeeId(), (safePage - 1) * safeSize, safeSize),
                mapper.countApplications(scopedApplicant, status, scopedDepartment, scope.type().name(),
                        scope.employeeId()),
                safePage, safeSize);
    }

    private ArchiveAccessApplication require(Long id, AccessActor actor) {
        ArchiveAccessApplication application = mapper.findApplication(id, actor.scope().employeeId(),
                actor.scope().departmentId(), actor.scope().type().name());
        if (application == null) {
            throw new ArchiveAccessStateException("档案访问申请不存在或无权访问: " + id);
        }
        return application;
    }

    private ArchiveAccessApplication transition(ArchiveAccessApplication current, String next) {
        ArchiveAccessApplication updated = current.withState(next, current.versionNo() + 1);
        if (mapper.updateState(updated) != 1) {
            throw new ArchiveAccessStateException("访问申请版本已变化，请刷新后重试");
        }
        return updated;
    }

    private void insertDecision(Long applicationId, Long userId, String decision, String note) {
        if (mapper.insertDecision(new ArchiveAccessDecision(null, applicationId, userId, null,
                decision, note, LocalDateTime.now())) != 1) {
            throw new ArchiveAccessStateException("档案访问审批记录保存失败");
        }
    }

    private void requireApplicant(ArchiveAccessApplication application, AccessActor actor) {
        if (!application.applicantId().equals(actor.userId())) {
            throw new ArchiveAccessStateException("只有申请人可以提交档案访问申请");
        }
    }

    private void requireApplicantOrHr(ArchiveAccessApplication application, AccessActor actor) {
        if (!application.applicantId().equals(actor.userId())
                && !actor.roles().contains("HR_ADMIN")) {
            throw new ArchiveAccessStateException("只有申请人或人事管理员可以操作档案使用记录");
        }
    }

    private void record(Long operatorId, String action, Long applicationId) {
        record(operatorId, action, applicationId, (String) null, null);
    }

    private void record(Long operatorId, String action, Long applicationId,
                        ArchiveAccessApplication before, ArchiveAccessApplication after) {
        record(operatorId, action, applicationId,
                before == null ? null : auditSummary(before), after == null ? null : auditSummary(after));
    }

    private void record(Long operatorId, String action, Long applicationId,
                        String beforeSummary, String afterSummary) {
        if (operationLogService != null) {
            operationLogService.record(operatorId, action, "ARCHIVE_ACCESS_APPLICATION", applicationId, "SUCCESS",
                    beforeSummary, afterSummary);
        }
    }

    private String auditSummary(ArchiveAccessApplication application) {
        return "status=" + application.status() + ";useType=" + application.useType()
                + ";version=" + application.versionNo();
    }
}
