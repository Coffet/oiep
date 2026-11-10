package com.oiep.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SQLite file contains every note, including moderation-hidden ones. On POSIX filesystems
 * it and its directory must be owner-only; on non-POSIX filesystems the helpers must be a
 * no-op rather than failing startup.
 */
class DatabaseFilePermissionTests {

    @TempDir
    Path tempDir;

    @Test
    void createsAndRestrictsTheDatabaseParentDirectory() throws Exception {
        Path database = tempDir.resolve("nested/data/oiep.db");
        DataSourceConfig.ensureSqliteParentDirectory(
                "jdbc:sqlite:" + database + "?busy_timeout=5000&journal_mode=WAL");

        Path parent = database.getParent();
        assertThat(Files.isDirectory(parent)).isTrue();
        if (isPosix()) {
            assertThat(Files.getPosixFilePermissions(parent))
                    .isEqualTo(PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void restrictsAnExistingDatabaseFileAndWalSiblings() throws Exception {
        Path database = tempDir.resolve("oiep.db");
        Files.createFile(database);
        Files.createFile(tempDir.resolve("oiep.db-wal"));
        Files.createFile(tempDir.resolve("oiep.db-shm"));

        DataSourceConfig.hardenDatabaseFile("jdbc:sqlite:" + database + "?journal_mode=WAL");

        if (isPosix()) {
            Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rw-------");
            assertThat(Files.getPosixFilePermissions(database)).isEqualTo(ownerOnly);
            assertThat(Files.getPosixFilePermissions(tempDir.resolve("oiep.db-wal"))).isEqualTo(ownerOnly);
            assertThat(Files.getPosixFilePermissions(tempDir.resolve("oiep.db-shm"))).isEqualTo(ownerOnly);
        }
    }

    @Test
    void hardeningIsBestEffortForMissingFilesAndInMemoryUrls() {
        DataSourceConfig.hardenDatabaseFile("jdbc:sqlite::memory:");
        DataSourceConfig.hardenDatabaseFile("jdbc:sqlite:" + tempDir.resolve("not-there.db"));
        DataSourceConfig.ensureSqliteParentDirectory("jdbc:sqlite::memory:");
        // Reaching here without an exception is the assertion.
        assertThat(true).isTrue();
    }

    private static boolean isPosix() {
        return java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }
}
