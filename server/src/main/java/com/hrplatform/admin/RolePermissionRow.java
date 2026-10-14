package com.hrplatform.admin;

/**
 * 角色 × 模块授权记录投影：从 sys_role_permission 关联 sys_role / sys_permission 查出的
 * 一条"某角色已被授予某模块"的记录。
 */
public record RolePermissionRow(String roleCode, String moduleCode) {
}
