package com.hrplatform.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityApplicationTest {
    @Test
    void applicationClassIsAvailable() {
        assertThat(IdentityApplication.class).isNotNull();
    }
}
