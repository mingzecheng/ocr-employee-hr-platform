package com.hrplatform.common.config;

/**
 * Thread-local holder for the current datasource routing decision.
 *
 * <p>The per-statement route ({@link #MASTER} / {@link #REPLICA}) is set by the MyBatis interceptor
 * right before each statement and cleared right after. The force-master window outlives a single
 * statement so that reads issued shortly after a write in the same request still hit the primary
 * (write-after-read consistency). Both values are cleared at the end of every web request by
 * {@link DataSourceRouteCleanupFilter} so nothing leaks onto a recycled servlet thread.
 */
public final class DataSourceRouteContext {

    public static final String MASTER = "master";
    public static final String REPLICA = "replica";

    private static final ThreadLocal<String> CURRENT_ROUTE = new ThreadLocal<>();
    private static final ThreadLocal<Long> FORCE_MASTER_UNTIL_MILLIS = new ThreadLocal<>();

    private DataSourceRouteContext() {
    }

    public static void setRoute(String route) {
        CURRENT_ROUTE.set(route);
    }

    public static String getRoute() {
        return CURRENT_ROUTE.get();
    }

    public static void clearRoute() {
        CURRENT_ROUTE.remove();
    }

    /** Arm a window during which every read is forced to the primary. */
    public static void forceMasterFor(long millis) {
        FORCE_MASTER_UNTIL_MILLIS.set(System.currentTimeMillis() + millis);
    }

    public static boolean isForceMasterActive() {
        Long until = FORCE_MASTER_UNTIL_MILLIS.get();
        return until != null && until > System.currentTimeMillis();
    }

    /** Full cleanup — invoked at request end and from tests. */
    public static void clear() {
        CURRENT_ROUTE.remove();
        FORCE_MASTER_UNTIL_MILLIS.remove();
    }
}
