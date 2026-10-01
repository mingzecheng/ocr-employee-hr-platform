package com.hrplatform.archive.employee;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EmployeeMapper {
    int insert(Employee employee);
    Employee findByIdInScope(@Param("id") long id,
                             @Param("scopeType") String scopeType,
                             @Param("employeeId") Long employeeId,
                             @Param("departmentId") Long departmentId);
    List<Employee> listInScope(@Param("offset") int offset, @Param("limit") int limit,
                               @Param("scopeType") String scopeType,
                               @Param("employeeId") Long employeeId,
                               @Param("departmentId") Long departmentId,
                               @Param("keyword") String keyword,
                               @Param("status") String status);
    long countInScope(@Param("scopeType") String scopeType,
                      @Param("employeeId") Long employeeId,
                      @Param("departmentId") Long departmentId,
                      @Param("keyword") String keyword,
                      @Param("status") String status);
    int countByEmployeeNo(@Param("employeeNo") String employeeNo);
}
