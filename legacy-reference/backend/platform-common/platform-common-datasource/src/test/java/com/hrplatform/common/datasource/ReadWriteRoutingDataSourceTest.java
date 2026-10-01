package com.hrplatform.common.datasource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

class ReadWriteRoutingDataSourceTest {

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void routesToReadWhenAnActualTransactionIsReadOnly() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        assertThat(new ReadWriteRoutingDataSource().determineCurrentLookupKey()).isEqualTo("READ");
    }

    @Test
    void routesToWriteWhenTransactionIsNotReadOnly() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        assertThat(new ReadWriteRoutingDataSource().determineCurrentLookupKey()).isEqualTo("WRITE");
    }

    @Test
    void routesToWriteWhenNoTransactionIsActive() {
        assertThat(new ReadWriteRoutingDataSource().determineCurrentLookupKey()).isEqualTo("WRITE");
    }

    @Test
    void usePrimaryForcesWriteInsideReadOnlyTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        AspectJProxyFactory factory = new AspectJProxyFactory(new WriteService());
        factory.addAspect(new PrimaryDataSourceAspect());
        WriteService proxy = factory.getProxy();

        proxy.writeToPrimary();
    }

    static class WriteService {
        @UsePrimary
        public void writeToPrimary() {
            assertThat(new ReadWriteRoutingDataSource().determineCurrentLookupKey()).isEqualTo("WRITE");
        }
    }
}
