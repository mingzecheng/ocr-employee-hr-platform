package com.hrplatform.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 从当前绑定的 HttpServletRequest 提取风控指纹（IP / UA / method / uri / 会话 jti）。
 * 静态工具类，避免侵入 OperationLogService 的构造函数与既有单元测试。
 */
public final class RiskFieldsExtractor {
    /** JwtAuthenticationFilter 写入 MDC 的 JWT jti 键。 */
    public static final String MDC_JTI = "hr.request.jti";

    private RiskFieldsExtractor() {
    }

    public static RiskFields current() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return RiskFields.EMPTY;
        }
        return fromRequest(request);
    }

    /** 从指定请求构建风控指纹（sessionId 取当前 MDC，便于单元测试）。 */
    static RiskFields fromRequest(HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        String userAgent = trimTo(request.getHeader("User-Agent"), 512);
        String requestMethod = trimTo(request.getMethod(), 10);
        String requestUri = trimTo(request.getRequestURI(), 512);
        String sessionId = trimTo(MDC.get(MDC_JTI), 128);
        return new RiskFields(clientIp, userAgent, requestMethod, requestUri, sessionId);
    }

    /**
     * 优先取 X-Forwarded-For 第一个 IP（反向代理场景），否则回退到 remoteAddr。
     */
    static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            first = first.trim();
            if (StringUtils.hasText(first)) {
                return trimTo(first, 45);
            }
        }
        return trimTo(request.getRemoteAddr(), 45);
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    private static String trimTo(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
