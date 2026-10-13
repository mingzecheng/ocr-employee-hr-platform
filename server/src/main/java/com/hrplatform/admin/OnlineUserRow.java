package com.hrplatform.admin;

import java.util.Set;

/**
 * 在线用户行。loginApprox 为近似登录时间（由 refresh token 剩余 TTL 反推，ISO 格式）。
 */
public record OnlineUserRow(Long userId, String username, Set<String> roles, String loginApprox) {
}
