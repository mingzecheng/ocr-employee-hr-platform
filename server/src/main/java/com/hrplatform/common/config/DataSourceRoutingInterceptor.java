package com.hrplatform.common.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Zero-intrusion read/write split router for MyBatis.
 *
 * <p>Routes purely by {@link SqlCommandType} (no annotations, no Mapper changes):
 * <ul>
 *   <li>INSERT / UPDATE / DELETE → master, and arms a short force-master window so a read issued
 *       right after the write within the same request still sees the fresh data.</li>
 *   <li>SELECT → replica, <b>unless</b> a write transaction is currently active (the connection is
 *       already bound to the master, and in-transaction reads must see our own writes), the
 *       force-master window is open, or no replica should be used for other reasons.</li>
 * </ul>
 * The per-statement route is cleared in a {@code finally} block; the force-master window is cleared
 * at request end by {@link DataSourceRouteCleanupFilter}.
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
        })
})
public class DataSourceRoutingInterceptor implements Interceptor {

    /** How long a write keeps forcing subsequent reads onto the master. */
    static final long FORCE_MASTER_WINDOW_MS = 3000L;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        SqlCommandType type = ms.getSqlCommandType();
        try {
            if (type == SqlCommandType.SELECT) {
                routeSelect();
            } else {
                DataSourceRouteContext.setRoute(DataSourceRouteContext.MASTER);
                DataSourceRouteContext.forceMasterFor(FORCE_MASTER_WINDOW_MS);
            }
            return invocation.proceed();
        } finally {
            DataSourceRouteContext.clearRoute();
        }
    }

    private void routeSelect() {
        boolean writeTransactionActive = TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        if (writeTransactionActive || DataSourceRouteContext.isForceMasterActive()) {
            DataSourceRouteContext.setRoute(DataSourceRouteContext.MASTER);
        } else {
            DataSourceRouteContext.setRoute(DataSourceRouteContext.REPLICA);
        }
    }
}
