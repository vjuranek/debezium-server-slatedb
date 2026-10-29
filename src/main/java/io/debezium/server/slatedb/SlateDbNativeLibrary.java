/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.server.slatedb;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.DebeziumException;

/**
 * Makes the SlateDB native library ({@code slatedb_uniffi}) available to the UniFFI generated bindings.
 * <p>
 * The {@code slatedb-uniffi} artifact bundles the native libraries for the supported platforms as classpath
 * resources (e.g. {@code linux-x86-64/libslatedb_uniffi.so}), but its generated loader resolves the library via
 * {@link System#loadLibrary(String)} (or, when the {@code uniffi.component.slatedb.libraryOverride} system property
 * points to an absolute path, via {@link System#load(String)}). This helper extracts the platform specific library
 * bundled on the classpath to a temporary file and sets that override property, so the sink works without requiring
 * the user to pre-install the native library on {@code java.library.path}.
 *
 * @author vjuranek
 */
final class SlateDbNativeLibrary {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlateDbNativeLibrary.class);

    /** System property honoured by the UniFFI generated loader to override the library location. */
    static final String LIBRARY_OVERRIDE_PROPERTY = "uniffi.component.slatedb.libraryOverride";

    private static final String LIBRARY_NAME = "slatedb_uniffi";

    private static volatile boolean loaded = false;

    private SlateDbNativeLibrary() {
    }

    /**
     * Ensures the SlateDB native library is resolvable before any {@code io.slatedb.uniffi} class is initialized.
     * The extraction happens at most once per JVM and is skipped entirely when the user already provided the
     * override property (e.g. to use a system-installed library).
     */
    static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }

        if (System.getProperty(LIBRARY_OVERRIDE_PROPERTY) != null) {
            LOGGER.debug("'{}' is already set, skipping bundled SlateDB native library extraction", LIBRARY_OVERRIDE_PROPERTY);
            loaded = true;
            return;
        }

        final String resource = resourcePath();
        try (InputStream in = SlateDbNativeLibrary.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new DebeziumException("Bundled SlateDB native library '" + resource
                        + "' was not found on the classpath. This platform may not be supported by slatedb-uniffi.");
            }

            final Path tempDir = Files.createTempDirectory("slatedb-native");
            tempDir.toFile().deleteOnExit();
            final Path target = tempDir.resolve(libraryFileName());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit();

            System.setProperty(LIBRARY_OVERRIDE_PROPERTY, target.toAbsolutePath().toString());
            LOGGER.info("Extracted bundled SlateDB native library to '{}'", target);
            loaded = true;
        }
        catch (IOException e) {
            throw new DebeziumException("Failed to extract bundled SlateDB native library '" + resource + "'", e);
        }
    }

    private static String resourcePath() {
        return osDirectory() + "-" + archDirectory() + "/" + libraryFileName();
    }

    private static String libraryFileName() {
        final String os = osName();
        if (os.contains("win")) {
            return LIBRARY_NAME + ".dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "lib" + LIBRARY_NAME + ".dylib";
        }
        return "lib" + LIBRARY_NAME + ".so";
    }

    private static String osDirectory() {
        final String os = osName();
        if (os.contains("win")) {
            return "win32";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "darwin";
        }
        if (os.contains("linux")) {
            return "linux";
        }
        throw new DebeziumException("Unsupported operating system for SlateDB native library: " + System.getProperty("os.name"));
    }

    private static String archDirectory() {
        final String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            return "x86-64";
        }
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "aarch64";
        }
        throw new DebeziumException("Unsupported architecture for SlateDB native library: " + System.getProperty("os.arch"));
    }

    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }
}
