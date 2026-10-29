/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.server.slatedb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.inject.Instance;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.debezium.runtime.BatchEvent;
import io.debezium.runtime.CapturingEvents;
import io.slatedb.uniffi.Db;
import io.slatedb.uniffi.DbBuilder;
import io.slatedb.uniffi.ObjectStore;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Round-trip tests for {@link SlateDbChangeConsumer} backed by a local file object store, exercising the real
 * (natively bundled) SlateDB Java client.
 */
class SlateDbChangeConsumerTest {

    private static final String OBJECT_STORE_URI = "file:///";

    private Path tempDir;
    private String dbPath;
    private SlateDbChangeConsumer consumer;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("slatedb-test");
        dbPath = tempDir.resolve("debezium").toAbsolutePath().toString();

        consumer = new SlateDbChangeConsumer();
        consumer.customDb = notResolvable();
        consumer.configure(config(OBJECT_STORE_URI, dbPath));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (consumer != null) {
            consumer.close();
        }
        if (tempDir != null) {
            try (var paths = Files.walk(tempDir)) {
                paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    void shouldWriteAndDeleteRecords() throws Exception {
        BatchEvent insert1 = event("key-1", "value-1");
        BatchEvent insert2 = event("key-2", "value-2");
        BatchEvent tombstone = event("key-1", null);
        BatchEvent nullKey = event(null, "orphan");

        CapturingEvents<BatchEvent> events = capturingEvents(List.of(insert1, insert2, tombstone, nullKey));

        consumer.handle(events);

        verify(insert1).commit();
        verify(insert2).commit();
        verify(tombstone).commit();
        verify(nullKey).commit();

        // Close the consumer so the database is durably flushed before reopening it for verification.
        consumer.close();
        consumer = null;

        assertThat(readValue("key-2")).isEqualTo("value-2");
        assertThat(readValue("key-1")).isNull(); // overwritten by the tombstone
        assertThat(readValue("key-missing")).isNull();
    }

    @Test
    void shouldHandleEmptyBatch() throws Exception {
        consumer.handle(capturingEvents(List.of()));
        // Nothing to assert beyond the absence of exceptions.
    }

    private String readValue(String key) throws Exception {
        try (ObjectStore store = ObjectStore.resolve(OBJECT_STORE_URI);
                DbBuilder builder = new DbBuilder(dbPath, store)) {
            Db db = builder.build().get(30, TimeUnit.SECONDS);
            try {
                byte[] value = db.get(key.getBytes(StandardCharsets.UTF_8)).get(30, TimeUnit.SECONDS);
                return value == null ? null : new String(value, StandardCharsets.UTF_8);
            }
            finally {
                db.shutdown().get(30, TimeUnit.SECONDS);
                db.close();
            }
        }
    }

    private static BatchEvent event(String key, String value) {
        BatchEvent event = mock(BatchEvent.class);
        when(event.key()).thenReturn(key);
        when(event.value()).thenReturn(value);
        when(event.destination()).thenReturn("test.destination");
        return event;
    }

    @SuppressWarnings("unchecked")
    private static CapturingEvents<BatchEvent> capturingEvents(List<BatchEvent> records) {
        CapturingEvents<BatchEvent> events = mock(CapturingEvents.class);
        when(events.records()).thenReturn(records);
        return events;
    }

    @SuppressWarnings("unchecked")
    private static Instance<Db> notResolvable() {
        Instance<Db> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(false);
        return instance;
    }

    private static Config config(String objectStoreUri, String dbPath) {
        return new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(java.util.Map.of(
                        "debezium.sink.slatedb.objectstore.uri", objectStoreUri,
                        "debezium.sink.slatedb.path", dbPath,
                        "debezium.sink.slatedb.flush.on.batch", "true"),
                        "test", 100))
                .build();
    }
}
