package com.hrplatform.organization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {
    @Mock
    private OrganizationMapper mapper;

    @Test
    void departmentTreeBuildsNestedChildrenInQueryOrder() {
        when(mapper.listEnabledDepartments()).thenReturn(List.of(
                new DepartmentNode(1L, "HQ", "总部", null, List.of()),
                new DepartmentNode(2L, "HR", "人事部", 1L, List.of()),
                new DepartmentNode(3L, "OPS", "运营部", 1L, List.of())
        ));

        List<DepartmentNode> result = new OrganizationService(mapper).departmentTree();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("总部");
        assertThat(result.get(0).children()).extracting(DepartmentNode::name)
                .containsExactly("人事部", "运营部");
    }
}
