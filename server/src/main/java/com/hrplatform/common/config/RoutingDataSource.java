package com.hrplatform.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Routes connections to the master (write) or a replica (read) datasource based on
 * {@link DataSourceRouteContext}.
 *
 * <p>The lookup key defaults to {@code master}. When a replica connection cannot be acquired
 * (replica down / network partition), the datasource fails open onto the master and logs a warning,
 * so a presentation/demo never collapses because of an unavailable read replica.
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(RoutingDataSource.class);

    private final DataSource master;
    private final DataSource replica;

    public RoutingDataSource(DataSource master, DataSource replica) {
        this.master = master;
        this.replica = replica;
        Map<Object, Object> targets = new HashMap<>();
        targets.put(DataSourceRouteContext.MASTER, master);
        targets.put(DataSourceRouteContext.REPLICA, replica);
        super.setTargetDataSources(targets);
        super.setDefaultTargetDataSource(master);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        if (DataSourceRouteContext.isForceMasterActive()) {
            return DataSourceRouteContext.MASTER;
        }
        String route = DataSourceRouteContext.getRoute();
        return route != null ? route : DataSourceRouteContext.MASTER;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return obtainConnection(null, null);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return obtainConnection(username, password);
    }

    private Connection obtainConnection(String username, String password) throws SQLException {
        String key = (String) determineCurrentLookupKey();
        try {
            return open(key, username, password);
        } catch (SQLException ex) {
            if (DataSourceRouteContext.REPLICA.equals(key)) {
                log.warn("Replica datasource unavailable, failing over to master: {}", ex.getMessage());
                return open(DataSourceRouteContext.MASTER, username, password);
            }
            throw ex;
        }
    }

    private Connection open(String key, String username, String password) throws SQLException {
        DataSource target = DataSourceRouteContext.REPLICA.equals(key) ? replica : master;
        return username == null ? target.getConnection() : target.getConnection(username, password);
    }
}
