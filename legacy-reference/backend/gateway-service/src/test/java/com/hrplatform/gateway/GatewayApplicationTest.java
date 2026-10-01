package com.hrplatform.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayApplicationTest {
    @Test
    void applicationClassIsAvailable() {
        assertThat(GatewayApplication.class).isNotNull();
    }
}
