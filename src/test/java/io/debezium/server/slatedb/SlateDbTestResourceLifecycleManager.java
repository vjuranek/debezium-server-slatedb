package io.debezium.server.slatedb;

import static java.lang.System.mapLibraryName;

import java.io.File;
import java.util.Map;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.util.Testing;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import jnr.ffi.LibraryLoader;

/**
 *
 * @author vjuranek
 */
public class SlateDbTestResourceLifecycleManager implements QuarkusTestResourceLifecycleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlateDbTestResourceLifecycleManager.class);

    public static final String SLATE_DB_PATH = Testing.Files.createTestingDirectory("slatedb").getAbsolutePath();

    private static SlateDb db;

    public static SlateDb getDb() {
        return db;
    }

    private static void init() {
        final Config config = ConfigProvider.getConfig();
        String dylib = "slatedb";
        System.setProperty("jnr.ffi.library.path", getLibraryPath(dylib));

        try {
            db = LibraryLoader.create(SlateDb.class).load(dylib);
            db.init(SLATE_DB_PATH);
        }
        catch (Exception e) {
            LOGGER.warn("Failed to initialize SlateDB", e);
        }
    }

    @Override
    public Map<String, String> start() {
        init();
        return Map.of("debezium.sink.slatedb.destination", SLATE_DB_PATH);
    }

    @Override
    public void stop() {
        try {
            db.close();
        }
        catch (Exception e) {
            // ignored
        }
    }

    private static String getLibraryPath(String dylib) {
        File f = new File(SlateDbChangeConsumer.class.getClassLoader().getResource(mapLibraryName(dylib)).getFile());
        return f.getParent();
    }
}
