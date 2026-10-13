package com.hrplatform.common.security;

/**
 * refresh token 不存在、已过期或已被 rotate/登出。映射为 HTTP 401，前端应清本地凭证跳登录页。
 */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
