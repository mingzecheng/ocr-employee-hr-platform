package com.hrplatform.common.config;

import com.hrplatform.audit.OperationLogService;
import com.hrplatform.auth.SysUser;
import com.hrplatform.auth.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionAdminBootstrapTest {
    @Mock
    private SysUserMapper mapper;

    @Mock
    private OperationLogService operationLogService;

    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
    }

    @Test
    void createsOneSystemAdminWhenNoEnabledAdminExists() {
        when(mapper.countEnabledSystemAdmins()).thenReturn(0L, 0L);
        when(mapper.lockRoleIdByCode("SYSTEM_ADMIN")).thenReturn(1L);
        when(mapper.findByUsername("company-admin")).thenReturn(null, new SysUser(
                7L, "company-admin", "hash", null, null, true, Set.of("SYSTEM_ADMIN")
        ));
        when(mapper.insertAdminUser(eq("company-admin"), org.mockito.ArgumentMatchers.anyString(),
                isNull(), isNull(), eq(true))).thenReturn(1);
        when(mapper.insertUserRole(7L, "SYSTEM_ADMIN")).thenReturn(1);
        ProductionAdminBootstrap bootstrap = new ProductionAdminBootstrap(
                mapper, passwordEncoder, operationLogService, "company-admin", "Long-Initial-Secret-2026"
        );

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<String> passwordHash = ArgumentCaptor.forClass(String.class);
        verify(mapper).insertAdminUser(eq("company-admin"), passwordHash.capture(), isNull(), isNull(), eq(true));
        assertThat(passwordEncoder.matches("Long-Initial-Secret-2026", passwordHash.getValue())).isTrue();
        verify(mapper).insertUserRole(7L, "SYSTEM_ADMIN");
        verify(operationLogService).record(null, "BOOTSTRAP_CREATE", "SYS_USER", 7L, "SUCCESS");
    }

    @Test
    void leavesExistingSystemAdminUntouched() {
        when(mapper.countEnabledSystemAdmins()).thenReturn(1L);
        ProductionAdminBootstrap bootstrap = new ProductionAdminBootstrap(mapper, passwordEncoder,
                operationLogService, "", "");

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        verify(mapper, never()).insertAdminUser(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.nullable(Long.class),
                org.mockito.ArgumentMatchers.nullable(Long.class), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void refusesFirstStartupWhenBootstrapCredentialsAreMissing() {
        when(mapper.countEnabledSystemAdmins()).thenReturn(0L);
        ProductionAdminBootstrap bootstrap = new ProductionAdminBootstrap(mapper, passwordEncoder,
                operationLogService, "", "");

        assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_USERNAME")
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD");
    }
}
