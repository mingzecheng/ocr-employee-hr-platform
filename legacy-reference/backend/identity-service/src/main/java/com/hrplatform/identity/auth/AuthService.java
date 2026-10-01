package com.hrplatform.identity.auth;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.security.JwtService;
import com.hrplatform.identity.role.SysRoleMapper;
import com.hrplatform.identity.user.SysUser;
import com.hrplatform.identity.user.SysUserMapper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(SysUserMapper userMapper, SysRoleMapper roleMapper, JwtService jwtService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.jwtService = jwtService;
    }

    public LoginData login(LoginRequest request) {
        SysUser user = userMapper.findEnabledByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        List<String> roles = roleMapper.findCodesByUserId(user.getId());
        List<String> permissions = roleMapper.findPermissionCodesByUserId(user.getId());
        JwtService.IssuedToken issued = jwtService.issue(
                user.getId(), user.getUsername(), roles, permissions,
                user.getEmployeeId(), user.getDepartmentId());
        return new LoginData(issued.value(), issued.expiresAt());
    }

    public CurrentUserData currentUser(JwtPrincipal principal) {
        return new CurrentUserData(principal.userId(), principal.username(),
                principal.roles(), principal.permissions(), principal.employeeId(), principal.departmentId());
    }
}
