package com.hrplatform.common.config;

import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the read/write splitting routing logic. Uses Mockito mocks for the two physical
 * DataSources, so no real MySQL / H2 is required.
 */
class DataSourceRoutingTest {

    private DataSource master;
    private DataSource replica;
    private RoutingDataSource routing;
    private DataSourceRoutingInterceptor interceptor;

    @BeforeEach
    void setUp() throws SQLException {
        DataSourceRouteContext.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);

        master = mock(DataSource.class);
        replica = mock(DataSource.class);
        Connection masterConn = mock(Connection.class);
        Connection replicaConn = mock(Connection.class);
        when(master.getConnection()).thenReturn(masterConn);
        when(replica.getConnection()).thenReturn(replicaConn);

        routing = new RoutingDataSource(master, replica);
        interceptor = new DataSourceRoutingInterceptor();
    }

    @AfterEach
    void tearDown() {
        DataSourceRouteContext.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private MappedStatement statement(SqlCommandType type) {
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getSqlCommandType()).thenReturn(type);
        return ms;
    }

    private Invocation updateInvocation(MappedStatement ms) {
        Invocation inv = mock(Invocation.class);
        when(inv.getArgs()).thenReturn(new Object[]{ms, null});
        return inv;
    }

    private Invocation queryInvocation(MappedStatement ms) {
        Invocation inv = mock(Invocation.class);
        when(inv.getArgs()).thenReturn(new Object[]{ms, null, RowBounds.DEFAULT, null});
        return inv;
    }

    // ---- RoutingDataSource key resolution ----

    @Test
    void routeToReplicaWhenContextSaysReplica() throws SQLException {
        DataSourceRouteContext.setRoute(DataSourceRouteContext.REPLICA);

        routing.getConnection();

        verify(replica).getConnection();
        verify(master, never()).getConnection();
    }

    @Test
    void defaultRouteIsMasterWhenNoContext() throws SQLException {
        routing.getConnection();

        verify(master).getConnection();
        verify(replica, never()).getConnection();
    }

    @Test
    void forceMasterWindowOverridesReplicaRoute() throws SQLException {
        DataSourceRouteContext.forceMasterFor(5000);
        DataSourceRouteContext.setRoute(DataSourceRouteContext.REPLICA);

        routing.getConnection();

        verify(master).getConnection();
        verify(replica, never()).getConnection();
    }

    @Test
    void expiredForceMasterWindowFallsBackToReplicaRoute() throws SQLException {
        DataSourceRouteContext.forceMasterFor(-1000); // already expired
        DataSourceRouteContext.setRoute(DataSourceRouteContext.REPLICA);

        routing.getConnection();

        verify(replica).getConnection();
        verify(master, never()).getConnection();
    }

    @Test
    void replicaFailureFailsOpenToMaster() throws SQLException {
        when(replica.getConnection()).thenThrow(new SQLException("replica down"));
        DataSourceRouteContext.setRoute(DataSourceRouteContext.REPLICA);

        Connection connection = routing.getConnection();

        verify(replica).getConnection();
        verify(master).getConnection();
        assertThat(connection).isNotNull();
    }

    @Test
    void masterFailurePropagatesInsteadOfSwallowing() throws SQLException {
        when(master.getConnection()).thenThrow(new SQLException("master down"));
        // no route set -> defaults to master, master must not be masked
        assertThatThrownBy(() -> routing.getConnection())
                .isInstanceOf(SQLException.class).hasMessageContaining("master down");
    }

    // ---- MyBatis interceptor routing ----

    @Test
    void writeStatementRoutesToMasterAndArmsForceWindow() throws Throwable {
        MappedStatement ms = statement(SqlCommandType.INSERT);
        Invocation inv = updateInvocation(ms);
        when(inv.proceed()).thenAnswer(call -> {
            assertThat(DataSourceRouteContext.getRoute()).isEqualTo(DataSourceRouteContext.MASTER);
            return null;
        });

        interceptor.intercept(inv);

        verify(inv).proceed();
        assertThat(DataSourceRouteContext.isForceMasterActive()).isTrue();
        assertThat(DataSourceRouteContext.getRoute()).isNull(); // cleared after proceed
    }

    @Test
    void readOutsideTransactionRoutesToReplica() throws Throwable {
        MappedStatement ms = statement(SqlCommandType.SELECT);
        Invocation inv = queryInvocation(ms);
        when(inv.proceed()).thenAnswer(call -> {
            assertThat(DataSourceRouteContext.getRoute()).isEqualTo(DataSourceRouteContext.REPLICA);
            return null;
        });

        interceptor.intercept(inv);

        assertThat(DataSourceRouteContext.isForceMasterActive()).isFalse();
    }

    @Test
    void readWithinActiveForceWindowRoutesToMasterForWriteAfterReadConsistency() throws Throwable {
        DataSourceRouteContext.forceMasterFor(3000);
        MappedStatement ms = statement(SqlCommandType.SELECT);
        Invocation inv = queryInvocation(ms);
        when(inv.proceed()).thenAnswer(call -> {
            assertThat(DataSourceRouteContext.getRoute()).isEqualTo(DataSourceRouteContext.MASTER);
            return null;
        });

        interceptor.intercept(inv);
    }

    @Test
    void readInsideWriteTransactionRoutesToMaster() throws Throwable {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        MappedStatement ms = statement(SqlCommandType.SELECT);
        Invocation inv = queryInvocation(ms);
        when(inv.proceed()).thenAnswer(call -> {
            assertThat(DataSourceRouteContext.getRoute()).isEqualTo(DataSourceRouteContext.MASTER);
            return null;
        });

        interceptor.intercept(inv);
    }

    @Test
    void readInsideReadOnlyTransactionRoutesToReplica() throws Throwable {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        MappedStatement ms = statement(SqlCommandType.SELECT);
        Invocation inv = queryInvocation(ms);
        when(inv.proceed()).thenAnswer(call -> {
            assertThat(DataSourceRouteContext.getRoute()).isEqualTo(DataSourceRouteContext.REPLICA);
            return null;
        });

        interceptor.intercept(inv);
    }
}
