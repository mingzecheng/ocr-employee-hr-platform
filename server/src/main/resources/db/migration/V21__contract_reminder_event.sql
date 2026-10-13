-- P0 合同到期提醒事件化：把原先"查询时派生 DUE"改成可追踪、可确认的事件流。
-- 定时任务每日扫描 NOT_DUE 且即将到期的合同，按 30/7/1 天窗口生成 PENDING 事件；
-- HR 在待办里看到并 ACK 后不再提醒。幂等靠 uk_contract_stage：同一合同同一阶段只生成一次。
CREATE TABLE IF NOT EXISTS contract_reminder_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    contract_id BIGINT NOT NULL COMMENT 'employee_contract.id',
    due_date DATE NOT NULL COMMENT '合同到期日（冗余自 employee_contract.ends_on）',
    reminder_stage VARCHAR(10) NOT NULL COMMENT '到期提前量窗口：30D / 7D / 1D',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / ACKED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acked_at TIMESTAMP NULL COMMENT 'HR 知悉时间，ACKED 时写入',
    UNIQUE KEY uk_contract_stage (contract_id, reminder_stage),
    INDEX idx_status_due (status, due_date),
    CONSTRAINT fk_reminder_contract FOREIGN KEY (contract_id) REFERENCES employee_contract(id),
    CONSTRAINT ck_reminder_stage CHECK (reminder_stage IN ('30D', '7D', '1D')),
    CONSTRAINT ck_reminder_status CHECK (status IN ('PENDING', 'ACKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
