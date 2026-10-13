package com.hrplatform.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色 × 模块权限矩阵（只读，代码静态映射）。
 *
 * <p>本系统按 ADR-008 采用固定角色注解，不做动态权限引擎；此矩阵仅用于
 * 系统管理员在控制台上直观查看各角色可访问的功能模块，不参与鉴权判定。
 */
public final class RoleMatrix {

    private RoleMatrix() {
    }

    public static final List<String> ROLES = List.of(
            "SYSTEM_ADMIN", "HR_ADMIN", "DEPT_MANAGER", "EMPLOYEE");

    public static final List<String> MODULES = List.of(
            "员工档案", "组织管理", "人事审批", "档案借用", "盘点",
            "合同", "OCR识别", "统计报表", "审计日志", "系统监控");

    /** role -> module -> 是否可访问。 */
    public static Map<String, Map<String, Boolean>> matrix() {
        Map<String, Map<String, Boolean>> out = new LinkedHashMap<>();
        out.put("SYSTEM_ADMIN", all(true));
        out.put("HR_ADMIN", build(Map.of(
                "员工档案", true, "组织管理", true, "人事审批", true, "档案借用", true,
                "盘点", true, "合同", true, "OCR识别", true, "统计报表", true,
                "审计日志", true, "系统监控", false)));
        out.put("DEPT_MANAGER", build(Map.of(
                "员工档案", true, "组织管理", false, "人事审批", true, "档案借用", true,
                "盘点", true, "合同", true, "OCR识别", false, "统计报表", true,
                "审计日志", false, "系统监控", false)));
        out.put("EMPLOYEE", build(Map.of(
                "员工档案", true, "组织管理", false, "人事审批", true, "档案借用", true,
                "盘点", false, "合同", true, "OCR识别", false, "统计报表", false,
                "审计日志", false, "系统监控", false)));
        return out;
    }

    private static Map<String, Boolean> all(boolean value) {
        Map<String, Boolean> row = new LinkedHashMap<>();
        for (String module : MODULES) {
            row.put(module, value);
        }
        return row;
    }

    private static Map<String, Boolean> build(Map<String, Boolean> overrides) {
        Map<String, Boolean> row = new LinkedHashMap<>();
        for (String module : MODULES) {
            row.put(module, overrides.getOrDefault(module, false));
        }
        return row;
    }
}
