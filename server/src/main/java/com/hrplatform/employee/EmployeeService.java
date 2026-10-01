package com.hrplatform.employee;

import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Service
public class EmployeeService {
    private static final Pattern EMPLOYEE_NO = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{1,31}");
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");

    private final EmployeeMapper mapper;
    private final OperationLogService operationLogService;

    public EmployeeService(EmployeeMapper mapper, OperationLogService operationLogService) {
        this.mapper = mapper;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public Employee create(EmployeeCreateRequest request, Long operatorId) {
        if (!EMPLOYEE_NO.matcher(request.employeeNo()).matches()) {
            throw new IllegalArgumentException("员工编号格式错误");
        }
        if (StringUtils.hasText(request.phone()) && !PHONE.matcher(request.phone()).matches()) {
            throw new IllegalArgumentException("联系电话格式错误");
        }
        if (mapper.findByEmployeeNo(request.employeeNo()) != null) {
            throw new EmployeeExistsException(request.employeeNo());
        }
        Employee employee = mapper.insert(new Employee(
                null, request.employeeNo(), request.name(), request.phone(), request.departmentId(),
                request.positionId(), request.status(), request.hireDate(), null
        ));
        operationLogService.record(operatorId, "CREATE", "EMPLOYEE", employee.id(), "SUCCESS");
        return employee;
    }

    public Employee getRequired(Long employeeId, DataScope scope) {
        Employee employee = mapper.findByIdWithScope(employeeId, scope.employeeId(), scope.departmentId(), scope.type().name());
        if (employee == null) {
            throw new DataScopeDeniedException(employeeId);
        }
        return employee;
    }
}
