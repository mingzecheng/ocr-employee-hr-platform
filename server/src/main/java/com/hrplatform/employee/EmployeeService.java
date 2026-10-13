package com.hrplatform.employee;

import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.organization.OrganizationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class EmployeeService {
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");

    private final EmployeeMapper mapper;
    private final OrganizationMapper organizationMapper;
    private final OperationLogService operationLogService;

    @org.springframework.beans.factory.annotation.Autowired
    public EmployeeService(EmployeeMapper mapper, OrganizationMapper organizationMapper,
                           OperationLogService operationLogService) {
        this.mapper = mapper;
        this.organizationMapper = organizationMapper;
        this.operationLogService = operationLogService;
    }

    public EmployeeService(EmployeeMapper mapper, OperationLogService operationLogService) {
        this(mapper, null, operationLogService);
    }

    @Transactional
    public Employee create(EmployeeCreateRequest request, Long operatorId) {
        if (StringUtils.hasText(request.phone()) && !PHONE.matcher(request.phone()).matches()) {
            throw new IllegalArgumentException("联系电话格式错误");
        }
        if (organizationMapper != null
                && !organizationMapper.isEnabledPosition(request.positionId(), request.departmentId())) {
            throw new IllegalArgumentException("所选岗位不存在、已停用或不属于所选部门");
        }
        String employeeNo = generateEmployeeNo();
        int inserted = mapper.insert(new Employee(
                null, employeeNo, request.name(), request.phone(), request.departmentId(),
                request.positionId(), request.status(), request.hireDate(), null
        ));
        if (inserted != 1) {
            throw new IllegalStateException("员工档案创建失败");
        }
        Employee employee = mapper.findByEmployeeNo(employeeNo);
        if (employee == null) {
            throw new IllegalStateException("员工档案已写入但无法读取");
        }
        operationLogService.record(operatorId, "CREATE", "EMPLOYEE", employee.id(), "SUCCESS");
        return employee;
    }

    private String generateEmployeeNo() {
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 20).toUpperCase(Locale.ROOT);
        return "EMP-" + LocalDate.now(ZoneId.of("Asia/Shanghai")).getYear() + "-" + suffix;
    }

    public Employee getRequired(Long employeeId, DataScope scope) {
        Employee employee = mapper.findByIdWithScope(employeeId, scope.employeeId(), scope.departmentId(), scope.type().name());
        if (employee == null) {
            throw new DataScopeDeniedException(employeeId);
        }
        return employee;
    }

    @Transactional
    public Employee updateProfile(Long employeeId, EmployeeProfileUpdateRequest request, Long operatorId) {
        String name = request.name() == null ? "" : request.name().strip();
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("姓名不能为空");
        }
        if (name.length() > 128) {
            throw new IllegalArgumentException("姓名不能超过 128 个字符");
        }
        String phone = request.phone() == null ? null : request.phone().strip();
        if (!StringUtils.hasText(phone)) {
            phone = null;
        } else if (!PHONE.matcher(phone).matches()) {
            throw new IllegalArgumentException("联系电话格式错误");
        }

        Employee current = mapper.findByIdWithScope(employeeId, null, null, DataScope.Type.ALL.name());
        if (current == null) {
            throw new DataScopeDeniedException(employeeId);
        }
        if (Objects.equals(current.name(), name) && Objects.equals(current.phone(), phone)) {
            operationLogService.record(operatorId, "UPDATE", "EMPLOYEE", employeeId, "SUCCESS");
            return current;
        }
        int changed = mapper.updateProfile(new EmployeeProfileMutation(employeeId, name, phone));
        if (changed != 1) {
            throw new IllegalStateException("员工档案更新失败");
        }
        Employee updated = mapper.findByIdWithScope(employeeId, null, null, DataScope.Type.ALL.name());
        if (updated == null) {
            throw new IllegalStateException("员工档案已更新但无法读取");
        }
        operationLogService.record(operatorId, "UPDATE", "EMPLOYEE", employeeId, "SUCCESS");
        return updated;
    }

    @Transactional(readOnly = true)
    public EmployeePage list(String keyword, Long departmentId, EmployeeStatus status,
                             int page, int pageSize, DataScope scope) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        Long scopedDepartment = scope.type() == DataScope.Type.DEPARTMENT ? scope.departmentId() : departmentId;
        Long scopedEmployee = scope.type() == DataScope.Type.EMPLOYEE ? scope.employeeId() : null;
        List<Employee> items = mapper.list(keyword, status == null ? null : status.name(), scopedDepartment,
                scopedEmployee, scope.type().name(), (safePage - 1) * safeSize, safeSize);
        long total = mapper.count(keyword, status == null ? null : status.name(), scopedDepartment,
                scopedEmployee, scope.type().name());
        return new EmployeePage(items, total, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public EmployeeStatusHistoryPage listStatusHistory(Long employeeId, int page, int pageSize, DataScope scope) {
        if (mapper.findByIdWithScope(employeeId, scope.employeeId(), scope.departmentId(), scope.type().name()) == null) {
            throw new DataScopeDeniedException(employeeId);
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        List<EmployeeStatusHistoryView> items = mapper.listStatusHistory(employeeId, scope.employeeId(),
                scope.departmentId(), scope.type().name(), (safePage - 1) * safeSize, safeSize);
        long total = mapper.countStatusHistory(employeeId, scope.employeeId(), scope.departmentId(), scope.type().name());
        return new EmployeeStatusHistoryPage(items, total, safePage, safeSize);
    }
}
