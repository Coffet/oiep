package com.oiep.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Explicit HikariCP {@link javax.sql.DataSource} for SQLite.
 *
 * <p>Why not rely on auto-configuration: SQLite creates a database file but never its parent
 * directory. We therefore create the directory before the context finishes booting, which
 * also makes {@code schema.sql} initialization (and the demo seeder) work on a clean checkout
 * with no {@code data/} directory. Pool sizing, busy timeout and pragmas are supplied through
 * {@code spring.datasource.hikari.*} and the JDBC URL.</p>
 */
@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

    private static final String SQLITE_URL_PREFIX = "jdbc:sqlite:";

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        String jdbcUrl = properties.determineUrl();
        ensureSqliteParentDirectory(jdbcUrl);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);

        String username = properties.determineUsername();
        if (StringUtils.hasText(username)) {
            dataSource.setUsername(username);
        }
        String password = properties.determinePassword();
        if (StringUtils.hasText(password)) {
            dataSource.setPassword(password);
        }
        String driverClassName = properties.determineDriverClassName();
        if (StringUtils.hasText(driverClassName)) {
            dataSource.setDriverClassName(driverClassName);
        }
        return dataSource;
    }

    /**
     * Creates the parent directory of a file-backed SQLite database. In-memory URLs are
     * ignored.
     */
    static void ensureSqliteParentDirectory(String jdbcUrl) {
        Path databaseFile = sqliteDatabasePath(jdbcUrl);
        if (databaseFile == null) {
            return;
        }
        Path parent = databaseFile.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create SQLite database directory: " + parent, ex);
        }
    }

    /**
     * Resolves the file path from a {@code jdbc:sqlite:} URL, ignoring driver query parameters
     * (e.g. {@code ?busy_timeout=5000&journal_mode=WAL}). Returns {@code null} for in-memory
     * URLs.
     */
    static Path sqliteDatabasePath(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith(SQLITE_URL_PREFIX)) {
            return null;
        }
        String location = jdbcUrl.substring(SQLITE_URL_PREFIX.length());
        int queryIndex = location.indexOf('?');
        if (queryIndex >= 0) {
            location = location.substring(0, queryIndex);
        }
        if (location.startsWith("file:")) {
            location = location.substring("file:".length());
        }
        if (location.isEmpty()
                || location.startsWith(":memory:")
                || location.startsWith("file::memory:")
                || location.startsWith("mem:")) {
            return null;
        }
        try {
            return Paths.get(location).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
