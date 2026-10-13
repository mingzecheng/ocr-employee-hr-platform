package com.hrplatform.auth;

import com.hrplatform.common.security.InvalidRefreshTokenException;
import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.security.JwtService;
import com.hrplatform.common.security.RefreshTokenService;
import com.hrplatform.common.security.RefreshTokenUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 7 天免登录流程端到端验证：
 * 登录发 refreshToken → /refresh 换发新 access + 新 refresh（旧 refresh 一次性）→ logout 清 refresh。
 * RefreshTokenService / SysUserMapper 用 MockitoBean 打桩，测试环境不连真实 Redis/DB。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRefreshIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;
    @MockitoBean
    private SysUserMapper userMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void stubUser() {
        SysUser user = new SysUser(99L, "hr-admin", encoder.encode("secret"), 500L, 7L, true, Set.of("EMPLOYEE"));
        when(userMapper.findByUsername("hr-admin")).thenReturn(user);
        when(userMapper.findRoles(99L)).thenReturn(Set.of("EMPLOYEE"));
        when(userMapper.findById(99L)).thenReturn(user);
    }

    @Test
    void loginIssuesRefreshTokenByDefault() throws Exception {
        when(refreshTokenService.issue(99L)).thenReturn("login-refresh-uuid");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"hr-admin","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").value("login-refresh-uuid"));
    }

    @Test
    void rememberMeFalseOmitsRefreshToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"hr-admin","password":"secret","rememberMe":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void refreshRotatesTokenAndIssuesNewAccess() throws Exception {
        when(refreshTokenService.rotate("old-refresh"))
                .thenReturn(new RefreshTokenService.RotatedRefreshToken(99L, "new-refresh-uuid"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"old-refresh"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-uuid"));

        // 旧 refresh token 已被 service rotate 逻辑删除，这里只需验证端点把旧 token 传给了 service。
        verify(refreshTokenService).rotate("old-refresh");
    }

    @Test
    void forgedRefreshTokenReturns401() throws Exception {
        when(refreshTokenService.rotate("forged"))
                .thenThrow(new InvalidRefreshTokenException("refresh token 无效或已过期"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"forged"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void redisDownReturns503OnRefresh() throws Exception {
        when(refreshTokenService.rotate(any()))
                .thenThrow(new RefreshTokenUnavailableException("down", null));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"any"}
                                """))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void logoutRevokesRefreshTokenFromRedis() throws Exception {
        String access = "Bearer " + jwtService.issue(new JwtPrincipal(99L, 500L, 7L, Set.of("EMPLOYEE")));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"session-refresh"}
                                """))
                .andExpect(status().isOk());

        verify(refreshTokenService).revoke("session-refresh");
    }
}
