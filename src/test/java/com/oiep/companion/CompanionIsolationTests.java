package com.oiep.companion;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the "component based, removable" promise: the core application must not depend on
 * the optional AI companion package.
 *
 * <p>Core code may use only the generic extension points ({@code CspContributor},
 * {@code PageExtension}) and the two generic include slots. A single stray import from
 * {@code com.oiep.companion} in the core would make the component effectively unremovable,
 * so this test fails the build if one appears.</p>
 */
class CompanionIsolationTests {

    private static final Path JAVA_ROOT = Path.of("src", "main", "java", "com", "oiep");
    private static final Path JSP_ROOT = Path.of("src", "main", "webapp", "WEB-INF", "jsp");

    @Test
    void coreJavaNeverReferencesTheCompanionPackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(JAVA_ROOT)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("companion"))
                    .forEach(path -> {
                        try {
                            String source = normalize(Files.readString(path, StandardCharsets.UTF_8));
                            if (source.contains("com.oiep.companion")) {
                                offenders.add(path.toString());
                            }
                        } catch (IOException ex) {
                            throw new IllegalStateException("Could not read " + path, ex);
                        }
                    });
        }
        assertThat(offenders)
                .as("core Java must not import the optional companion package")
                .isEmpty();
    }

    @Test
    void coreJspsOnlyUseTheGenericPageExtensionSlot() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(JSP_ROOT)) {
            files.filter(path -> path.toString().endsWith(".jsp") || path.toString().endsWith(".jspf"))
                    .filter(path -> !path.toString().contains("companion"))
                    .forEach(path -> {
                        try {
                            String source = normalize(Files.readString(path, StandardCharsets.UTF_8));
                            if (source.contains("companion")) {
                                offenders.add(path.toString());
                            }
                        } catch (IOException ex) {
                            throw new IllegalStateException("Could not read " + path, ex);
                        }
                    });
        }
        assertThat(offenders)
                .as("core JSPs must use only the generic pageExtensions loop, not component paths")
                .isEmpty();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
