package com.hrplatform.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRouteConfigurationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void definesBusinessRoutesWithLocalServiceTargets() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).extracting(Route::getId).containsExactlyInAnyOrder(
                "identity-auth", "identity-departments", "archive-employees",
                "archive-api", "archive-statistics", "workflow-requests", "workflow-approvals",
                "workflow-access", "workflow-uses", "workflow-inventory", "workflow-todos");
        assertThat(routes).allMatch(route -> route.getUri().toString().startsWith("http://127.0.0.1:808"));
        assertThat(routes).noneMatch(route -> route.getUri().toString().contains("sqlite"));
    }
}
