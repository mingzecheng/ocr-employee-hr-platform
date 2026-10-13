package com.hrplatform.admin;

import java.util.List;
import java.util.Map;

/**
 * 角色权限矩阵视图：roles / modules 为展示维度，matrix 为 role -> module -> 是否可访问。
 */
public record RoleMatrixView(List<String> roles, List<String> modules,
                             Map<String, Map<String, Boolean>> matrix) {
}
