package com.hrplatform.contract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 合同到期提醒事件化调度器。
 *
 * <p>每日扫描一次 {@code renewal_status='NOT_DUE'} 且即将到期的合同，按 30/7/1 天窗口
 * 生成 PENDING 的 {@code contract_reminder_event}。幂等靠：
 * <ul>
 *   <li>生成前先 {@code exists} 判断该合同该阶段是否已存在；</li>
 *   <li>表上 {@code uk_contract_stage(contract_id, reminder_stage)} 兜底，即使并发重复插入也只会有一行。</li>
 * </ul>
 * 已经做出续签决定（RENEWED / NOT_RENEWING）的合同不会被再次扫描，历史 PENDING 事件随合同状态
 * 变为非 NOT_DUE 后在待办聚合里自动消失。
 */
@Component
public class ContractReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(ContractReminderScheduler.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /** 最大窗口：一次性取出 30 天内到期的候选，再在内存里分 30/7/1 三档。 */
    private static final int MAX_WINDOW_DAYS = 30;

    private final ContractReminderMapper mapper;

    public ContractReminderScheduler(ContractReminderMapper mapper) {
        this.mapper = mapper;
    }

    @Scheduled(cron = "${hr.workflow.contract-reminder-cron:0 20 5 * * ?}", zone = "Asia/Shanghai")
    public int generateDueReminders() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        List<ContractReminderMapper.DueContractRow> candidates =
                mapper.findDueContractsWithin(today, today.plusDays(MAX_WINDOW_DAYS));
        int created = 0;
        for (ContractReminderMapper.DueContractRow contract : candidates) {
            long daysToDue = ChronoUnit.DAYS.between(today, contract.dueDate());
            created += maybeInsert(contract.contractId(), contract.dueDate(), MAX_WINDOW_DAYS, "30D", daysToDue);
            if (daysToDue <= 7) {
                created += maybeInsert(contract.contractId(), contract.dueDate(), 7, "7D", daysToDue);
            }
            if (daysToDue <= 1) {
                created += maybeInsert(contract.contractId(), contract.dueDate(), 1, "1D", daysToDue);
            }
        }
        if (created > 0) {
            log.info("Generated {} contract reminder event(s) for {} candidate contract(s) due by {}",
                    created, candidates.size(), today.plusDays(MAX_WINDOW_DAYS));
        }
        return created;
    }

    private int maybeInsert(Long contractId, LocalDate dueDate, int windowDays, String stage, long daysToDue) {
        if (daysToDue > windowDays) {
            return 0;
        }
        if (mapper.exists(contractId, stage)) {
            return 0;
        }
        return mapper.insert(contractId, dueDate, stage);
    }
}
