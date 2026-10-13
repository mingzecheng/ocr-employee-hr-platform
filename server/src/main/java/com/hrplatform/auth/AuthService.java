package com.hrplatform.auth;

import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.InvalidRefreshTokenException;
import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.security.JwtService;
import com.hrplatform.common.security.RefreshTokenService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
public class AuthService {
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final boolean demoAccountsEnabled;
    private final OperationLogService operationLogService;
    private final RefreshTokenService refreshTokenService;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService,
                       @Value("${platform.demo-accounts-enabled:true}") boolean demoAccountsEnabled,
                       OperationLogService operationLogService,
                       RefreshTokenService refreshTokenService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.demoAccountsEnabled = demoAccountsEnabled;
        this.operationLogService = operationLogService;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService,
                       boolean demoAccountsEnabled) {
        this(userMapper, passwordEncoder, jwtService, demoAccountsEnabled, null, null);
    }

    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService,
                       boolean demoAccountsEnabled, OperationLogService operationLogService) {
        this(userMapper, passwordEncoder, jwtService, demoAccountsEnabled, operationLogService, null);
    }

    public AuthResponse login(LoginRequest request) {
        if (!demoAccountsEnabled && request.username().regionMatches(true, 0, "demo-", 0, 5)) {
            record(null, "LOGIN", "SYS_USER", null, "FAILED");
            throw new InvalidCredentialsException();
        }
        SysUser user = userMapper.findByUsername(request.username());
        if (user != null) {
            user = user.withRoles(userMapper.findRoles(user.id()));
        }
        if (user == null || !user.enabled() || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            record(user == null ? null : user.id(), "LOGIN", "SYS_USER", user == null ? null : user.id(), "FAILED");
            throw new InvalidCredentialsException();
        }
        JwtPrincipal principal = new JwtPrincipal(user.id(), user.employeeId(), user.departmentId(), user.roles());
        String token = jwtService.issue(principal);
        // rememberMe 缺省 true；false 时不发 refresh token（仅 8 小时 access token）。
        String refreshToken = request.rememberMe() == Boolean.FALSE || refreshTokenService == null
                ? null : refreshTokenService.issue(user.id());
        record(user.id(), "LOGIN", "SYS_USER", user.id(), "SUCCESS");
        return new AuthResponse(token, Instant.now().plus(jwtService.expiration()), AuthUser.from(user), refreshToken);
    }

    /**
     * 用旧 refresh token 换发新的 access token + 新的 refresh token（rotate）。
     * 旧 access token 在自然过期前仍有效（不强制失效），符合本科演示项目的安全模型。
     */
    public AuthResponse refresh(String refreshToken) {
        if (refreshTokenService == null) {
            throw new InvalidRefreshTokenException("refresh token 服务未配置");
        }
        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotate(refreshToken);
        SysUser user = userMapper.findById(rotated.userId());
        if (user == null || !user.enabled()) {
            throw new InvalidRefreshTokenException("账号不存在或已被禁用");
        }
        user = user.withRoles(userMapper.findRoles(user.id()));
        JwtPrincipal principal = new JwtPrincipal(user.id(), user.employeeId(), user.departmentId(), user.roles());
        String accessToken = jwtService.issue(principal);
        record(user.id(), "REFRESH", "SYS_USER", user.id(), "SUCCESS");
        return new AuthResponse(accessToken, Instant.now().plus(jwtService.expiration()),
                AuthUser.from(user), rotated.refreshToken());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String name = request.name().trim();
        String phone = request.phone().trim();
        if (!StringUtils.hasText(name) || !StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("注册信息不能为空");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("密码按 UTF-8 编码后不能超过 72 字节");
        }
        List<EmployeeRegistrationBinding> employees = userMapper.findActiveEmployeesForRegistration(name, phone);
        if (employees.size() != 1) {
            throw new AccountAdminStateException("员工信息不匹配或该员工已注册，请核对后联系管理员");
        }
        EmployeeRegistrationBinding employee = employees.get(0);
        if (userMapper.countUsersByEmployeeId(employee.id()) > 0) {
            throw new AccountAdminStateException("员工信息不匹配或该员工已注册，请核对后联系管理员");
        }
        String username = employee.employeeNo();
        if (userMapper.findByUsername(username) != null) {
            throw new AccountAdminStateException("员工账号已存在，请联系管理员处理");
        }

        try {
            if (userMapper.insertAdminUser(username, passwordEncoder.encode(request.password()), employee.id(),
                    employee.departmentId(), true) != 1) {
                throw new AccountAdminStateException("账号创建失败，请稍后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new AccountAdminStateException("该用户名已被使用");
        }

        SysUser user = userMapper.findByUsername(username);
        if (user == null || userMapper.insertUserRole(user.id(), "EMPLOYEE") != 1) {
            throw new AccountAdminStateException("账号初始化失败，请稍后重试");
        }
        user = user.withRoles(userMapper.findRoles(user.id()));
        JwtPrincipal principal = new JwtPrincipal(user.id(), user.employeeId(), user.departmentId(), user.roles());
        String token = jwtService.issue(principal);
        record(user.id(), "REGISTER", "SYS_USER", user.id(), "SUCCESS");
        return new AuthResponse(token, Instant.now().plus(jwtService.expiration()), AuthUser.from(user));
    }

    private void record(Long operatorId, String action, String objectType, Long objectId, String result) {
        if (operationLogService == null) {
            return;
        }
        try {
            operationLogService.record(operatorId, action, objectType, objectId, result);
        } catch (RuntimeException exception) {
            // Authentication must retain its stable response even if audit storage is temporarily unavailable.
        }
    }
}
