package com.hrplatform.admin;

/**
 * 服务器监控快照。字段名与前端 {@code ServerMonitor} 类型一一对应。
 * mysql / redis 段在对应中间件不可用时返回 {@code null}（降级），
 * jvm / system / app 段取自本机 MXBean，始终可用。
 */
public record MonitorSnapshot(Jvm jvm, SystemInfo system, Mysql mysql, Redis redis, App app) {

    public record Jvm(long heapUsed, long heapMax, long nonHeapUsed,
                      int threadCount, long classLoaded, long gcCount, long gcTimeMs) {
    }

    public record SystemInfo(int cpuCores, double systemLoad,
                            long physicalMemTotal, long physicalMemUsed,
                            long diskTotal, long diskFree) {
    }

    public record Mysql(Integer threadsConnected, Long uptimeSec) {
    }

    public record Redis(Long usedMemory, Integer connectedClients, Long hits, Long misses) {
    }

    public record App(String startTime, long uptimeMs, String version) {
    }
}
