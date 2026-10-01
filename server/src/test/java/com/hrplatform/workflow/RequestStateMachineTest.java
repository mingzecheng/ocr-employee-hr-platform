package com.hrplatform.workflow;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestStateMachineTest {
    @Test
    void submitAndTwoApprovalsReachApproved() {
        assertThat(RequestStateMachine.submit("DRAFT")).isEqualTo("PENDING_DEPT_APPROVAL");
        assertThat(RequestStateMachine.approve("PENDING_DEPT_APPROVAL", "DEPARTMENT",
                Set.of("DEPT_MANAGER"))).isEqualTo("PENDING_HR_APPROVAL");
        assertThat(RequestStateMachine.approve("PENDING_HR_APPROVAL", "HR",
                Set.of("HR_ADMIN"))).isEqualTo("APPROVED");
    }

    @Test
    void wrongNodeAndDuplicateApprovalAreRejected() {
        assertThatThrownBy(() -> RequestStateMachine.approve("PENDING_HR_APPROVAL", "DEPARTMENT",
                Set.of("DEPT_MANAGER"))).isInstanceOf(RequestStateException.class);
        assertThatThrownBy(() -> RequestStateMachine.approve("APPROVED", "HR",
                Set.of("HR_ADMIN"))).isInstanceOf(RequestStateException.class);
    }

    @Test
    void rejectIsAllowedOnlyAtApprovalNodes() {
        assertThat(RequestStateMachine.reject("PENDING_DEPT_APPROVAL")).isEqualTo("REJECTED");
        assertThatThrownBy(() -> RequestStateMachine.reject("DRAFT"))
                .isInstanceOf(RequestStateException.class);
    }
}
