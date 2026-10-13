package com.hrplatform.contract;

import com.hrplatform.audit.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 合同到期提醒的"知悉(ACK)"服务。
 *
 * <p>只有 HR_ADMIN 能调用（控制器层已限制）。对一条 PENDING 提醒置 ACKED，写操作日志。
 * 已 ACKED 的重复调用视为幂等成功（前端可能双击），不存在的 id 抛出异常。
 */
@Service
public class ContractReminderService {
    private final ContractReminderMapper mapper;
    private final OperationLogService operationLogService;

    public ContractReminderService(ContractReminderMapper mapper, OperationLogService operationLogService) {
        this.mapper = mapper;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public void ack(Long id, Long operatorId) {
        ContractReminderEvent event = mapper.findById(id);
        if (event == null) {
            throw new IllegalArgumentException("合同到期提醒不存在: " + id);
        }
        if ("ACKED".equals(event.status())) {
            // 幂等：已经知悉过，不再重复写库/审计。
            return;
        }
        if (mapper.ack(id, LocalDateTime.now()) != 1) {
            throw new IllegalStateException("合同到期提醒状态已变化，请刷新后重试: " + id);
        }
        operationLogService.record(operatorId, "ACK_CONTRACT_REMINDER", "CONTRACT_REMINDER", id, "SUCCESS");
    }
}
