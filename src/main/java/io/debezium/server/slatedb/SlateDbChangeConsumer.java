/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.server.slatedb;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.eclipse.microprofile.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.DebeziumException;
import io.debezium.Module;
import io.debezium.config.Configuration;
import io.debezium.config.Field;
import io.debezium.metadata.ComponentMetadata;
import io.debezium.metadata.ComponentMetadataFactory;
import io.debezium.runtime.BatchEvent;
import io.debezium.runtime.CapturingEvents;
import io.debezium.server.BaseChangeConsumer;
import io.debezium.server.CustomConsumerBuilder;
import io.debezium.server.api.DebeziumServerConsumer;
import io.debezium.server.api.DebeziumServerSink;
import io.slatedb.uniffi.Db;
import io.slatedb.uniffi.DbBuilder;
import io.slatedb.uniffi.ObjectStore;
import io.slatedb.uniffi.WriteBatch;

/**
 * An implementation of the Debezium Server sink that stores change events in an
 * <a href="https://github.com/slatedb/slatedb">SlateDB</a> database using the official SlateDB Java client.
 * <p>
 * Each change event is written as a key/value pair, where both the key and the value are serialized to bytes.
 * Tombstone events (events with a {@code null} value) are translated into SlateDB deletes.
 *
 * @author vjuranek
 */
@Named("slatedb")
@Dependent
public class SlateDbChangeConsumer extends BaseChangeConsumer implements DebeziumServerConsumer<CapturingEvents<BatchEvent>>, DebeziumServerSink {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlateDbChangeConsumer.class);

    private static final String PROP_PREFIX = "debezium.sink.slatedb.";

    private final ComponentMetadataFactory componentMetadataFactory = new ComponentMetadataFactory();

    private SlateDbChangeConsumerConfig config;
    private ObjectStore objectStore;
    private Db db;

    @Inject
    @CustomConsumerBuilder
    Instance<Db> customDb;

    @Override
    public void configure(Config mpConfig) throws Exception {
        Configuration configuration = Configuration.from(getConfigSubset(mpConfig, PROP_PREFIX));
        this.config = new SlateDbChangeConsumerConfig(configuration);

        SlateDbNativeLibrary.ensureLoaded();

        if (customDb.isResolvable()) {
            db = customDb.get();
            LOGGER.info("Using custom SlateDB instance provided via @CustomConsumerBuilder");
            return;
        }

        final String uri = config.getObjectStoreUri();
        final String path = config.getDatabasePath();
        LOGGER.info("Opening SlateDB database '{}' backed by object store '{}'", path, uri);

        try {
            objectStore = ObjectStore.resolve(uri);
            try (DbBuilder builder = new DbBuilder(path, objectStore)) {
                db = await(builder.build());
            }
        }
        catch (Exception e) {
            closeQuietly();
            throw new DebeziumException("Failed to open SlateDB database '" + path + "' at '" + uri + "'", e);
        }
        LOGGER.info("Opened SlateDB database '{}'", path);
    }

    @PreDestroy
    @Override
    public void close() {
        if (db != null) {
            try {
                await(db.shutdown());
            }
            catch (Exception e) {
                LOGGER.warn("Failed to cleanly shut down SlateDB", e);
            }
        }
        closeQuietly();
        LOGGER.info("SlateDB database closed");
    }

    @Override
    public void handle(CapturingEvents<BatchEvent> events) throws InterruptedException {
        final List<BatchEvent> records = events.records();
        if (records.isEmpty()) {
            return;
        }

        final WriteBatch batch = new WriteBatch();
        try {
            int written = 0;
            for (BatchEvent record : records) {
                if (record.key() == null) {
                    LOGGER.warn("Skipping event with null key for destination '{}'", record.destination());
                    continue;
                }
                final byte[] key = getBytes(record.key());
                if (record.value() == null) {
                    LOGGER.trace("Deleting key '{}'", getString(record.key()));
                    batch.delete(key);
                }
                else {
                    LOGGER.trace("Writing key '{}'", getString(record.key()));
                    batch.put(key, getBytes(record.value()));
                }
                written++;
            }

            if (written > 0) {
                await(db.write(batch));
                if (config.isFlushOnBatch()) {
                    await(db.flush());
                }
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
        catch (Exception e) {
            throw new DebeziumException("Failed to write batch to SlateDB", e);
        }
        finally {
            batch.close();
        }

        for (BatchEvent record : records) {
            record.commit();
        }
    }

    @Override
    public Optional<Boolean> tombstoneSupport() {
        return Optional.of(Boolean.TRUE);
    }

    @Override
    public Field.Set getConfigFields() {
        return Field.setOf(
                SlateDbChangeConsumerConfig.OBJECT_STORE_URI,
                SlateDbChangeConsumerConfig.DATABASE_PATH,
                SlateDbChangeConsumerConfig.FLUSH_ON_BATCH,
                SlateDbChangeConsumerConfig.OPERATION_TIMEOUT_MS);
    }

    @Override
    public List<ComponentMetadata> getConnectorMetadata() {
        return List.of(componentMetadataFactory.createComponentMetadata(this, Module.version()));
    }

    private <T> T await(CompletableFuture<T> future) throws Exception {
        final Duration timeout = config.getOperationTimeout();
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (ExecutionException e) {
            throw unwrap(e);
        }
        catch (TimeoutException e) {
            throw new DebeziumException("SlateDB operation did not complete within " + timeout.toMillis() + " ms", e);
        }
    }

    private static Exception unwrap(ExecutionException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof Exception) {
            return (Exception) cause;
        }
        return new DebeziumException(cause != null ? cause : e);
    }

    private void closeQuietly() {
        if (db != null) {
            try {
                db.close();
            }
            catch (Exception e) {
                LOGGER.warn("Failed to release SlateDB handle", e);
            }
            db = null;
        }
        if (objectStore != null) {
            try {
                objectStore.close();
            }
            catch (Exception e) {
                LOGGER.warn("Failed to release SlateDB object store handle", e);
            }
            objectStore = null;
        }
    }
}
