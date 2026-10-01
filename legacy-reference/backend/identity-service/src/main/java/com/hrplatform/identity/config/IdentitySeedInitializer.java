package com.hrplatform.identity.config;

import com.hrplatform.identity.role.SysRoleMapper;
import com.hrplatform.identity.user.SysUser;
import com.hrplatform.identity.user.SysUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"local", "test"})
public class IdentitySeedInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final String adminPassword;

    public IdentitySeedInitializer(SysUserMapper userMapper,
                                   SysRoleMapper roleMapper,
                                   @Value("${identity.seed.admin-password}") String adminPassword) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.adminPassword = adminPassword;
    }

    public void initialize() {
        if (userMapper.countByUsername("admin") > 0) {
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("identity.seed.admin-password must be configured");
        }
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPasswordHash(new BCryptPasswordEncoder().encode(adminPassword));
        admin.setStatus("ACTIVE");
        userMapper.insert(admin);
        Long roleId = roleMapper.findIdByCode("SYSTEM_ADMIN");
        if (roleId == null) {
            throw new IllegalStateException("SYSTEM_ADMIN role is missing");
        }
        roleMapper.insertUserRole(admin.getId(), roleId);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initialize();
    }
}
