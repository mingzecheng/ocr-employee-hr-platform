package com.hrplatform.employee;

import com.hrplatform.common.security.JwtPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmployeeControllerPrivacyTest {
    private final EmployeeService service = mock(EmployeeService.class);
    private final EmployeeController controller = new EmployeeController(service);

    @Test
    void employeePhoneIsMaskedForDepartmentManagerListAndDetail() {
        Employee employee = employee();
        when(service.list(null, null, null, 1, 20, principal(Set.of("DEPT_MANAGER")).dataScope()))
                .thenReturn(new EmployeePage(List.of(employee), 1, 1, 20));
        when(service.getRequired(7L, principal(Set.of("DEPT_MANAGER")).dataScope())).thenReturn(employee);
        var authentication = authentication(Set.of("DEPT_MANAGER"));

        var page = controller.list(null, null, null, 1, 20, authentication).data();
        var detail = controller.get(7L, authentication).data();

        assertThat(page.items().get(0).phone()).isEqualTo("138****8001");
        assertThat(detail.phone()).isEqualTo("138****8001");
    }

    @Test
    void hrAndSystemAdministratorsCanReadBusinessPhone() {
        Employee employee = employee();
        when(service.getRequired(7L, principal(Set.of("HR_ADMIN")).dataScope())).thenReturn(employee);
        var response = controller.get(7L, authentication(Set.of("HR_ADMIN")));

        assertThat(response.data().phone()).isEqualTo("13800138001");
    }

    private Employee employee() {
        return new Employee(7L, "EMP-007", "演示员工", "13800138001", 3L, 4L,
                EmployeeStatus.ACTIVE, null, null);
    }

    private JwtPrincipal principal(Set<String> roles) {
        return new JwtPrincipal(9L, 7L, 3L, roles);
    }

    private UsernamePasswordAuthenticationToken authentication(Set<String> roles) {
        return new UsernamePasswordAuthenticationToken(principal(roles), "", List.of());
    }
}
