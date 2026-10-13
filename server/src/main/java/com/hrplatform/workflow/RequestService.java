package com.hrplatform.workflow;

import com.hrplatform.common.security.DataScope;
import com.hrplatform.common.idempotency.IdempotentReplayException;
import com.hrplatform.common.idempotency.IdempotencyService;
import com.hrplatform.audit.OperationLogService;
import com.hrplatform.archive.ArchiveCatalog;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.employee.EmployeeMapper;
import com.hrplatform.employee.EmployeeStatus;
import com.hrplatform.employee.Employee;
import com.hrplatform.organization.OrganizationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Objects;

@Service
public class RequestService {
    private static final DateTimeFormatter REQUEST_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final RequestMapper mapper;
    private final EmployeeMapper employeeMapper;
    private final OrganizationMapper organizationMapper;
    private final OperationLogService operationLogService;
    private final EmployeeChangeService employeeChangeService;
    private final ArchiveCatalog archiveCatalog;
    private final IdempotencyService idempotencyService;

    @Autowired
    public RequestService(RequestMapper mapper, EmployeeMapper employeeMapper, OrganizationMapper organizationMapper,
                          OperationLogService operationLogService, EmployeeChangeService employeeChangeService,
                          ArchiveCatalog archiveCatalog) {
        this(mapper, employeeMapper, organizationMapper, operationLogService, employeeChangeService,
                archiveCatalog, null);
    }

    public RequestService(RequestMapper mapper, EmployeeMapper employeeMapper, OrganizationMapper organizationMapper,
                          OperationLogService operationLogService, EmployeeChangeService employeeChangeService,
                          ArchiveCatalog archiveCatalog, IdempotencyService idempotencyService) {
        this.mapper = mapper;
        this.employeeMapper = employeeMapper;
        this.organizationMapper = organizationMapper;
        this.operationLogService = operationLogService;
        this.employeeChangeService = employeeChangeService;
        this.archiveCatalog = archiveCatalog;
        this.idempotencyService = idempotencyService;
    }

    public RequestService(RequestMapper mapper, EmployeeMapper employeeMapper, OrganizationMapper organizationMapper,
                          OperationLogService operationLogService) {
        this(mapper, employeeMapper, organizationMapper, operationLogService,
                new EmployeeChangeService(mapper, employeeMapper), null, null);
    }

    public RequestService(RequestMapper mapper, EmployeeMapper employeeMapper) {
        this(mapper, employeeMapper, null, null);
    }

    public RequestService(RequestMapper mapper, EmployeeMapper employeeMapper, OperationLogService operationLogService) {
        this(mapper, employeeMapper, null, operationLogService);
    }

