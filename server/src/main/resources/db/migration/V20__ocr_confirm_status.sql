-- P0-2 OCR 结果确认回填：为 ocr_binding 增加"人工确认"状态机字段。
-- 待确认(PENDING) → 已确认(CONFIRMED) / 已拒绝(REJECTED)。
-- confirmed_by/confirmed_at 记录最后一次确认/拒绝的操作者与时间；
-- confirm_note 记录确认或拒绝时附带的备注（如低置信度字段已修订说明、拒绝原因）。
-- 默认 PENDING 保证历史 ocr_binding 行不会被误判为已确认。
ALTER TABLE ocr_binding
    ADD COLUMN confirm_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        COMMENT 'OCR 结果人工确认状态：PENDING/CONFIRMED/REJECTED' AFTER review_required,
    ADD COLUMN confirmed_by BIGINT NULL
        COMMENT '最后一次确认/拒绝操作者 sys_user.id' AFTER confirm_status,
    ADD COLUMN confirmed_at TIMESTAMP NULL
        COMMENT '最后一次确认/拒绝时间' AFTER confirmed_by,
    ADD COLUMN confirm_note VARCHAR(512) NULL
        COMMENT '确认或拒绝备注' AFTER confirmed_at;

CREATE INDEX idx_ocr_binding_confirm_status ON ocr_binding (confirm_status);
