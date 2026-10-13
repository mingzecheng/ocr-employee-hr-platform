package com.hrplatform.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

@Mapper
public interface SysUserMapper {
    SysUser findByUsername(@Param("username") String username);

    SysUser findById(@Param("id") Long id);

    List<EmployeeRegistrationBinding> findActiveEmployeesForRegistration(@Param("name") String name,
                                                                          @Param("phone") String phone);

    long countUsersByEmployeeId(@Param("employeeId") Long employeeId);

    Set<String> findRoles(@Param("id") Long userId);

    List<AdminUserRow> listAdminUsers(@Param("offset") int offset, @Param("limit") int limit);

    long countAdminUsers();

    List<AdminRoleOption> listEnabledRoles();

    AdminUserRow findAdminUser(@Param("id") Long id);

    AdminUserRow findAdminUserByUsername(@Param("username") String username);

    EmployeeBinding findActiveEmployee(@Param("id") Long id);

    int insertAdminUser(@Param("username") String username, @Param("passwordHash") String passwordHash,
                        @Param("employeeId") Long employeeId, @Param("departmentId") Long departmentId,
                        @Param("enabled") boolean enabled);

    int updateAdminUser(@Param("id") Long id, @Param("passwordHash") String passwordHash,
                        @Param("employeeId") Long employeeId, @Param("departmentId") Long departmentId,
                        @Param("enabled") boolean enabled);

    int deleteUserRoles(@Param("userId") Long userId);

    int updateDepartmentForEmployee(@Param("employeeId") Long employeeId,
                                    @Param("departmentId") Long departmentId);

    int deleteUserRoleForEmployee(@Param("employeeId") Long employeeId,
                                  @Param("roleCode") String roleCode);

    int insertUserRole(@Param("userId") Long userId, @Param("roleCode") String roleCode);

    Long lockRoleIdByCode(@Param("code") String code);

    long countEnabledSystemAdmins();
}
