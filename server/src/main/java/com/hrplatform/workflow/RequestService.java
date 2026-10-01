package com.hrplatform.workflow;

import com.hrplatform.common.security.DataScope;
import com.hrplatform.employee.EmployeeMapper;
import com.hrplatform.employee.EmployeeMutation;
import com.hrplatform.employee.EmployeeStatus;
import com.hrplatform.employee.Employee;
import com.hrplatform.employee.EmployeeStatusHistory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.List;

@Service
public class RequestService {
    private static final DateTimeFormatter REQUEST_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final RequestMapper mapper;
    private final EmployeeMapper employeeMapper;

    @Autowired
    public RequestService(RequestMapper mapper, EmployeeMapper employeeMapper) {
        this.mapper = mapper;
        this.employeeMapper = employeeMapper;
    }

    @Transactional
    public HrRequest create(RequestCreateRequest request, RequestActor actor) {
        requireType(request.requestType());
        if (actor.scope().type() == DataScope.Type.NONE) {
            throw new RequestStateException("当前用户没有创建人事申请的数据范围");
        }
        LocalDateTime now = LocalDateTime.now();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        HrRequest draft = new HrRequest(null,
                "HR-" + REQUEST_TIME.format(now) + "-" + suffix,
                request.requestType().trim().toUpperCase(), request.employeeId(),
                request.targetDepartmentId(), request.targetPositionId(), actor.userId(),
                request.sourceVersionId(), request.payloadJson(), "DRAFT", null, 0,
                now, null, null);
        HrRequest saved = mapper.insert(draft);
        return saved == null ? draft : saved;
    }

    @Transactional
    public HrRequest submit(Long id, RequestActor actor) {
        HrRequest request = require(id, actor);
        String status = RequestStateMachine.submit(request.status());
        return persistState(request, status, "DEPARTMENT", actor.userId(), "SUBMITTED", null);
    }

    @Transactional
    public HrRequest approve(Long id, RequestActor actor, String comment) {
        HrRequest request = require(id, actor);
        authorizeNode(request, actor);
        String nextStatus = RequestStateMachine.approve(request.status(), request.currentNode(), actor.roles());
        String nextNode = "PENDING_HR_APPROVAL".equals(nextStatus) ? "HR" : null;
        HrRequest updated = persistState(request, nextStatus, nextNode, actor.userId(), "APPROVED", comment);
        if ("APPROVED".equals(nextStatus)) {
            applyEmployeeChange(request);
        }
        return updated;
    }

    @Transactional
    public HrRequest reject(Long id, RequestActor actor, String reason) {
        HrRequest request = require(id, actor);
        authorizeNode(request, actor);
        String nextStatus = RequestStateMachine.reject(request.status());
        return persistState(request, nextStatus, null, actor.userId(), "REJECTED", reason);
    }

    @Transactional(readOnly = true)
    public HrRequest get(Long id, RequestActor actor) {
        return require(id, actor);
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

    private void authorizeNode(HrRequest request, RequestActor actor) {
        if ("DEPARTMENT".equals(request.currentNode())) {
            if (actor.scope().type() == DataScope.Type.DEPARTMENT
                    && !actor.scope().departmentId().equals(request.targetDepartmentId())) {
                throw new RequestStateException("不能审批其他部门的人事申请");
            }
            if (!actor.roles().contains("DEPT_MANAGER") && !actor.roles().contains("SYSTEM_ADMIN")) {
                throw new RequestStateException("当前用户不能进行部门审批");
            }
        } else if ("HR".equals(request.currentNode())) {
            if (!actor.roles().contains("HR_ADMIN") && !actor.roles().contains("SYSTEM_ADMIN")) {
                throw new RequestStateException("当前用户不能进行人事审批");
            }
        }
    }

    private void applyEmployeeChange(HrRequest request) {
        Employee before = employeeMapper.findByIdWithScope(request.employeeId(), null, null, DataScope.Type.ALL.name());
        EmployeeStatus status = switch (request.requestType()) {
            case "ONBOARDING" -> EmployeeStatus.ACTIVE;
            case "OFFBOARDING" -> EmployeeStatus.INACTIVE;
            default -> EmployeeStatus.ACTIVE;
        };
        LocalDate hireDate = "ONBOARDING".equals(request.requestType()) ? LocalDate.now() : null;
        LocalDate leaveDate = "OFFBOARDING".equals(request.requestType()) ? LocalDate.now() : null;
        EmployeeMutation mutation = new EmployeeMutation(request.employeeId(),
                request.targetDepartmentId(), request.targetPositionId(), status, hireDate, leaveDate);
        employeeMapper.updateStatusAndOrganization(mutation);
        employeeMapper.insertStatusHistory(new EmployeeStatusHistory(request.employeeId(),
                before == null || before.status() == null ? null : before.status().name(), status.name(),
                before == null ? null : before.departmentId(), mutation.departmentId(),
                before == null ? null : before.positionId(), mutation.positionId(), request.id(),
                request.applicantId(), LocalDateTime.now()));
    }

    private void requireType(String type) {
        if (type == null || !("ONBOARDING".equalsIgnoreCase(type)
                || "TRANSFER".equalsIgnoreCase(type) || "OFFBOARDING".equalsIgnoreCase(type))) {
            throw new IllegalArgumentException("requestType 必须是 ONBOARDING、TRANSFER 或 OFFBOARDING");
        }
    }
}
