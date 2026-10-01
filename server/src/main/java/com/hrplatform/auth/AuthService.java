package com.hrplatform.auth;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthService {
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse login(LoginRequest request) {
        SysUser user = userMapper.findByUsername(request.username());
        if (user == null || !user.enabled() || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        JwtPrincipal principal = new JwtPrincipal(user.id(), user.employeeId(), user.departmentId(), user.roles());
        String token = jwtService.issue(principal);
        return new AuthResponse(token, Instant.now().plus(jwtService.expiration()), AuthUser.from(user));
    }
}
