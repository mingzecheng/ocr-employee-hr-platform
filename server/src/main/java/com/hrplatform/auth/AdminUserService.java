package com.hrplatform.auth;

import com.hrplatform.audit.OperationLogService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminUserService {
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final SysUserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;

    public AdminUserService(SysUserMapper mapper, PasswordEncoder passwordEncoder,
                            OperationLogService operationLogService) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
    }

    @Transactional(readOnly = true)
    public AdminUserPage list(int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        List<AdminUserView> items = mapper.listAdminUsers((safePage - 1) * safeSize, safeSize).stream()
                .map(this::view)
                .toList();
        return new AdminUserPage(items, mapper.countAdminUsers(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public List<AdminRoleOption> roles() {
        return mapper.listEnabledRoles();
    }

    @Transactional
    public AdminUserView create(AdminUserCreateRequest input, Long operatorId) {
        AdminUserCreateRequest request = normalizeCreate(input);
        Set<String> roleCodes = normalizeRoles(request.roleCodes());
        validateRoles(roleCodes);
        validateBinding(roleCodes, request.employeeId(), request.departmentId());
        if (mapper.findAdminUserByUsername(request.username()) != null) {
            throw new AccountAdminStateException("用户名已存在");
        }
        String passwordHash = passwordEncoder.encode(request.password());
        if (mapper.insertAdminUser(request.username(), passwordHash, request.employeeId(), request.departmentId(),
                request.enabled()) != 1) {
            throw new AccountAdminStateException("账号创建失败");
        }
        AdminUserRow created = mapper.findAdminUserByUsername(request.username());
        if (created == null) {
            throw new AccountAdminStateException("账号已创建但无法读取");
        }
        replaceRoles(created.id(), roleCodes);
        record(operatorId, "CREATE", created.id());
        return view(created);
    }

    @Transactional
    public AdminUserView update(Long id, AdminUserUpdateRequest input, Long operatorId) {
        AdminUserRow current = mapper.findAdminUser(id);
        if (current == null) {
            throw new AccountAdminStateException("账号不存在: " + id);
        }
        AdminUserUpdateRequest request = normalizeUpdate(input);
        Set<String> roleCodes = normalizeRoles(request.roleCodes());
        validateRoles(roleCodes);
        validateBinding(roleCodes, request.employeeId(), request.departmentId());
        boolean currentlySystemAdmin = mapper.findRoles(id).contains(SYSTEM_ADMIN);
        boolean remainsEnabledSystemAdmin = request.enabled() && roleCodes.contains(SYSTEM_ADMIN);
        if (currentlySystemAdmin || roleCodes.contains(SYSTEM_ADMIN)) {
            lockSystemAdminRole();
        }
        if (currentlySystemAdmin && !remainsEnabledSystemAdmin && mapper.countEnabledSystemAdmins() <= 1) {
            throw new AccountAdminStateException("不能移除或停用最后一个系统管理员");
        }
        String passwordHash = StringUtils.hasText(request.password())
                ? passwordEncoder.encode(request.password()) : null;
        if (mapper.updateAdminUser(id, passwordHash, request.employeeId(), request.departmentId(), request.enabled()) != 1) {
            throw new AccountAdminStateException("账号已被其他操作修改或不存在");
        }
        replaceRoles(id, roleCodes);
        AdminUserRow updated = mapper.findAdminUser(id);
        if (updated == null) {
            throw new AccountAdminStateException("账号已更新但无法读取");
        }
        record(operatorId, "UPDATE", id);
        return view(updated);
    }

    private AdminUserView view(AdminUserRow row) {
        return new AdminUserView(row.id(), row.username(), row.employeeId(), row.departmentId(), row.enabled(),
                Set.copyOf(mapper.findRoles(row.id())));
    }

    private void replaceRoles(Long userId, Set<String> roles) {
        mapper.deleteUserRoles(userId);
        for (String role : roles) {
            if (mapper.insertUserRole(userId, role) != 1) {
                throw new AccountAdminStateException("角色分配失败: " + role);
            }
        }
    }

    private void validateRoles(Set<String> roleCodes) {
        Set<String> enabled = mapper.listEnabledRoles().stream().map(AdminRoleOption::code).collect(Collectors.toSet());
        if (!enabled.containsAll(roleCodes)) {
            Set<String> invalid = new LinkedHashSet<>(roleCodes);
            invalid.removeAll(enabled);
            throw new AccountAdminStateException("角色不存在或已停用: " + String.join(",", invalid));
        }
    }

    private void validateBinding(Set<String> roleCodes, Long employeeId, Long departmentId) {
        boolean needsEmployee = roleCodes.stream().anyMatch(role -> !SYSTEM_ADMIN.equals(role));
        if (employeeId == null) {
            if (needsEmployee || departmentId != null) {
                throw new AccountAdminStateException("非系统管理员账号必须绑定在职员工和所属部门");
            }
            return;
        }
        if (departmentId == null) {
            throw new AccountAdminStateException("绑定员工时必须指定所属部门");
        }
        EmployeeBinding employee = mapper.findActiveEmployee(employeeId);
        if (employee == null) {
            throw new AccountAdminStateException("账号只能绑定在职员工");
        }
        if (!departmentId.equals(employee.departmentId())) {
            throw new AccountAdminStateException("账号部门必须与员工部门一致");
        }
    }

    private void lockSystemAdminRole() {
        if (mapper.lockRoleIdByCode(SYSTEM_ADMIN) == null) {
            throw new AccountAdminStateException("系统管理员角色不存在或已删除");
        }
    }

    private AdminUserCreateRequest normalizeCreate(AdminUserCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.username()) || !StringUtils.hasText(request.password())) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        String username = request.username().trim();
        if (username.length() > 64 || request.password().length() < 8 || request.password().length() > 72) {
            throw new IllegalArgumentException("用户名或密码长度不符合要求");
        }
        return new AdminUserCreateRequest(username, request.password(), request.employeeId(), request.departmentId(),
                request.enabled(), request.roleCodes());
    }

    private AdminUserUpdateRequest normalizeUpdate(AdminUserUpdateRequest request) {
        if (request == null || request.roleCodes() == null || request.roleCodes().isEmpty()) {
            throw new IllegalArgumentException("至少分配一个角色");
        }
        if (StringUtils.hasText(request.password()) && (request.password().length() < 8 || request.password().length() > 72)) {
            throw new IllegalArgumentException("密码长度必须为 8-72 位");
        }
        return request;
    }

    private Set<String> normalizeRoles(Set<String> input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("至少分配一个角色");
        }
        Set<String> roles = input.stream()
                .filter(StringUtils::hasText)
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (roles.size() != input.size()) {
            throw new IllegalArgumentException("角色集合包含空值或重复值");
        }
        return roles;
    }

    private void record(Long operatorId, String action, Long objectId) {
        if (operationLogService != null) {
            operationLogService.record(operatorId, action, "SYS_USER", objectId, "SUCCESS");
        }
    }
}
