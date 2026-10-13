package com.hrplatform.auth;

import com.hrplatform.audit.OperationLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock
    private SysUserMapper mapper;
    @Mock
    private OperationLogService operationLogService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void ordinaryAccountMustBindAnActiveEmployeeInTheSameDepartment() {
        when(mapper.listEnabledRoles()).thenReturn(List.of(new AdminRoleOption("EMPLOYEE", "员工")));
        when(mapper.findActiveEmployee(20L)).thenReturn(new EmployeeBinding(20L, 3L, "ACTIVE"));

        assertThatThrownBy(() -> service().create(new AdminUserCreateRequest(
                        "staff", "password123", 20L, 4L, true, Set.of("EMPLOYEE")), 9L))
                .isInstanceOf(AccountAdminStateException.class)
                .hasMessageContaining("部门必须与员工部门一致");

        verify(mapper, never()).insertAdminUser(any(), any(), any(), any(), any(Boolean.class));
    }

    @Test
    void systemAdminCanBeCreatedWithoutEmployeeBindingAndPasswordIsHashed() {
        when(mapper.listEnabledRoles()).thenReturn(List.of(new AdminRoleOption("SYSTEM_ADMIN", "系统管理员")));
        when(mapper.findAdminUserByUsername("sys-2")).thenReturn(null,
                new AdminUserRow(12L, "sys-2", null, null, true));
        when(mapper.insertAdminUser(eq("sys-2"), any(), eq(null), eq(null), eq(true))).thenReturn(1);
        when(mapper.deleteUserRoles(12L)).thenReturn(0);
        when(mapper.insertUserRole(12L, "SYSTEM_ADMIN")).thenReturn(1);
        when(mapper.findRoles(12L)).thenReturn(Set.of("SYSTEM_ADMIN"));

        AdminUserView result = service().create(new AdminUserCreateRequest(
                "sys-2", "password123", null, null, true, Set.of("SYSTEM_ADMIN")), 9L);

        assertThat(result.roleCodes()).containsExactly("SYSTEM_ADMIN");
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(mapper).insertAdminUser(eq("sys-2"), hash.capture(), eq(null), eq(null), eq(true));
        assertThat(hash.getValue()).startsWith("$2");
        assertThat(encoder.matches("password123", hash.getValue())).isTrue();
        verify(operationLogService).record(9L, "CREATE", "SYS_USER", 12L, "SUCCESS");
    }

    @Test
    void disabledRoleCannotBeAssigned() {
        when(mapper.listEnabledRoles()).thenReturn(List.of(new AdminRoleOption("EMPLOYEE", "员工")));

        assertThatThrownBy(() -> service().create(new AdminUserCreateRequest(
                        "staff", "password123", 20L, 3L, true, Set.of("HR_ADMIN")), 9L))
                .isInstanceOf(AccountAdminStateException.class)
                .hasMessageContaining("不存在或已停用");
    }

    @Test
    void updateReplacesTheCompleteRoleSetAndOptionalPassword() {
        when(mapper.findAdminUser(12L)).thenReturn(new AdminUserRow(12L, "staff", 20L, 3L, true),
                new AdminUserRow(12L, "staff", 21L, 4L, true));
        when(mapper.findRoles(12L)).thenReturn(Set.of("EMPLOYEE"));
        when(mapper.listEnabledRoles()).thenReturn(List.of(new AdminRoleOption("EMPLOYEE", "员工"),
                new AdminRoleOption("DEPT_MANAGER", "部门负责人")));
        when(mapper.findActiveEmployee(21L)).thenReturn(new EmployeeBinding(21L, 4L, "ACTIVE"));
        when(mapper.deleteUserRoles(12L)).thenReturn(1);
        when(mapper.insertUserRole(12L, "DEPT_MANAGER")).thenReturn(1);
        when(mapper.updateAdminUser(eq(12L), any(), eq(21L), eq(4L), eq(true))).thenReturn(1);
        when(mapper.findRoles(12L)).thenReturn(Set.of("DEPT_MANAGER"));

        AdminUserView updated = service().update(12L,
                new AdminUserUpdateRequest(21L, 4L, true, "newpass123", Set.of("DEPT_MANAGER")), 9L);

        assertThat(updated.roleCodes()).containsExactly("DEPT_MANAGER");
        verify(mapper).deleteUserRoles(12L);
        verify(mapper).insertUserRole(12L, "DEPT_MANAGER");
    }

    @Test
    void lastEnabledSystemAdminCannotBeDisabledOrLoseRole() {
        when(mapper.findAdminUser(12L)).thenReturn(new AdminUserRow(12L, "sys", null, null, true));
        when(mapper.findRoles(12L)).thenReturn(Set.of("SYSTEM_ADMIN"));
        when(mapper.listEnabledRoles()).thenReturn(List.of(new AdminRoleOption("SYSTEM_ADMIN", "系统管理员")));
        when(mapper.lockRoleIdByCode("SYSTEM_ADMIN")).thenReturn(5L);
        when(mapper.countEnabledSystemAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> service().update(12L,
                        new AdminUserUpdateRequest(null, null, false, null, Set.of("SYSTEM_ADMIN")), 9L))
                .isInstanceOf(AccountAdminStateException.class)
                .hasMessageContaining("最后一个系统管理员");

        verify(mapper, never()).updateAdminUser(any(), any(), any(), any(), any(Boolean.class));
    }

    private AdminUserService service() {
        return new AdminUserService(mapper, encoder, operationLogService);
    }
}
