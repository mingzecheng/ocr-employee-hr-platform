package com.hrplatform.auth;

import com.hrplatform.common.security.JwtPrincipal;
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

    public AuthController(AuthService authService, TokenBlacklistService tokenBlacklist) {
        this.authService = authService;
        this.tokenBlacklist = tokenBlacklist;
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
     * 登出：将当前 token 的 SHA-256 摘要写入 Redis 黑名单，使其立即失效。
     * 幂等：重复登出不会报错（黑名单 key 覆盖写入即可）。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            tokenBlacklist.invalidate(authorization.substring(7));
        }
        return ApiResponse.success(null, "unknown");
    }
}
