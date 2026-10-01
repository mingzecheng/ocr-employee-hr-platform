package com.hrplatform.identity.user;

import com.hrplatform.identity.org.DepartmentMapper;
import com.hrplatform.identity.org.DepartmentNode;
import com.hrplatform.identity.role.SysRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SysUserMapperTest {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Test
    void findsSeededEnabledAdminWithoutReturningPassword() {
        SysUser user = userMapper.findEnabledByUsername("admin");

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("admin");
        assertThat(user.getPasswordHash()).isNotBlank();
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void returnsStableRolesAndOnlyActiveDepartments() {
        SysUser user = userMapper.findEnabledByUsername("admin");

        assertThat(roleMapper.findCodesByUserId(user.getId()))
                .containsExactly("SYSTEM_ADMIN");

        List<DepartmentNode> departments = departmentMapper.findActiveTree();
        assertThat(departments).extracting(DepartmentNode::getCode)
                .contains("HQ");
        assertThat(departments).allMatch(DepartmentNode::isActive);
    }
}
