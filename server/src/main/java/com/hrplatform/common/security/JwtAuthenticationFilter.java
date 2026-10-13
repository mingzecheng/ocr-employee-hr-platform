package com.hrplatform.common.security;

import com.hrplatform.auth.SysUser;
import com.hrplatform.auth.SysUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final SysUserMapper userMapper;
    private final boolean liveValidation;
    private final TokenBlacklistService blacklist;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this(jwtService, null, false, null);
    }

    public JwtAuthenticationFilter(JwtService jwtService, SysUserMapper userMapper, boolean liveValidation) {
        this(jwtService, userMapper, liveValidation, null);
    }

    public JwtAuthenticationFilter(JwtService jwtService, SysUserMapper userMapper, boolean liveValidation,
                                  TokenBlacklistService blacklist) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.liveValidation = liveValidation;
        this.blacklist = blacklist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                // 登出黑名单检查：token 在黑名单中则返回 401 并短路。
                if (blacklist != null && blacklist.isBlacklisted(token)) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                JwtPrincipal principal = resolvePrincipal(token);
                var authorities = principal.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
                var authentication = new UsernamePasswordAuthenticationToken(principal, token, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtAuthenticationException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private JwtPrincipal resolvePrincipal(String token) {
        JwtPrincipal claimsPrincipal = jwtService.parse(token);
        if (!liveValidation) {
            return claimsPrincipal;
        }
        if (userMapper == null) {
            throw new JwtAuthenticationException("JWT live validation is not configured", null);
        }
        SysUser user = userMapper.findById(claimsPrincipal.userId());
        if (user == null || !user.enabled()) {
            throw new JwtAuthenticationException("User is disabled or no longer exists", null);
        }
        java.util.Set<String> roles = userMapper.findRoles(user.id());
        return new JwtPrincipal(user.id(), user.employeeId(), user.departmentId(),
                roles == null ? java.util.Set.of() : roles);
    }
}
