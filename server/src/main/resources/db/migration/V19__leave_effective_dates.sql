-- P0-1 请假状态闭环：为请假明细记录"审批通过后实际生效的状态切换窗口"。
-- start_date/end_date 是员工发起请假时填报的请假区间；effective_from/effective_to
-- 是请假申请 HR 审批通过后才写入的自动切换窗口。两者在当前实现中取值一致，
-- 但语义上解耦"申请区间"与"审批生效区间"，并允许历史行保持 NULL（调度器跳过）。
ALTER TABLE leave_request_detail
    ADD COLUMN effective_from DATE NULL COMMENT '员工状态切换为 ON_LEAVE 的起始日，审批通过时写入；NULL 表示尚未审批或无需自动切换' AFTER end_date,
    ADD COLUMN effective_to DATE NULL COMMENT '员工状态切回 ACTIVE 的结束日，审批通过时写入；NULL 表示尚未审批或无需自动切换' AFTER effective_from;

CREATE INDEX idx_leave_request_effective_window
    ON leave_request_detail (effective_from, effective_to);
