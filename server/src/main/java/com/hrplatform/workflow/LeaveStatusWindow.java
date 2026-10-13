package com.hrplatform.workflow;

import java.time.LocalDate;

/**
 * 请假状态切换窗口：由调度器读取，用于把员工状态在 ACTIVE 与 ON_LEAVE 之间切换。
 *
 * @param requestId      请假申请 id（hr_request.id）
 * @param employeeId     请假员工 id
 * @param effectiveFrom  状态切到 ON_LEAVE 的起始日
 * @param effectiveTo    状态切回 ACTIVE 的结束日
 */
public record LeaveStatusWindow(Long requestId, Long employeeId,
                                LocalDate effectiveFrom, LocalDate effectiveTo) {
}
