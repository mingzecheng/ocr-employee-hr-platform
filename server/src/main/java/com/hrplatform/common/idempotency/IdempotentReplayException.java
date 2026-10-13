package com.hrplatform.common.idempotency;

/**
 * 审批幂等命中：同一审批人对同一申请单在幂等窗口内重复提交。
 * 由 GlobalExceptionHandler 映射为 HTTP 409 STATE_CONFLICT。
 */
public class IdempotentReplayException extends RuntimeException {
    public IdempotentReplayException(String message) {
        super(message);
    }
}
