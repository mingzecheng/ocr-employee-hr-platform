package com.hrplatform.auth;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.security.RefreshTokenService;
import com.hrplatform.common.security.TokenBlacklistService;
import com.hrplatform.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final TokenBlacklistService tokenBlacklist;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthService authService, TokenBlacklistService tokenBlacklist,
                          RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.tokenBlacklist = tokenBlacklist;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request), "unknown");
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request), "unknown");
    }

    @GetMapping("/me")
    public ApiResponse<JwtPrincipal> me(Authentication authentication) {
        return ApiResponse.success((JwtPrincipal) authentication.getPrincipal(), "unknown");
    }

    /**
     * 用 refresh token 换发新的 access token + 新的 refresh token（rotate，旧 refresh token 立即失效）。
     * 公开端点（不要求 Authorization 头），由前端在 access token 401 时自动调用。
     */
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@RequestBody(required = false) RefreshRequest request) {
        String token = request == null ? null : request.refreshToken();
        return ApiResponse.success(authService.refresh(token), "unknown");
    }

    /**
     * 登出：将当前 access token 的 SHA-256 摘要写入 Redis 黑名单使其立即失效，
     * 同时删除请求体携带的 refresh token（若有）。
     * 幂等：重复登出不会报错。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                    @RequestBody(required = false) LogoutRequest request) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            tokenBlacklist.invalidate(authorization.substring(7));
        }
        if (request != null) {
            refreshTokenService.revoke(request.refreshToken());
        }
        return ApiResponse.success(null, "unknown");
    }
}
