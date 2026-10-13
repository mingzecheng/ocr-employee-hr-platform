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
import static org.mockito.Mockito.mock;
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

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
