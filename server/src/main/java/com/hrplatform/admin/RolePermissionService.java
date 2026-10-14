package com.hrplatform.admin;

import com.hrplatform.common.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 角色 × 模块授权服务。
 *
 * <p>底层读写 {@code sys_role_permission}：行存在 = 允许，不存在 = 拒绝。
 * 启动时若该表为空，按 {@link RoleMatrix} 的静态默认矩阵种子化一次；之后以 DB 为准动态返回矩阵。
 *
 * <p>这是"管理后台模块可见性"，不是接口级细粒度权限：员工 / HR / 部门负责人的业务接口仍由
 * {@code @PreAuthorize} 注解把关；本服务只额外约束后台控制台各 GET 页面能否访问。
 */
@Service
public class RolePermissionService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RolePermissionService.class);

    /** 后台控制台页面模块 key（与矩阵业务模块区分，仅用于后台守卫）。 */
    public static final String MODULE_ONLINE_USERS = "在线用户";
    public static final String MODULE_LOGIN_LOGS = "登录日志";
    /** 系统监控同时是矩阵中的一个业务模块，直接复用 RoleMatrix.MODULES 里的"系统监控"。 */

    private final RolePermissionMapper mapper;

    public RolePermissionService(RolePermissionMapper mapper) {
        this.mapper = mapper;
    }

    /** 启动种子化：sys_role_permission 为空时，用静态默认矩阵灌一次默认授权。 */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIfEmpty();
    }

    void seedIfEmpty() {
        try {
            if (mapper.countRolePermissions() > 0) {
                return;
            }
            // 1) 确保所有模块（业务 10 个 + 后台守卫 2 个）在 sys_permission 中存在。
            Set<String> allModules = new LinkedHashSet<>(RoleMatrix.MODULES);
            allModules.add(MODULE_ONLINE_USERS);
            allModules.add(MODULE_LOGIN_LOGS);
            for (String module : allModules) {
                ensurePermission(module);
            }
            // 2) 按静态默认矩阵授予"为 true"的格子。
            Map<String, Map<String, Boolean>> defaults = RoleMatrix.matrix();
            for (String role : RoleMatrix.ROLES) {
                Map<String, Boolean> row = defaults.get(role);
                for (String module : RoleMatrix.MODULES) {
                    if (Boolean.TRUE.equals(row.get(module))) {
                        grantQuietly(role, module);
                    }
                }
            }
            // 3) 系统管理员默认拥有全部后台控制台模块。
            grantQuietly("SYSTEM_ADMIN", MODULE_ONLINE_USERS);
            grantQuietly("SYSTEM_ADMIN", MODULE_LOGIN_LOGS);
            log.info("角色权限矩阵已按静态默认种子化，共 {} 条授权", mapper.countRolePermissions());
        } catch (RuntimeException ex) {
            // 种子化失败不应阻断应用启动（例如最小化测试 schema 未建表时降级为只读静态矩阵）。
            log.warn("角色权限种子化跳过: {}", ex.getMessage());
        }
    }

    /** 从 DB 重建 角色×模块 矩阵；DB 无数据时退化为静态默认矩阵。 */
    public RoleMatrixView matrix() {
        Map<String, Set<String>> grantedByRole = new HashMap<>();
        try {
            for (RolePermissionRow row : mapper.findGrantedPermissions()) {
                grantedByRole.computeIfAbsent(row.roleCode(), k -> new LinkedHashSet<>())
                        .add(row.moduleCode());
            }
        } catch (RuntimeException ex) {
            log.warn("读取角色权限矩阵失败，返回静态默认矩阵: {}", ex.getMessage());
            return new RoleMatrixView(RoleMatrix.ROLES, RoleMatrix.MODULES, RoleMatrix.matrix());
        }
        if (grantedByRole.isEmpty() && mapper.countRolePermissions() == 0) {
            return new RoleMatrixView(RoleMatrix.ROLES, RoleMatrix.MODULES, RoleMatrix.matrix());
        }
        Map<String, Map<String, Boolean>> matrix = new LinkedHashMap<>();
        for (String role : RoleMatrix.ROLES) {
            Set<String> granted = grantedByRole.getOrDefault(role, Set.of());
            Map<String, Boolean> row = new LinkedHashMap<>();
            for (String module : RoleMatrix.MODULES) {
                row.put(module, granted.contains(module));
            }
            matrix.put(role, row);
        }
        return new RoleMatrixView(RoleMatrix.ROLES, RoleMatrix.MODULES, matrix);
    }

    /**
     * 编辑某角色对某模块的授权。allowed=true 授予（幂等），allowed=false 撤销。
     */
    @Transactional
    public void updatePermission(String roleCode, String module, boolean allowed) {
        if (!RoleMatrix.ROLES.contains(roleCode)) {
            throw new IllegalArgumentException("未知角色: " + roleCode);
        }
        if (!RoleMatrix.MODULES.contains(module)) {
            throw new IllegalArgumentException("未知模块: " + module);
        }
        if (allowed) {
            grantQuietly(roleCode, module);
        } else {
            revokeQuietly(roleCode, module);
        }
    }

    /**
     * 后台模块守卫：当前登录用户的任一角色必须拥有该模块权限，否则抛 403。
     * 由 GlobalExceptionHandler 映射为 FORBIDDEN。
     */
    public void requireModule(String module) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new AccessDeniedException("未认证");
        }
        for (String role : principal.roles()) {
            if (hasPermission(role, module)) {
                return;
            }
        }
        throw new AccessDeniedException("缺少模块权限: " + module);
    }

    private boolean hasPermission(String roleCode, String module) {
        try {
            return Boolean.TRUE.equals(mapper.hasPermission(roleCode, module));
        } catch (RuntimeException ex) {
            // schema 缺失等异常时，保守放行（避免把系统管理员锁在控制台外）。
            log.warn("校验模块权限失败，放行 role={} module={}: {}", roleCode, module, ex.getMessage());
            return true;
        }
    }

    private void ensurePermission(String module) {
        if (mapper.findPermissionIdByCode(module) == null) {
            mapper.insertPermission(module);
        }
    }

    private void grantQuietly(String roleCode, String module) {
        Long roleId = mapper.findRoleIdByCode(roleCode);
        Long permissionId = mapper.findPermissionIdByCode(module);
        if (roleId == null || permissionId == null) {
            return;
        }
        if (!Boolean.TRUE.equals(mapper.hasPermission(roleCode, module))) {
            mapper.insertRolePermission(roleId, permissionId);
        }
    }

    private void revokeQuietly(String roleCode, String module) {
        Long roleId = mapper.findRoleIdByCode(roleCode);
        Long permissionId = mapper.findPermissionIdByCode(module);
        if (roleId == null || permissionId == null) {
            return;
        }
        mapper.deleteRolePermission(roleId, permissionId);
    }
}
