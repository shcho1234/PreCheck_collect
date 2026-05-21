package com.sks.precheck.collect.config;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * 실행 환경별 DataSource 설정을 제공한다.
 *
 * test 프로파일은 PostgreSQL, prod 프로파일은 Altibase 연결을 사용한다.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Profile("test")
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties testDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @Profile("test")
    public DataSource testDataSource(DataSourceProperties testDataSourceProperties) {
        return testDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean
    @Profile("prod")
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties prodDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @Profile("prod")
    public DataSource dataSource(DataSourceProperties prodDataSourceProperties) {
        return prodDataSourceProperties.initializeDataSourceBuilder().build();
    }
}

