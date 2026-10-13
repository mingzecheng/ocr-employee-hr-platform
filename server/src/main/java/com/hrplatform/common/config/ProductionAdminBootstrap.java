package com.hrplatform.common.config;

import com.hrplatform.audit.OperationLogService;
import com.hrplatform.auth.SysUser;
import com.hrplatform.auth.SysUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Profile("production")
public class ProductionAdminBootstrap implements ApplicationRunner {
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final String username;
    private final String password;

    public ProductionAdminBootstrap(SysUserMapper userMapper, PasswordEncoder passwordEncoder,
                                   OperationLogService operationLogService,
                                   @Value("${platform.bootstrap.admin-username:}") String username,
                                   @Value("${platform.bootstrap.admin-password:}") String password) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userMapper.countEnabledSystemAdmins() > 0) {
            return;
        }
        String normalizedUsername = validateCredentials();

        if (userMapper.lockRoleIdByCode(SYSTEM_ADMIN) == null) {
            throw new IllegalStateException("Production bootstrap requires the SYSTEM_ADMIN role");
        }
        if (userMapper.countEnabledSystemAdmins() > 0) {
            return;
        }
        if (userMapper.findByUsername(normalizedUsername) != null) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME already exists; choose another username");
        }

        String passwordHash = passwordEncoder.encode(password);
        if (userMapper.insertAdminUser(normalizedUsername, passwordHash, null, null, true) != 1) {
            throw new IllegalStateException("Production bootstrap could not create the system administrator");
        }
        SysUser created = userMapper.findByUsername(normalizedUsername);
        if (created == null || userMapper.insertUserRole(created.id(), SYSTEM_ADMIN) != 1) {
            throw new IllegalStateException("Production bootstrap could not assign the system administrator role");
        }
        operationLogService.record(null, "BOOTSTRAP_CREATE", "SYS_USER", created.id(), "SUCCESS");
    }

    private String validateCredentials() {
        String normalizedUsername = username == null ? "" : username.trim();
        List<String> errors = new ArrayList<>();
        if (normalizedUsername.length() < 3 || normalizedUsername.length() > 64
                || normalizedUsername.isBlank() || normalizedUsername.toLowerCase(Locale.ROOT).startsWith("demo-")) {
            errors.add("BOOTSTRAP_ADMIN_USERNAME must be a non-demo username of 3-64 characters");
        }
        if (password == null || password.length() < 16
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            errors.add("BOOTSTRAP_ADMIN_PASSWORD must contain 16-72 UTF-8 bytes");
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException(String.join("; ", errors));
        }
        return normalizedUsername;
    }
}
