package com.hrplatform.common.security;

import com.hrplatform.auth.SysUser;
import com.hrplatform.auth.SysUserMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {
    private final JwtService jwtService = new JwtService(
            "test-secret-key-test-secret-key-test-secret-key", Duration.ofHours(8));

    @Test
    void disabledAccountCannotUsePreviouslyIssuedToken() throws Exception {
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.findById(11L)).thenReturn(new SysUser(11L, "staff", "hash", 22L, 7L, false));
        String token = jwtService.issue(new JwtPrincipal(11L, 22L, 7L, Set.of("HR_ADMIN")));
        FilterChain chain = mock(FilterChain.class);

        SecurityContextHolder.clearContext();
        new JwtAuthenticationFilter(jwtService, mapper, true).doFilter(
                request(token), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void currentRolesReplaceRolesEmbeddedInToken() throws Exception {
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.findById(11L)).thenReturn(new SysUser(11L, "staff", "hash", 22L, 9L, true));
        when(mapper.findRoles(11L)).thenReturn(Set.of("EMPLOYEE"));
        String token = jwtService.issue(new JwtPrincipal(11L, 22L, 7L, Set.of("SYSTEM_ADMIN")));

        SecurityContextHolder.clearContext();
        new JwtAuthenticationFilter(jwtService, mapper, true).doFilter(
                request(token), new MockHttpServletResponse(), mock(FilterChain.class));

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_EMPLOYEE");
        assertThat(((JwtPrincipal) authentication.getPrincipal()).departmentId()).isEqualTo(9L);
    }

    @Test
    void revokedTokenIsRejectedWith401AndShortCircuitsChain() throws Exception {
        TokenBlacklistService blacklist = mock(TokenBlacklistService.class);
        String token = jwtService.issue(new JwtPrincipal(11L, 22L, 7L, Set.of("HR_ADMIN")));
        when(blacklist.isBlacklisted(token)).thenReturn(true);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContextHolder.clearContext();
        new JwtAuthenticationFilter(jwtService, null, false, blacklist).doFilter(
                request(token), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nonRevokedTokenStillAuthenticates() throws Exception {
        TokenBlacklistService blacklist = mock(TokenBlacklistService.class);
        String token = jwtService.issue(new JwtPrincipal(11L, 22L, 7L, Set.of("HR_ADMIN")));
        when(blacklist.isBlacklisted(token)).thenReturn(false);

        SecurityContextHolder.clearContext();
        new JwtAuthenticationFilter(jwtService, null, false, blacklist).doFilter(
                request(token), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void revokedCheckDegradesToAllowWhenBlacklistSaysNotRevoked() throws Exception {
        TokenBlacklistService blacklist = mock(TokenBlacklistService.class);
        String token = jwtService.issue(new JwtPrincipal(11L, 22L, 7L, Set.of("HR_ADMIN")));
        // Redis 不可用时 TokenBlacklistService.isBlacklisted 内部降级返回 false，过滤器应照常放行
        when(blacklist.isBlacklisted(anyString())).thenReturn(false);

        SecurityContextHolder.clearContext();
        new JwtAuthenticationFilter(jwtService, null, false, blacklist).doFilter(
                request(token), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