    @Transactional
    public HrRequest create(RequestCreateRequest request, RequestActor actor) {
        String requestType = request.requestType().trim().toUpperCase(Locale.ROOT);
        requireType(requestType);
        if (actor.scope().type() == DataScope.Type.NONE) {
            throw new RequestStateException("当前用户没有创建人事申请的数据范围");
        }
        boolean employeeChangeRequest = isEmployeeChangeRequest(requestType);
        Employee employee = employeeChangeRequest
                ? employeeMapper.findByIdWithScopeForUpdate(request.employeeId(), actor.scope().employeeId(),
                        actor.scope().departmentId(), actor.scope().type().name())
                : employeeMapper.findByIdWithScope(request.employeeId(), actor.scope().employeeId(),
                        actor.scope().departmentId(), actor.scope().type().name());
        if (employee == null && employeeChangeRequest) {
            employee = employeeMapper.findByIdWithScope(request.employeeId(), actor.scope().employeeId(),
                    actor.scope().departmentId(), actor.scope().type().name());
        }
        if (employee == null) {
            throw new RequestStateException("员工不存在或当前用户无权访问该员工");
        }
        validateSourceVersion(request.sourceVersionId(), employee.id(), actor.scope());
        if (employeeChangeRequest && mapper.countOpenEmployeeChanges(employee.id(), null) > 0) {
            throw new RequestStateException("员工已有未完成的人事变更申请，请先处理完成");
        }
        if ("ONBOARDING".equals(requestType) && employee.status() != EmployeeStatus.PENDING_ONBOARD) {
            throw new RequestStateException("只有待入职员工可以提交入职申请");
        }
        if ("OFFBOARDING".equals(requestType) && employee.status() != EmployeeStatus.ACTIVE) {
            throw new RequestStateException("只有在职员工可以提交离职申请，请假期间请先销假返岗");
        }
        if ("LEAVE".equals(requestType) && employee.status() != EmployeeStatus.ACTIVE
                && employee.status() != EmployeeStatus.ON_LEAVE) {
            throw new RequestStateException("只有在职或休假员工可以提交请假申请");
        }
        validateDetails(request);
        RequestDraftDetails details = requestDetails(request);
        if ("TRANSFER".equals(requestType) && employee.status() != EmployeeStatus.ACTIVE) {
            throw new RequestStateException("只有在职员工可以申请调动");
        }
        if (organizationMapper != null && ("ONBOARDING".equals(requestType) || "TRANSFER".equals(requestType))
                && !organizationMapper.isEnabledPosition(details.targetPositionId(), details.targetDepartmentId())) {
            throw new IllegalArgumentException("目标岗位不存在、已停用或不属于目标部门");
        }
        Long approvalDepartmentId = "ONBOARDING".equals(requestType) && details.targetDepartmentId() != null
                ? details.targetDepartmentId() : employee.departmentId();
        Long targetPositionId = details.targetPositionId() == null
                ? employee.positionId() : details.targetPositionId();
        LocalDateTime now = LocalDateTime.now();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        HrRequest draft = new HrRequest(null,
                "HR-" + REQUEST_TIME.format(now) + "-" + suffix,
                requestType, request.employeeId(), details.targetDepartmentId(), approvalDepartmentId,
                targetPositionId, actor.userId(),
                request.sourceVersionId(), null, "DRAFT", null, 0,
                now, null, null, details.leaveType(), details.leaveStartDate(), details.leaveEndDate(),
                "LEAVE".equals(requestType) ? details.reason() : null,
                details.effectiveDate(), details.contractType(), details.leaveDate(), details.handoverPerson(),
                "OFFBOARDING".equals(requestType) ? "PENDING" : null, details.reason());
        if (mapper.insert(draft) != 1) {
            throw new RequestStateException("人事申请创建失败");
        }
        if ("LEAVE".equals(requestType)) {
            requireDetailInsert(mapper.insertLeaveDetail(new LeaveRequestDetail(null, draft.requestNo(), details.leaveType(),
                    details.leaveStartDate(), details.leaveEndDate(), details.reason())));
        } else if ("ONBOARDING".equals(requestType)) {
            requireDetailInsert(mapper.insertOnboardingDetail(new OnboardingDetail(draft.requestNo(),
                    details.effectiveDate(), details.contractType())));
        } else if ("TRANSFER".equals(requestType)) {
            requireDetailInsert(mapper.insertTransferDetail(new TransferDetail(draft.requestNo(),
                    employee.departmentId(), employee.positionId(), details.effectiveDate(), details.reason())));
        } else if ("OFFBOARDING".equals(requestType)) {
            requireDetailInsert(mapper.insertOffboardingDetail(new OffboardingDetail(draft.requestNo(),
                    details.leaveDate(), details.handoverPerson(), "PENDING", details.reason())));
        }
        HrRequest result = mapper.findByRequestNo(draft.requestNo());
        if (result == null) {
            throw new RequestStateException("人事申请已写入但无法读取");
        }
        record(actor, "CREATE", result.id(), null, auditSummary(result));
        return result;
    }

    @Transactional
    public HrRequest submit(Long id, RequestActor actor) {
        HrRequest request = require(id, actor);
        if (!request.applicantId().equals(actor.userId()) && !actor.roles().contains("HR_ADMIN")) {
            throw new RequestStateException("只有申请人或人事管理员可以提交该申请");
        }
        String status = RequestStateMachine.submit(request.status());
        HrRequest updated = persistState(request, status, "DEPARTMENT", actor.userId(), "SUBMITTED", null);
        record(actor, "SUBMIT", id, request, updated);
        return updated;
    }

    @Transactional
    public HrRequest cancel(Long id, RequestActor actor) {
        HrRequest request = require(id, actor);
        if (!request.applicantId().equals(actor.userId()) && !actor.roles().contains("HR_ADMIN")) {
            throw new RequestStateException("只有申请人或人事管理员可以取消草稿");
        }
        String status = RequestStateMachine.cancel(request.status());
        HrRequest cancelled = persistState(request, status, null, actor.userId(), "CANCELLED", null);
        record(actor, "CANCEL", id, request, cancelled);
        return cancelled;
    }

