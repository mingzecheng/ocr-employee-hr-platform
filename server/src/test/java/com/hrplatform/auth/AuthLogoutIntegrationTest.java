package com.hrplatform.auth;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.security.JwtService;
import com.hrplatform.common.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端验证登出使 token 立即失效：
 * 拿 token -> 访问受保护接口 200 -> logout -> 再用旧 token 访问 -> 401。
 * 黑名单用 MockitoBean 记录（测试环境不连真实 Redis）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthLogoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TokenBlacklistService blacklist;

    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void stubBlacklist() {
        revokedTokens.clear();
        doAnswer(inv -> {
            revokedTokens.add(inv.getArgument(0, String.class));
            return null;
        }).when(blacklist).invalidate(anyString());
        when(blacklist.isBlacklisted(anyString()))
                .thenAnswer(inv -> revokedTokens.contains(inv.getArgument(0, String.class)));
    }

    private String bearer() {
        return "Bearer " + jwtService.issue(new JwtPrincipal(11L, 22L, 7L, Set.of("HR_ADMIN")));
    }

    @Test
    void logoutImmediatelyInvalidatesToken() throws Exception {
        String token = bearer();

        // 登出前：受保护接口可访问
        mockMvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isOk());

        // 登出
        mockMvc.perform(post("/api/auth/logout").header("Authorization", token))
                .andExpect(status().isOk());

        // 登出后：旧 token 立即失效
        mockMvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedLogoutWithAlreadyRevokedTokenIsCleanNot5xx() throws Exception {
        String token = bearer();
        mockMvc.perform(post("/api/auth/logout").header("Authorization", token))
                .andExpect(status().isOk());

        // 同一已失效 token 再次登出：被过滤器拒绝（401），而非 500 异常
        mockMvc.perform(post("/api/auth/logout").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }
}
