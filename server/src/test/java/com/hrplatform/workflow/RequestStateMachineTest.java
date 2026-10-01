package com.hrplatform.workflow;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestStateMachineTest {
    @Test
    void submitAndTwoApprovalsReachApproved() {
        assertThat(RequestStateMachine.submit("DRAFT")).isEqualTo("PENDING_DEPARTMENT");
        assertThat(RequestStateMachine.approve("PENDING_DEPARTMENT", "DEPARTMENT",
                Set.of("DEPT_MANAGER"))).isEqualTo("PENDING_HR");
        assertThat(RequestStateMachine.approve("PENDING_HR", "HR",
                Set.of("HR_ADMIN"))).isEqualTo("APPROVED");
    }

    @Test
    void wrongNodeAndDuplicateApprovalAreRejected() {
        assertThatThrownBy(() -> RequestStateMachine.approve("PENDING_HR", "DEPARTMENT",
                Set.of("DEPT_MANAGER"))).isInstanceOf(RequestStateException.class);
        assertThatThrownBy(() -> RequestStateMachine.approve("APPROVED", "HR",
                Set.of("HR_ADMIN"))).isInstanceOf(RequestStateException.class);
    }

    @Test
    void rejectIsAllowedOnlyAtApprovalNodes() {
        assertThat(RequestStateMachine.reject("PENDING_DEPARTMENT")).isEqualTo("REJECTED");
        assertThatThrownBy(() -> RequestStateMachine.reject("DRAFT"))
                .isInstanceOf(RequestStateException.class);
    }
}
