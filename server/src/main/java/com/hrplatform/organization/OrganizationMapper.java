package com.hrplatform.organization;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrganizationMapper {
    List<DepartmentNode> listEnabledDepartments();

    List<DepartmentAdminView> listAllDepartments();

    DepartmentAdminView findDepartment(@Param("id") Long id);

    DepartmentAdminView findDepartmentByCode(@Param("code") String code);

    boolean departmentCodeExists(@Param("code") String code, @Param("excludeId") Long excludeId);

    boolean isActiveEmployee(@Param("employeeId") Long employeeId);

    boolean hasEnabledChildren(@Param("departmentId") Long departmentId);

    boolean hasActiveEmployees(@Param("departmentId") Long departmentId);

    int clearManagerAssignmentForEmployee(@Param("employeeId") Long employeeId);

    int insertDepartment(DepartmentMutationRequest request);

    int updateDepartment(@Param("id") Long id, @Param("request") DepartmentMutationRequest request);

    List<PositionOption> listEnabledPositions(Long departmentId);

    List<PositionAdminView> listDepartmentPositions(@Param("departmentId") Long departmentId);

    PositionAdminView findPosition(@Param("id") Long id);

    PositionAdminView findPositionByDepartmentAndCode(@Param("departmentId") Long departmentId,
                                                       @Param("code") String code);

    boolean positionCodeExists(@Param("departmentId") Long departmentId, @Param("code") String code,
                               @Param("excludeId") Long excludeId);

    int insertPosition(@Param("departmentId") Long departmentId, @Param("request") PositionMutationRequest request);

    int updatePosition(@Param("departmentId") Long departmentId, @Param("id") Long id,
                       @Param("request") PositionMutationRequest request);

    boolean isEnabledPosition(@Param("positionId") Long positionId,
                              @Param("departmentId") Long departmentId);

    int countChildren(@Param("departmentId") Long departmentId);

    int countPositions(@Param("departmentId") Long departmentId);

    int countEmployeesByDepartment(@Param("departmentId") Long departmentId);

    int countActiveEmployeesByPosition(@Param("positionId") Long positionId);

    int deleteDepartment(@Param("id") Long id);

    int deletePosition(@Param("departmentId") Long departmentId, @Param("id") Long id);
}
