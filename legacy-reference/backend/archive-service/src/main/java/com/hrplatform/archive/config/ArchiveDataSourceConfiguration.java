package com.hrplatform.archive.config;

import com.hrplatform.common.datasource.DataSourceProperties;
import com.hrplatform.common.datasource.PrimaryDataSourceAspect;
import com.hrplatform.common.datasource.ReadWriteRoutingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(DataSourceProperties.class)
@MapperScan(basePackages = "com.hrplatform.archive", annotationClass = Mapper.class)
public class ArchiveDataSourceConfiguration {

    @Bean
    public DataSource writeDataSource(DataSourceProperties properties) {
        return createDataSource(properties.getWrite(), false);
    }

    @Bean
    public DataSource readDataSource(DataSourceProperties properties) {
        return createDataSource(properties.getRead(), true);
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("writeDataSource") DataSource writeDataSource,
                                 @Qualifier("readDataSource") DataSource readDataSource) {
        ReadWriteRoutingDataSource routingDataSource = new ReadWriteRoutingDataSource();
        routingDataSource.setTargetDataSources(Map.of("WRITE", writeDataSource, "READ", readDataSource));
        routingDataSource.setDefaultTargetDataSource(writeDataSource);
        routingDataSource.setLenientFallback(false);
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }

    @Bean
    public PrimaryDataSourceAspect primaryDataSourceAspect() {
        return new PrimaryDataSourceAspect();
    }

    @Bean
    public PlatformTransactionManager transactionManager(@Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    private HikariDataSource createDataSource(DataSourceProperties.PoolProperties properties,
                                               boolean readOnly) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(require(properties.getUrl(), "url"));
        config.setUsername(require(properties.getUsername(), "username"));
        config.setPassword(properties.getPassword());
        config.setMaximumPoolSize(properties.getMaximumPoolSize());
        config.setMinimumIdle(properties.getMinimumIdle());
        config.setConnectionTimeout(properties.getConnectionTimeout());
        config.setReadOnly(readOnly);
        return new HikariDataSource(config);
    }

    private String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Datasource " + name + " must be configured");
        }
        return value;
    }
}
