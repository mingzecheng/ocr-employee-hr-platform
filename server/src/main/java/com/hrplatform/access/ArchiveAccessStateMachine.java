package com.hrplatform.access;

public final class ArchiveAccessStateMachine {
    private ArchiveAccessStateMachine() {
    }

    public static String submit(String status) {
        require(status, "DRAFT");
        return "PENDING_APPROVAL";
    }

    public static String approve(String status) {
        require(status, "PENDING_APPROVAL");
        return "APPROVED";
    }

    public static String reject(String status) {
        require(status, "PENDING_APPROVAL");
        return "REJECTED";
    }

    public static String use(String status) {
        require(status, "APPROVED");
        return "IN_USE";
    }

    public static String returnState(String status, boolean abnormal) {
        if ("ABNORMAL".equals(status)) {
            return "ABNORMAL";
        }
        require(status, "IN_USE");
        return abnormal ? "ABNORMAL" : "RETURNED";
    }

    public static String resolveAbnormal(String status) {
        require(status, "ABNORMAL");
        return "RETURNED";
    }

    private static void require(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new ArchiveAccessStateException("状态应为 " + expected + ", 实际为 " + actual);
        }
    }
}
