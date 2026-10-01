package com.hrplatform.common.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {

    private static final ThreadLocal<Integer> PRIMARY_DEPTH = ThreadLocal.withInitial(() -> 0);

    @Override
    protected Object determineCurrentLookupKey() {
        if (isPrimaryForced()) {
            return "WRITE";
        }
        boolean readOnlyTransaction = TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        return readOnlyTransaction ? "READ" : "WRITE";
    }

    static void pushPrimary() {
        PRIMARY_DEPTH.set(PRIMARY_DEPTH.get() + 1);
    }

    static void popPrimary() {
        int depth = PRIMARY_DEPTH.get() - 1;
        if (depth <= 0) {
            PRIMARY_DEPTH.remove();
        } else {
            PRIMARY_DEPTH.set(depth);
        }
    }

    private static boolean isPrimaryForced() {
        return PRIMARY_DEPTH.get() > 0;
    }
}
