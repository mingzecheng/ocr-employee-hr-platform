package com.hrplatform.admin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD：以子类覆写 JVM / 系统 / 应用采集方法注入固定值，验证快照聚合与降级分支。
 * MySQL / Redis 在 jdbcTemplate / redisTemplate 为 null 时必须降级为 null，不抛异常。
 */
class MonitorServiceTest {

    @Test
    void aggregatesOverriddenCollectorsAndDegradesInfrastructureToNull() {
        MonitorService service = new MonitorService(null, null) {
            @Override
            protected MonitorSnapshot.Jvm readJvm() {
                return new MonitorSnapshot.Jvm(256L * 1024 * 1024, 1024L * 1024 * 1024,
                        128L * 1024 * 1024, 42, 8192, 7L, 350L);
            }

            @Override
            protected MonitorSnapshot.SystemInfo readSystem() {
                return new MonitorSnapshot.SystemInfo(8, 0.35, 16L * 1024 * 1024 * 1024,
                        6L * 1024 * 1024 * 1024, 500L * 1024 * 1024 * 1024, 200L * 1024 * 1024 * 1024);
            }

            @Override
            protected MonitorSnapshot.App readApp() {
                return new MonitorSnapshot.App("2023-11-14T00:00:00Z", 120_000L, "1.0.0-test");
            }
        };

        MonitorSnapshot snapshot = service.snapshot();

        assertThat(snapshot.jvm().heapUsed()).isEqualTo(256L * 1024 * 1024);
        assertThat(snapshot.jvm().heapMax()).isEqualTo(1024L * 1024 * 1024);
        assertThat(snapshot.jvm().threadCount()).isEqualTo(42);
        assertThat(snapshot.jvm().gcCount()).isEqualTo(7);
        assertThat(snapshot.system().cpuCores()).isEqualTo(8);
        assertThat(snapshot.system().diskTotal()).isEqualTo(500L * 1024 * 1024 * 1024);
        assertThat(snapshot.app().version()).isEqualTo("1.0.0-test");
        assertThat(snapshot.app().uptimeMs()).isEqualTo(120_000L);
        // 无数据源 / 无 Redis：两段均降级为 null。
        assertThat(snapshot.mysql()).isNull();
        assertThat(snapshot.redis()).isNull();
    }

    @Test
    void liveCollectorProducesNonZeroJvmAndAppValues() {
        // 真实 MXBean：至少堆内存、线程数、运行时间应可读出（不抛异常）。
        MonitorService service = new MonitorService(null, null);
        MonitorSnapshot snapshot = service.snapshot();

        assertThat(snapshot.jvm().heapMax()).isPositive();
        assertThat(snapshot.jvm().threadCount()).isPositive();
        assertThat(snapshot.system().cpuCores()).isPositive();
        assertThat(snapshot.app().uptimeMs()).isGreaterThanOrEqualTo(0L);
        assertThat(snapshot.app().startTime()).isNotBlank();
    }
}
