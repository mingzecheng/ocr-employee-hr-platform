package com.hrplatform.common.security;

/**
 * Redis 不可用导致 refresh token 无法校验/续期。由 GlobalExceptionHandler 映射为 HTTP 503，
 * 前端据此跳登录页（而不是当作"账号失效"反复重试）。
 */
public class RefreshTokenUnavailableException extends RuntimeException {
    public RefreshTokenUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
