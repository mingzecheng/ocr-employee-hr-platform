package com.hrplatform.audit;

/**
 * 一条操作请求的风控指纹。未绑定请求上下文（如异步/单元测试）时字段可为 null。
 */
public record RiskFields(String clientIp, String userAgent, String requestMethod,
                         String requestUri, String sessionId) {
    public static final RiskFields EMPTY = new RiskFields(null, null, null, null, null);
}
