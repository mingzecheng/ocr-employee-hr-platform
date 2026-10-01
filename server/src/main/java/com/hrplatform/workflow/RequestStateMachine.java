package com.hrplatform.workflow;

import java.util.Set;

public final class RequestStateMachine {
    private RequestStateMachine() {
    }

    public static String submit(String status) {
        require(status, "DRAFT");
        return "PENDING_DEPARTMENT";
    }

    public static String approve(String status, String node, Set<String> roles) {
        Set<String> safeRoles = roles == null ? Set.of() : roles;
        if ("PENDING_DEPARTMENT".equals(status) && "DEPARTMENT".equals(node)) {
            if (!safeRoles.contains("DEPT_MANAGER") && !safeRoles.contains("SYSTEM_ADMIN")) {
                throw new RequestStateException("当前用户不能进行部门审批");
            }
            return "PENDING_HR";
        }
        if ("PENDING_HR".equals(status) && "HR".equals(node)) {
            if (!safeRoles.contains("HR_ADMIN") && !safeRoles.contains("SYSTEM_ADMIN")) {
                throw new RequestStateException("当前用户不能进行人事审批");
            }
            return "APPROVED";
        }
        throw new RequestStateException("当前状态不允许审批: " + status);
    }

    public static String reject(String status) {
        if (!"PENDING_DEPARTMENT".equals(status) && !"PENDING_HR".equals(status)) {
            throw new RequestStateException("当前状态不允许拒绝: " + status);
        }
        return "REJECTED";
    }

    public static String cancel(String status) {
        require(status, "DRAFT");
        return "CANCELLED";
    }

    private static void require(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new RequestStateException("状态应为 " + expected + ", 实际为 " + actual);
        }
    }
}
