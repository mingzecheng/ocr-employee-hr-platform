package com.hrplatform.common.config;

import org.apache.ibatis.plugin.Interceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Replaces the single auto-configured datasource with two physical datasources (master / replica)
 * behind a {@link RoutingDataSource}.
 *
 * <p>By default the demo/development environment points master and replica at the same MySQL
 * instance (see {@code spring.datasource.replica.url} falling back to {@code MYSQL_URL}); a real
 * read replica is wired in production via {@code MYSQL_REPLICA_URL}. The routing logic itself works
 * identically in both setups.
 *
 * <p>Properties bind to {@link DataSourceProperties} (which exposes {@code url}) and are turned into
 * a pooled datasource via {@link DataSourceProperties#initializeDataSourceBuilder()}, so the
 * {@code url} property is correctly translated to Hikari's {@code jdbcUrl}. MyBatis picks up the
 * {@link Interceptor} bean automatically; the {@code @Primary} routing datasource is what MyBatis,
 * Flyway and the transaction manager all use.
 */
@Configuration
public class DataSourceRoutingConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.master")
    public DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.replica")
    public DataSourceProperties replicaDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource masterDataSource(DataSourceProperties masterDataSourceProperties) {
        return masterDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean
    public DataSource replicaDataSource(DataSourceProperties replicaDataSourceProperties) {
        return replicaDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean
    @Primary
    public DataSource routingDataSource(DataSource masterDataSource, DataSource replicaDataSource) {
        return new RoutingDataSource(masterDataSource, replicaDataSource);
    }

    @Bean
    @ConditionalOnMissingBean(DataSourceRoutingInterceptor.class)
    public Interceptor dataSourceRoutingInterceptor() {
        return new DataSourceRoutingInterceptor();
    }

    @Bean
    public DataSourceRouteCleanupFilter dataSourceRouteCleanupFilter() {
        return new DataSourceRouteCleanupFilter();
    }
}