    @Transactional
    public HrRequest approve(Long id, RequestActor actor, String comment) {
        HrRequest request = require(id, actor);
        requireIdempotentApproval(actor, id, "approve");
        authorizeNode(request, actor);
        String nextStatus = RequestStateMachine.approve(request.status(), request.currentNode(), actor.roles());
        String nextNode = "PENDING_HR_APPROVAL".equals(nextStatus) ? "HR" : null;
        if ("APPROVED".equals(nextStatus) && isEmployeeChangeRequest(request.requestType())
                && isEffectiveNow(request)) {
            employeeChangeService.lockEmployeeForApproval(request);
        }
        HrRequest updated = persistState(request, nextStatus, nextNode, actor.userId(), "APPROVED", comment);
        if ("APPROVED".equals(nextStatus) && "OFFBOARDING".equals(request.requestType())
                && !mapper.isHandoverComplete(request.id())) {
            throw new RequestStateException("离职交接尚未由人事确认");
        }
        if ("APPROVED".equals(nextStatus) && "LEAVE".equals(request.requestType())) {
            employeeChangeService.markLeaveEffectiveWindow(request);
        }
        if ("APPROVED".equals(nextStatus)) {
            LocalDateTime appliedAt = employeeChangeService.applyApprovedChange(request, actor.userId());
            if (appliedAt != null) {
                updated = updated.withEmployeeChangeAppliedAt(appliedAt);
                record(actor, "APPLY_EMPLOYEE_CHANGE", id, request, updated);
            }
        }
        record(actor, "APPROVE", id, request, updated);
        return updated;
    }

    @Transactional
    public HrRequest reject(Long id, RequestActor actor, String reason) {
        String normalizedReason = normalizeRejectReason(reason);
        HrRequest request = require(id, actor);
        requireIdempotentApproval(actor, id, "reject");
        authorizeNode(request, actor);
        String nextStatus = RequestStateMachine.reject(request.status());
        HrRequest rejected = persistState(request, nextStatus, null, actor.userId(), "REJECTED", normalizedReason);
        record(actor, "REJECT", id, request, rejected);
        return rejected;
    }

    @Transactional
    public HrRequest completeHandover(Long id, RequestActor actor) {
        HrRequest request = require(id, actor);
        if (!actor.roles().contains("HR_ADMIN")) {
            throw new RequestStateException("只有人事管理员可以确认离职交接");
        }
        if (!"OFFBOARDING".equals(request.requestType())
                || !"PENDING_HR_APPROVAL".equals(request.status())) {
            throw new RequestStateException("该申请当前不能确认离职交接");
        }
        if (mapper.completeHandover(id) != 1) {
            throw new RequestStateException("交接状态已变化，请刷新后重试");
        }
        HrRequest updated = require(id, actor);
        record(actor, "COMPLETE_HANDOVER", id, request, updated);
        return updated;
    }

    @Transactional(readOnly = true)
    public HrRequest get(Long id, RequestActor actor) {
        return require(id, actor);
    }

    @Transactional(readOnly = true)
    public RequestDetail detail(Long id, RequestActor actor) {
        HrRequest request = require(id, actor);
        return new RequestDetail(request, mapper.findDisplayContext(id), mapper.listApprovals(id));
    }

