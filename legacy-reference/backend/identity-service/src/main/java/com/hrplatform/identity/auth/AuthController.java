package com.hrplatform.identity.auth;

import com.hrplatform.common.api.ApiResponse;
import com.hrplatform.common.security.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginData> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request), com.hrplatform.common.api.TraceId.current());
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserData> me(Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return ApiResponse.success(authService.currentUser(principal), com.hrplatform.common.api.TraceId.current());
    }
}
