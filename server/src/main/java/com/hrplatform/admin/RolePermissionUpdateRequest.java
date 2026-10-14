package com.hrplatform.admin;

/**
 * 编辑某角色对某模块授权的请求体。
 *
 * @param module  模块 key（与 RoleMatrix.MODULES 一致）
 * @param allowed true=授予，false=撤销
 */
public record RolePermissionUpdateRequest(String module, Boolean allowed) {
}