    @Transactional(readOnly = true)
    public RequestPage list(String requestType, String status, Long employeeId, int page, int pageSize,
                            RequestActor actor) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        DataScope scope = actor.scope();
        Long scopedEmployee = scope.type() == DataScope.Type.EMPLOYEE ? scope.employeeId() : employeeId;
        Long scopedDepartment = scope.type() == DataScope.Type.DEPARTMENT ? scope.departmentId() : null;
        List<HrRequest> items = mapper.list(requestType, status, scopedEmployee, scopedDepartment,
                scope.type().name(), (safePage - 1) * safeSize, safeSize);
        return new RequestPage(items, mapper.count(requestType, status, scopedEmployee, scopedDepartment,
                scope.type().name()), safePage, safeSize);
    }

    private HrRequest require(Long id, RequestActor actor) {
        DataScope scope = actor.scope();
        HrRequest request = mapper.findByIdWithScope(id, scope.employeeId(), scope.departmentId(), scope.type().name());
        if (request == null) {
            throw new RequestStateException("人事申请不存在或无权访问: " + id);
        }
        return request;
    }

    private HrRequest persistState(HrRequest current, String status, String node, Long operatorId,
                                   String decision, String comment) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime submitted = current.submittedAt();
        if ("SUBMITTED".equals(decision) && submitted == null) {
            submitted = now;
        }
        LocalDateTime completed = "APPROVED".equals(status) || "REJECTED".equals(status)
                || "CANCELLED".equals(status)
                ? now : current.completedAt();
        HrRequest next = current.withState(status, node, current.versionNo() + 1, submitted, completed);
        int updatedRows = mapper.updateState(next);
        if (operatorId != null && ("APPROVED".equals(decision) || "REJECTED".equals(decision))) {
            mapper.insertApproval(new ApprovalRecord(null, current.id(), current.currentNode(), operatorId,
                    decision, comment, now));
        }
        if (updatedRows != 1) {
            throw new RequestStateException("申请版本已变化，请刷新后重试");
        }
        return next;
    }

    private void record(RequestActor actor, String action, Long requestId) {
        record(actor, action, requestId, (String) null, null);
    }

    private void record(RequestActor actor, String action, Long requestId, HrRequest before, HrRequest after) {
        record(actor, action, requestId, before == null ? null : auditSummary(before),
                after == null ? null : auditSummary(after));
    }

    private void record(RequestActor actor, String action, Long requestId,
                        String beforeSummary, String afterSummary) {
        if (operationLogService != null) {
            operationLogService.record(actor.userId(), action, "HR_REQUEST", requestId, "SUCCESS",
                    beforeSummary, afterSummary);
        }
    }

    private String auditSummary(HrRequest request) {
        return "type=" + request.requestType() + ";status=" + request.status()
                + ";node=" + (request.currentNode() == null ? "NONE" : request.currentNode())
                + ";version=" + request.versionNo()
                + ";handover=" + (request.handoverStatus() == null ? "NONE" : request.handoverStatus());
    }

    private static final Duration APPROVE_IDEMPOTENCY_WINDOW = Duration.ofSeconds(10);

    private void requireIdempotentApproval(RequestActor actor, Long requestId, String action) {
        if (idempotencyService == null || actor == null || actor.userId() == null) {
            return;
        }
        String key = "idem:" + action + ":" + actor.userId() + ":" + requestId;
        boolean acquired = idempotencyService.tryAcquire(key, APPROVE_IDEMPOTENCY_WINDOW);
        if (!acquired) {
            throw new IdempotentReplayException("请勿重复提交");
        }
    }

    private void authorizeNode(HrRequest request, RequestActor actor) {
        if ("DEPARTMENT".equals(request.currentNode())) {
            if (!actor.roles().contains("DEPT_MANAGER")) {
                throw new RequestStateException("当前用户不能进行部门审批");
            }
            if (actor.scope().departmentId() == null
                    || !actor.scope().departmentId().equals(request.approvalDepartmentId())) {
                throw new RequestStateException("不能审批其他部门的人事申请");
            }
        } else if ("HR".equals(request.currentNode())) {
            if (!actor.roles().contains("HR_ADMIN")) {
                throw new RequestStateException("当前用户不能进行人事审批");
            }
        }
    }

    @Transactional
    public boolean applyDueEmployeeChange(Long id) {
        boolean applied = employeeChangeService.applyDueEmployeeChange(id);
        if (applied && operationLogService != null) {
            operationLogService.record(null, "APPLY_EMPLOYEE_CHANGE", "HR_REQUEST", id, "SUCCESS");
        }
        return applied;
    }

    private void requireDetailInsert(int inserted) {
        if (inserted != 1) {
            throw new RequestStateException("人事申请明细创建失败");
        }
    }

    private void validateSourceVersion(Long versionId, Long employeeId, DataScope scope) {
        if (versionId == null) {
            return;
        }
        if (archiveCatalog == null) {
            throw new IllegalStateException("档案目录服务未配置");
        }
        ArchiveOcrSource source = archiveCatalog.findOcrSourceByVersionIdWithScope(versionId,
                scope.employeeId(), scope.departmentId(), scope.type().name());
        if (source == null || !Objects.equals(source.employeeId(), employeeId)) {
            throw new IllegalArgumentException("来源档案版本不存在或不属于所选员工");
        }
    }

    private String normalizeRejectReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("驳回原因不能为空");
        }
        String normalized = reason.trim();
        if (normalized.length() > 512) {
            throw new IllegalArgumentException("驳回原因不能超过512个字符");
        }
        return normalized;
    }

    private void requireType(String type) {
        if (type == null || !("ONBOARDING".equalsIgnoreCase(type)
                || "TRANSFER".equalsIgnoreCase(type) || "OFFBOARDING".equalsIgnoreCase(type)
                || "LEAVE".equalsIgnoreCase(type))) {
            throw new IllegalArgumentException("requestType 必须是 ONBOARDING、TRANSFER、LEAVE 或 OFFBOARDING");
        }
    }

    private boolean isEmployeeChangeRequest(String requestType) {
        return "ONBOARDING".equals(requestType) || "TRANSFER".equals(requestType)
                || "OFFBOARDING".equals(requestType);
    }

    private boolean isEffectiveNow(HrRequest request) {
        LocalDate effectiveDate = switch (request.requestType()) {
            case "ONBOARDING", "TRANSFER" -> request.effectiveDate();
            case "OFFBOARDING" -> request.leaveDate();
            default -> null;
        };
        return effectiveDate != null && !effectiveDate.isAfter(LocalDate.now());
    }

    private void validateDetails(RequestCreateRequest request) {
        if (request instanceof RequestCreateRequest.Leave leave) {
            if (leave.leaveType() == null || !Set.of("ANNUAL", "SICK", "PERSONAL", "OTHER")
                    .contains(leave.leaveType().toUpperCase(Locale.ROOT))
                    || leave.leaveStartDate() == null || leave.leaveEndDate() == null
                    || leave.leaveEndDate().isBefore(leave.leaveStartDate())
                    || leave.reason() == null || leave.reason().isBlank()
                    || leave.reason().length() > 512) {
                throw new IllegalArgumentException("请假申请必须填写类型、有效起止日期和原因");
            }
        } else if (request instanceof RequestCreateRequest.Transfer transfer) {
            if (transfer.targetDepartmentId() == null || transfer.targetPositionId() == null) {
                throw new IllegalArgumentException("入职或调动申请必须填写目标部门和岗位");
            }
            if (transfer.effectiveDate() == null) {
                throw new IllegalArgumentException("入职或调动申请必须填写生效日期");
            }
            if (transfer.reason() == null || transfer.reason().isBlank() || transfer.reason().length() > 512) {
                throw new IllegalArgumentException("调动申请必须填写原因");
            }
        } else if (request instanceof RequestCreateRequest.Onboarding onboarding) {
            if (onboarding.targetDepartmentId() == null || onboarding.targetPositionId() == null) {
                throw new IllegalArgumentException("入职或调动申请必须填写目标部门和岗位");
            }
            if (onboarding.effectiveDate() == null) {
                throw new IllegalArgumentException("入职或调动申请必须填写生效日期");
            }
            if (onboarding.contractType() == null
                    || !Set.of("FIXED_TERM", "OPEN_ENDED", "INTERN").contains(onboarding.contractType())
                    || onboarding.contractType().length() > 32) {
                throw new IllegalArgumentException("入职申请合同类型无效");
            }
        } else if (request instanceof RequestCreateRequest.Offboarding offboarding) {
            if (offboarding.leaveDate() == null || offboarding.handoverPerson() == null
                    || offboarding.handoverPerson().isBlank() || offboarding.handoverPerson().length() > 128
                    || offboarding.reason() != null && offboarding.reason().length() > 512) {
                throw new IllegalArgumentException("离职申请必须填写有效离职日期和交接人");
            }
        }
    }

    private RequestDraftDetails requestDetails(RequestCreateRequest request) {
        if (request instanceof RequestCreateRequest.Leave leave) {
            return new RequestDraftDetails(null, null, leave.leaveType(), leave.leaveStartDate(),
                    leave.leaveEndDate(), null, null, null, leave.reason().trim(), null);
        }
        if (request instanceof RequestCreateRequest.Onboarding onboarding) {
            return new RequestDraftDetails(onboarding.targetDepartmentId(), onboarding.targetPositionId(),
                    null, null, null, onboarding.effectiveDate(), onboarding.contractType(), null, null, null);
        }
        if (request instanceof RequestCreateRequest.Transfer transfer) {
            return new RequestDraftDetails(transfer.targetDepartmentId(), transfer.targetPositionId(),
                    null, null, null, transfer.effectiveDate(), null, null, transfer.reason().trim(), null);
        }
        if (request instanceof RequestCreateRequest.Offboarding offboarding) {
            return new RequestDraftDetails(null, null, null, null, null, null, null, offboarding.leaveDate(),
                    offboarding.reason() == null ? null : offboarding.reason().trim(),
                    offboarding.handoverPerson().trim());
        }
        throw new IllegalArgumentException("不支持的人事申请类型");
    }

    private record RequestDraftDetails(Long targetDepartmentId, Long targetPositionId, String leaveType,
                                       LocalDate leaveStartDate, LocalDate leaveEndDate,
                                       LocalDate effectiveDate, String contractType, LocalDate leaveDate,
                                       String reason, String handoverPerson) {
    }
}
