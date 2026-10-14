package com.hrplatform.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色模块授权持久化。底层复用既有 {@code sys_role} / {@code sys_permission} /
 * {@code sys_role_permission} 三表（见 V1 迁移）：
 * <ul>
 *   <li>sys_role.code 为角色编码（SYSTEM_ADMIN / HR_ADMIN / DEPT_MANAGER / EMPLOYEE）；</li>
 *   <li>sys_permission.code 为模块 key（与 RoleMatrix.MODULES 及后台控制台模块一致）；</li>
 *   <li>sys_role_permission 中"存在一行"即代表该角色拥有该模块权限，不存在即无权限。</li>
 * </ul>
 *
 * <p>这里不做接口级细粒度鉴权，仅维护管理后台的"模块可见性"开关。
 */
@Mapper
public interface RolePermissionMapper {

    /** sys_role_permission 当前授权记录总数（用于判断是否需要启动种子化）。 */
    long countRolePermissions();

    /** 按角色编码取 sys_role.id；不存在返回 null。 */
    Long findRoleIdByCode(@Param("roleCode") String roleCode);

    /** 按模块 key 取 sys_permission.id；不存在返回 null。 */
    Long findPermissionIdByCode(@Param("moduleCode") String moduleCode);

    /** 插入一条 sys_permission 模块记录（启动种子化用）。 */
    void insertPermission(@Param("moduleCode") String moduleCode);

    /** 授予：插入 sys_role_permission 关联行（调用方需先确认不存在）。 */
    void insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    /** 撤销：删除 sys_role_permission 关联行。 */
    int deleteRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    /** 判断某角色是否被授予某模块（后台模块守卫用）。 */
    boolean hasPermission(@Param("roleCode") String roleCode, @Param("moduleCode") String moduleCode);

    /** 全部已授权的 角色×模块 对（用于从 DB 重建矩阵）。 */
    List<RolePermissionRow> findGrantedPermissions();
}
