/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.server.slatedb;

import static java.lang.System.mapLibraryName;

import java.io.File;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.server.BaseChangeConsumer;

import jnr.ffi.LibraryLoader;

/**
 *
 *
 * @author vjuranek
 */
@Named("slatedb")
@Dependent
public class SlateDbChangeConsumer extends BaseChangeConsumer implements DebeziumEngine.ChangeConsumer<ChangeEvent<Object, Object>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlateDbChangeConsumer.class);

    public static final String PROP_PREFIX = "debezium.sink.slatedb.";

    @ConfigProperty(name = PROP_PREFIX + "destination", defaultValue = "/tmp/slatedb")
    String destination;

    private SlateDb db;

    @PostConstruct
    void connect() {
        final Config config = ConfigProvider.getConfig();
        String dylib = "slatedb";
        System.setProperty("jnr.ffi.library.path", getLibraryPath(dylib));

        try {
            db = LibraryLoader.create(SlateDb.class).load(dylib);
            db.init(destination);
            LOGGER.warn("SlateDB initialized");
        }
        catch (Exception e) {
            LOGGER.warn("Failed to initialize SlateDB", e);
        }
    }

    @PreDestroy
    void close() {
        try {
            db.close();
        }
        catch (Exception e) {
            LOGGER.warn("Failed to close SlateDB", e);
        }
    }

    @Override
    public void handleBatch(List<ChangeEvent<Object, Object>> records, DebeziumEngine.RecordCommitter<ChangeEvent<Object, Object>> committer)
            throws InterruptedException {
        for (ChangeEvent<Object, Object> record : records) {
            LOGGER.trace("Received event '{}'", record);
            if (record.value() != null) {
                // TODO serialize to bytes and store bytes
                // System.out.println("!!!inserting key " + record.key().toString() + " -> " + record.value().toString());
                db.insert(record.key().toString(), record.value().toString());
                committer.markProcessed(record);
            }
        }

        committer.markBatchFinished();
    }

    private static String getLibraryPath(String dylib) {
        File f = new File(SlateDbChangeConsumer.class.getClassLoader().getResource(mapLibraryName(dylib)).getFile());
        return f.getParent();
    }
}
