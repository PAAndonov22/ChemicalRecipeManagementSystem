package com.crms.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DatabaseConfig {
    @Bean
    public DataSource dataSource(@Value("${crms.database.path}") String configuredPath) throws IOException {
        Path databasePath = Path.of(configuredPath).toAbsolutePath().normalize();
        if (databasePath.getParent() != null) {
            Files.createDirectories(databasePath.getParent());
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + databasePath);
        config.setConnectionInitSql("PRAGMA foreign_keys=ON;");
        config.setMaximumPoolSize(1);
        config.setPoolName("crms-sqlite");
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
