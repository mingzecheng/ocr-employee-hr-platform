package com.hrplatform.access;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveAccessStateMachineTest {
    @Test
    void normalAccessLifecycle() {
        assertThat(ArchiveAccessStateMachine.submit("DRAFT")).isEqualTo("PENDING_APPROVAL");
        assertThat(ArchiveAccessStateMachine.approve("PENDING_APPROVAL")).isEqualTo("APPROVED");
        assertThat(ArchiveAccessStateMachine.use("APPROVED")).isEqualTo("IN_USE");
        assertThat(ArchiveAccessStateMachine.returnState("IN_USE", false)).isEqualTo("RETURNED");
    }

    @Test
    void abnormalReturnAndDuplicateUseAreRejected() {
        assertThat(ArchiveAccessStateMachine.returnState("IN_USE", true)).isEqualTo("ABNORMAL");
        assertThatThrownBy(() -> ArchiveAccessStateMachine.use("IN_USE"))
                .isInstanceOf(ArchiveAccessStateException.class);
    }
}
