package com.oiep.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
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
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Explicit HikariCP {@link javax.sql.DataSource} for SQLite.
 *
 * <p>Why not rely on auto-configuration: SQLite creates a database file but never its parent
 * directory. We therefore create the directory before the context finishes booting, which
 * also makes {@code schema.sql} initialization (and the demo seeder) work on a clean checkout
 * with no {@code data/} directory. Pool sizing, busy timeout and pragmas are supplied through
 * {@code spring.datasource.hikari.*} and the JDBC URL.</p>
 *
 * <p>On POSIX filesystems the database directory is restricted to the owner and the database
 * file (plus SQLite's {@code -wal}/{@code -shm} siblings) are set to {@code 0600} once created.
 * The database contains every submitted note, including moderation-hidden ones, so another
 * local user on a bare-metal host must not be able to read it. The Docker image additionally
 * owns {@code /data} as the non-root app user. Non-POSIX filesystems (Windows) are left to
 * their native ACLs.</p>
 */
@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    private static final String SQLITE_URL_PREFIX = "jdbc:sqlite:";
    private static final Set<PosixFilePermission> OWNER_DIRECTORY_ONLY =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> OWNER_FILE_ONLY =
            PosixFilePermissions.fromString("rw-------");

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
     * Runs after schema initialization, when SQLite has actually created the database file,
     * and restricts it (and any WAL siblings) to the application user.
     */
    @Bean
    public ApplicationRunner hardenSqliteFilePermissions(DataSourceProperties properties) {
        return args -> hardenDatabaseFile(properties.determineUrl());
    }

    /**
     * Creates the parent directory of a file-backed SQLite database and restricts it to the
     * owner where the filesystem supports POSIX permissions. In-memory URLs are ignored.
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
        restrictToOwner(parent, OWNER_DIRECTORY_ONLY);
    }

    /**
     * Restricts an existing database file and its WAL sidecar files to owner-only access.
     * Best-effort: non-POSIX filesystems (Windows) do not support POSIX modes and are left to
     * their native ACLs.
     */
    static void hardenDatabaseFile(String jdbcUrl) {
        Path databaseFile = sqliteDatabasePath(jdbcUrl);
        if (databaseFile == null) {
            return;
        }
        restrictToOwner(databaseFile, OWNER_FILE_ONLY);
        for (String suffix : new String[]{"-wal", "-shm"}) {
            restrictToOwner(databaseFile.resolveSibling(databaseFile.getFileName() + suffix), OWNER_FILE_ONLY);
        }
    }

    private static void restrictToOwner(Path path, Set<PosixFilePermission> permissions) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ex) {
            // Windows/non-POSIX filesystem: native ACLs apply; nothing to do.
            log.debug("POSIX file permissions are not supported on this filesystem; leaving {} to native ACLs", path);
        } catch (IOException ex) {
            // Never fail startup because of a best-effort hardening step; log for operators.
            log.warn("Could not restrict permissions on {}: {}", path, ex.getMessage());
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
