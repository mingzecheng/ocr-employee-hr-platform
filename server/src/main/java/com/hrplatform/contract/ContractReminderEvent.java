package com.hrplatform.contract;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同到期提醒事件。一个合同在 30/7/1 天窗口各最多一条（靠 uk_contract_stage 幂等）。
 *
 * @param stage 提醒窗口阶段：{@code 30D} / {@code 7D} / {@code 1D}
 * @param status {@code PENDING}（待 HR 知悉）/ {@code ACKED}（已知悉）
 */
public record ContractReminderEvent(Long id, Long contractId, LocalDate dueDate, String stage,
                                   String status, LocalDateTime createdAt, LocalDateTime ackedAt) {
}
