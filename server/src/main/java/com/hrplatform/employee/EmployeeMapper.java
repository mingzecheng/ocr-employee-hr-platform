package com.hrplatform.employee;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmployeeMapper {
    java.util.List<Employee> list(@Param("keyword") String keyword,
                                  @Param("status") String status,
                                  @Param("departmentId") Long departmentId,
                                  @Param("employeeId") Long employeeId,
                                  @Param("scopeType") String scopeType,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    long count(@Param("keyword") String keyword,
               @Param("status") String status,
               @Param("departmentId") Long departmentId,
               @Param("employeeId") Long employeeId,
               @Param("scopeType") String scopeType);

    Employee findByEmployeeNo(@Param("employeeNo") String employeeNo);

    Employee findByIdWithScope(
            @Param("id") Long id,
            @Param("employeeId") Long employeeId,
            @Param("departmentId") Long departmentId,
            @Param("scopeType") String scopeType
    );

    Employee findByIdWithScopeForUpdate(
            @Param("id") Long id,
            @Param("employeeId") Long employeeId,
            @Param("departmentId") Long departmentId,
            @Param("scopeType") String scopeType
    );

    Employee lockById(@Param("id") Long id);

    int insert(Employee employee);

    int updateProfile(EmployeeProfileMutation mutation);

    int updateStatusAndOrganization(EmployeeMutation mutation);

    int insertStatusHistory(EmployeeStatusHistory history);

    java.util.List<EmployeeStatusHistoryView> listStatusHistory(@Param("employeeId") Long employeeId,
                                                                 @Param("scopeEmployeeId") Long scopeEmployeeId,
                                                                 @Param("scopeDepartmentId") Long scopeDepartmentId,
                                                                 @Param("scopeType") String scopeType,
                                                                 @Param("offset") int offset,
                                                                 @Param("limit") int limit);

    long countStatusHistory(@Param("employeeId") Long employeeId,
                            @Param("scopeEmployeeId") Long scopeEmployeeId,
                            @Param("scopeDepartmentId") Long scopeDepartmentId,
                            @Param("scopeType") String scopeType);
}
