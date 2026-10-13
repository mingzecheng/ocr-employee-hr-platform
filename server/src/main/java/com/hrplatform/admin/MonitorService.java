package com.hrplatform.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Properties;

/**
 * 采集服务器运行态：JVM / 操作系统 / MySQL / Redis / 应用本身。
 *
 * <p>设计要点：
 * <ul>
 *   <li>JVM 与系统信息来自 JDK 内置 MXBean，无第三方依赖（未引入 OSHI）；</li>
 *   <li>MySQL 通过 {@code SHOW GLOBAL STATUS} 查询连接数与运行时间，H2 / 无连接时降级为 {@code null}；</li>
 *   <li>Redis 通过 {@code INFO} 命令采样内存 / 客户端 / 命中率，连接失败时整段降级为 {@code null}；</li>
 *   <li>各采集方法为 {@code protected}，便于单测以子类覆写注入固定值（TDD）。</li>
 * </ul>
 */
@Service
public class MonitorService {
    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public MonitorService(@Autowired(required = false) JdbcTemplate jdbcTemplate,
                         @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    public MonitorSnapshot snapshot() {
        return new MonitorSnapshot(readJvm(), readSystem(), readMysql(), readRedis(), readApp());
    }

    protected MonitorSnapshot.Jvm readJvm() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();

        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            long time = gc.getCollectionTime();
            if (count > 0) {
                gcCount += count;
            }
            if (time > 0) {
                gcTime += time;
            }
        }
        return new MonitorSnapshot.Jvm(heap.getUsed(), heap.getMax(), nonHeap.getUsed(),
                threads.getThreadCount(), classes.getLoadedClassCount(), gcCount, gcTime);
    }

    protected MonitorSnapshot.SystemInfo readSystem() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        long totalMem = 0;
        long freeMem = 0;
        double cpuLoad = -1;
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            totalMem = sunOs.getTotalMemorySize();
            freeMem = sunOs.getFreeMemorySize();
            cpuLoad = sunOs.getCpuLoad();
        }
        long diskTotal = 0;
        long diskFree = 0;
        for (File root : File.listRoots()) {
            diskTotal += root.getTotalSpace();
            diskFree += root.getUsableSpace();
        }
        return new MonitorSnapshot.SystemInfo(os.getAvailableProcessors(),
                cpuLoad >= 0 ? cpuLoad : os.getSystemLoadAverage(),
                totalMem, totalMem - freeMem, diskTotal, diskFree);
    }

    protected MonitorSnapshot.App readApp() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        String version = MonitorService.class.getPackage().getImplementationVersion();
        if (version == null || version.isBlank()) {
            version = "1.0.0";
        }
        return new MonitorSnapshot.App(
                java.time.Instant.ofEpochMilli(runtime.getStartTime()).toString(),
                runtime.getUptime(), version);
    }

    protected MonitorSnapshot.Mysql readMysql() {
        if (jdbcTemplate == null) {
            return null;
        }
        try {
            Long threadsConnected = queryGlobalStatus("Threads_connected");
            Long uptime = queryGlobalStatus("Uptime");
            if (threadsConnected == null && uptime == null) {
                return null;
            }
            return new MonitorSnapshot.Mysql(
                    threadsConnected == null ? null : threadsConnected.intValue(), uptime);
        } catch (RuntimeException ex) {
            // 测试库 H2 不支持 SHOW GLOBAL STATUS，或主库暂不可达时整段降级。
            log.debug("MySQL 状态采集降级：{}", ex.getMessage());
            return null;
        }
    }

    /**
     * 查询单个全局状态变量。{@code SHOW GLOBAL STATUS LIKE 'X'} 返回
     * {@code Variable_name / Value} 两列，这里取出 Value 列（列名大小写随驱动而异）。
     */
    private Long queryGlobalStatus(String variable) {
        try {
            List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SHOW GLOBAL STATUS LIKE '" + variable + "'");
            for (java.util.Map<String, Object> row : rows) {
                Object value = row.get("Value");
                if (value == null) {
                    for (java.util.Map.Entry<String, Object> e : row.entrySet()) {
                        if (!e.getKey().equalsIgnoreCase("Variable_name") && e.getValue() != null) {
                            value = e.getValue();
                            break;
                        }
                    }
                }
                if (value != null) {
                    return Long.parseLong(String.valueOf(value).trim());
                }
            }
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    protected MonitorSnapshot.Redis readRedis() {
        if (redisTemplate == null) {
            return null;
        }
        try {
            Properties info = fetchRedisInfo();
            if (info == null || info.isEmpty()) {
                return null;
            }
            Long usedMemory = parseLong(info.getProperty("used_memory"));
            Integer clients = parseLong(info.getProperty("connected_clients")) == null
                    ? null : parseLong(info.getProperty("connected_clients")).intValue();
            Long hits = parseLong(info.getProperty("keyspace_hits"));
            Long misses = parseLong(info.getProperty("keyspace_misses"));
            return new MonitorSnapshot.Redis(usedMemory, clients, hits, misses);
        } catch (RuntimeException ex) {
            log.debug("Redis 状态采集降级：{}", ex.getMessage());
            return null;
        }
    }

    private Properties fetchRedisInfo() {
        RedisConnection connection = ObjectsNotNull.redisConnection(redisTemplate);
        if (connection == null) {
            return null;
        }
        try {
            Properties info = connection.info();
            return info == null ? new Properties() : info;
        } finally {
            try {
                connection.close();
            } catch (RuntimeException ignored) {
                // 连接归还失败不影响采样结果。
            }
        }
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 内部工具：隔离 redisTemplate 连接获取，便于阅读。 */
    private static final class ObjectsNotNull {
        static RedisConnection redisConnection(StringRedisTemplate redisTemplate) {
            try {
                return redisTemplate.getConnectionFactory() == null
                        ? null : redisTemplate.getConnectionFactory().getConnection();
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }
}
