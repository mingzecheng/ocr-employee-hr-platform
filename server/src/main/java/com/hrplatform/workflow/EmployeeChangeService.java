package com.hrplatform.workflow;

import com.hrplatform.auth.SysUserMapper;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.employee.Employee;
import com.hrplatform.employee.EmployeeMapper;
import com.hrplatform.employee.EmployeeMutation;
import com.hrplatform.employee.EmployeeStatus;
import com.hrplatform.employee.EmployeeStatusHistory;
import com.hrplatform.organization.OrganizationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Service
public class EmployeeChangeService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final RequestMapper requestMapper;
    private final EmployeeMapper employeeMapper;
    private final SysUserMapper sysUserMapper;
    private final OrganizationMapper organizationMapper;

    @Autowired
    public EmployeeChangeService(RequestMapper requestMapper, EmployeeMapper employeeMapper,
                                 SysUserMapper sysUserMapper, OrganizationMapper organizationMapper) {
        this.requestMapper = requestMapper;
        this.employeeMapper = employeeMapper;
        this.sysUserMapper = sysUserMapper;
        this.organizationMapper = organizationMapper;
    }

    public EmployeeChangeService(RequestMapper requestMapper, EmployeeMapper employeeMapper) {
        this(requestMapper, employeeMapper, null, null);
    }

    @Transactional
    public LocalDateTime applyApprovedChange(HrRequest request, Long operatorId) {
        if (!applyEmployeeChange(request, operatorId)) {
            return null;
        }
        LocalDateTime appliedAt = LocalDateTime.now();
        if (requestMapper.markEmployeeChangeApplied(request.id(), appliedAt) != 1) {
            throw new RequestStateException("员工变更执行时间写入失败");
        }
        return appliedAt;
    }

    public void lockEmployeeForApproval(HrRequest request) {
        Employee employee = employeeMapper.lockById(request.employeeId());
        if (employee == null) {
            employee = employeeMapper.findByIdWithScope(request.employeeId(), null, null, DataScope.Type.ALL.name());
        }
        if (employee == null) {
            throw new RequestStateException("员工不存在，无法审批人事变更");
        }
        if (requestMapper.countOpenEmployeeChanges(request.employeeId(), request.id()) > 0) {
            throw new RequestStateException("员工已有其他未完成的人事变更申请，请先处理完成");
        }
    }

    @Transactional
    public boolean applyDueEmployeeChange(Long requestId) {
        LocalDate effectiveOn = LocalDate.now(BUSINESS_ZONE);
        if (!Objects.equals(requestId, requestMapper.lockDueEmployeeChange(requestId, effectiveOn))) {
            return false;
        }
        HrRequest request = requestMapper.findByIdWithScope(requestId, null, null, DataScope.Type.ALL.name());
        return request != null && applyApprovedChange(request, null) != null;
    }

    /**
     * 请假申请 HR 审批通过后，把请假起止日期写为员工状态自动切换窗口。
     * 幂等：mapper 层带 effective_from IS NULL 条件，重复审批/重复调用不会覆盖已写入窗口。
     */
    public void markLeaveEffectiveWindow(HrRequest request) {
        if (request == null || !"LEAVE".equals(request.requestType())) {
            return;
        }
        if (request.leaveStartDate() == null || request.leaveEndDate() == null) {
            return;
        }
        requestMapper.markLeaveEffectiveWindow(request.id(), request.leaveStartDate(), request.leaveEndDate());
    }

    /**
     * 调度器按日调用：根据今日日期把员工状态在 ACTIVE/ON_LEAVE 之间切换，并写 employee_status_history。
     * 幂等：员工当前状态已是目标状态时直接返回，不重复写历史；销假前还会校验是否有其他重叠请假仍在生效。
     *
     * @return true 表示本次发生了状态切换
     */
    @Transactional
    public boolean syncLeaveStatus(LeaveStatusWindow window, LocalDate today) {
        Employee before = employeeMapper.lockById(window.employeeId());
        if (before == null) {
            return false;
        }
        boolean withinWindow = !today.isBefore(window.effectiveFrom()) && !today.isAfter(window.effectiveTo());
        if (!withinWindow && today.isBefore(window.effectiveFrom())) {
            // 窗口尚未开始，不处理
            return false;
        }
        EmployeeStatus target = withinWindow ? EmployeeStatus.ON_LEAVE : EmployeeStatus.ACTIVE;
        if (before.status() == target) {
            // 幂等：已是目标状态，重复执行不重复写历史
            return false;
        }
        if (target == EmployeeStatus.ACTIVE
                && requestMapper.countOtherActiveLeaveWindows(window.employeeId(), today, window.requestId()) > 0) {
            // 还有其他已批准请假覆盖今天，保持 ON_LEAVE，不提前销假
            return false;
        }
        EmployeeMutation mutation = new EmployeeMutation(window.employeeId(),
                before.departmentId(), before.positionId(), target, before.hireDate(), before.leaveDate());
        if (employeeMapper.updateStatusAndOrganization(mutation) != 1) {
            throw new RequestStateException("员工状态已变化或员工不存在");
        }
        employeeMapper.insertStatusHistory(new EmployeeStatusHistory(window.employeeId(),
                before.status() == null ? null : before.status().name(), target.name(),
                before.departmentId(), before.departmentId(), before.positionId(), before.positionId(),
                window.requestId(), null, LocalDateTime.now()));
        return true;
    }

    private boolean applyEmployeeChange(HrRequest request, Long operatorId) {
        LocalDate effectiveDate = switch (request.requestType()) {
            case "ONBOARDING", "TRANSFER" -> request.effectiveDate();
            case "OFFBOARDING" -> request.leaveDate();
            default -> null;
        };
        if (effectiveDate == null || effectiveDate.isAfter(LocalDate.now(BUSINESS_ZONE))) {
            return false;
        }
        Employee before = employeeMapper.lockById(request.employeeId());
        if (before == null) {
            before = employeeMapper.findByIdWithScope(request.employeeId(), null, null, DataScope.Type.ALL.name());
        }
        if (before == null) {
            throw new RequestStateException("员工不存在，无法执行已审批的人事变更");
        }
        if (requestMapper.hasEarlierUnappliedEmployeeChange(request.employeeId(), effectiveDate, request.id())) {
            return false;
        }
        if ("ONBOARDING".equals(request.requestType()) && before.status() != EmployeeStatus.PENDING_ONBOARD) {
            throw new RequestStateException("员工状态已变化，不能执行入职变更");
        }
        if ("TRANSFER".equals(request.requestType()) && before.status() != EmployeeStatus.ACTIVE) {
            throw new RequestStateException("员工当前已非在职状态，不能执行调动");
        }
        if ("TRANSFER".equals(request.requestType())) {
            TransferDetail transfer = requestMapper.findTransferDetail(request.id());
            if (transfer != null && (!Objects.equals(transfer.fromDepartmentId(), before.departmentId())
                    || !Objects.equals(transfer.fromPositionId(), before.positionId()))) {
                throw new RequestStateException("员工组织信息已变化，调动申请的原部门或岗位与当前档案不一致");
            }
        }
        if ("OFFBOARDING".equals(request.requestType()) && before.status() != EmployeeStatus.ACTIVE
                && before.status() != EmployeeStatus.ON_LEAVE) {
            throw new RequestStateException("员工状态已变化，不能执行离职变更");
        }
        EmployeeStatus status = switch (request.requestType()) {
            case "ONBOARDING" -> EmployeeStatus.ACTIVE;
            case "OFFBOARDING" -> EmployeeStatus.INACTIVE;
            default -> before.status();
        };
        Long departmentId = "OFFBOARDING".equals(request.requestType()) || request.targetDepartmentId() == null
                ? before.departmentId() : request.targetDepartmentId();
        Long positionId = "OFFBOARDING".equals(request.requestType()) || request.targetPositionId() == null
                ? before.positionId() : request.targetPositionId();
        LocalDate hireDate = "ONBOARDING".equals(request.requestType()) ? request.effectiveDate() : null;
        LocalDate leaveDate = "OFFBOARDING".equals(request.requestType()) ? request.leaveDate() : null;
        EmployeeMutation mutation = new EmployeeMutation(request.employeeId(), departmentId, positionId, status,
                hireDate, leaveDate);
        if (employeeMapper.updateStatusAndOrganization(mutation) != 1) {
            throw new RequestStateException("员工状态已变化或员工不存在");
        }
        if ("TRANSFER".equals(request.requestType()) && sysUserMapper != null && organizationMapper != null) {
            if (sysUserMapper.updateDepartmentForEmployee(request.employeeId(), departmentId) > 1) {
                throw new RequestStateException("员工关联了多个账号，无法安全同步组织信息");
            }
            if (!Objects.equals(before.departmentId(), departmentId)) {
                organizationMapper.clearManagerAssignmentForEmployee(request.employeeId());
                sysUserMapper.deleteUserRoleForEmployee(request.employeeId(), "DEPT_MANAGER");
            }
        }
        employeeMapper.insertStatusHistory(new EmployeeStatusHistory(request.employeeId(),
                before.status() == null ? null : before.status().name(), status.name(),
                before.departmentId(), departmentId, before.positionId(), positionId, request.id(),
                operatorId, LocalDateTime.now()));
        return true;
    }
}
