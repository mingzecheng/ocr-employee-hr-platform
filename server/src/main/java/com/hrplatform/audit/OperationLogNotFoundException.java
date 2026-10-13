package com.hrplatform.audit;

/** 按 ID 查询审计日志详情时记录不存在，映射为 404。 */
public class OperationLogNotFoundException extends RuntimeException {
    public OperationLogNotFoundException(Long id) {
        super("审计日志不存在: " + id);
    }
}
