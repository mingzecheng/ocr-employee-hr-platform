package com.hrplatform.employee;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmployeeMapper {
    Employee findByEmployeeNo(@Param("employeeNo") String employeeNo);

    Employee findByIdWithScope(
            @Param("id") Long id,
            @Param("employeeId") Long employeeId,
            @Param("departmentId") Long departmentId,
            @Param("scopeType") String scopeType
    );

    Employee insert(Employee employee);

    int updateStatusAndOrganization(EmployeeMutation mutation);

    int insertStatusHistory(EmployeeStatusHistory history);
}
