package com.hrplatform.admin;

import java.time.LocalDateTime;

/**
 * 登录日志行。与审计日志列表不同，登录日志面向 SYSTEM_ADMIN，保留 clientIp / userAgent 原文。
 */
public record LoginLogItem(Long id, String username, String action, String clientIp, String userAgent,
                           String result, LocalDateTime createdAt) {
}
