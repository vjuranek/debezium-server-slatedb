/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.server.slatedb;

import java.time.Duration;

import org.apache.kafka.common.config.ConfigDef;

import io.debezium.config.Configuration;
import io.debezium.config.Field;

/**
 * Configuration fields for {@link SlateDbChangeConsumer}.
 *
 * @author vjuranek
 */
public class SlateDbChangeConsumerConfig {

    public static final Field OBJECT_STORE_URI = Field.create("objectstore.uri")
            .withDisplayName("Object store URI")
            .withType(ConfigDef.Type.STRING)
            .withDefault("file:///")
            .withWidth(ConfigDef.Width.LONG)
            .withImportance(ConfigDef.Importance.HIGH)
            .withDescription("URI of the object store backing the SlateDB database, e.g. 'file:///', 'memory:///' "
                    + "or 's3://bucket'. Any URL supported by the object_store crate is accepted. The URI must only "
                    + "identify the storage backend and must not contain a path; use 'path' for the database location.");

    public static final Field DATABASE_PATH = Field.create("path")
            .withDisplayName("Database path")
            .withType(ConfigDef.Type.STRING)
            .withDefault("/tmp/slatedb")
            .withWidth(ConfigDef.Width.MEDIUM)
            .withImportance(ConfigDef.Importance.HIGH)
            .withDescription("Path (prefix) of the SlateDB database within the object store. For a local 'file:///' "
                    + "store this is an absolute filesystem path such as '/tmp/slatedb'; for 's3://bucket' it is the "
                    + "key prefix within the bucket.");

    public static final Field FLUSH_ON_BATCH = Field.create("flush.on.batch")
            .withDisplayName("Flush on batch")
            .withType(ConfigDef.Type.BOOLEAN)
            .withDefault(true)
            .withWidth(ConfigDef.Width.SHORT)
            .withImportance(ConfigDef.Importance.MEDIUM)
            .withDescription("Whether to flush the SlateDB database to the object store after each processed batch. "
                    + "When disabled, SlateDB flushes according to its own policy, which may delay durability.");

    public static final Field OPERATION_TIMEOUT_MS = Field.create("operation.timeout.ms")
            .withDisplayName("Operation timeout (ms)")
            .withType(ConfigDef.Type.LONG)
            .withDefault(Duration.ofSeconds(30).toMillis())
            .withWidth(ConfigDef.Width.SHORT)
            .withImportance(ConfigDef.Importance.MEDIUM)
            .withValidation(Field::isPositiveLong)
            .withDescription("Maximum time in milliseconds to wait for a single asynchronous SlateDB operation to complete.");

    private String objectStoreUri;
    private String databasePath;
    private boolean flushOnBatch;
    private Duration operationTimeout;

    public SlateDbChangeConsumerConfig(Configuration config) {
        init(config);
    }

    protected void init(Configuration config) {
        objectStoreUri = config.getString(OBJECT_STORE_URI);
        databasePath = config.getString(DATABASE_PATH);
        flushOnBatch = config.getBoolean(FLUSH_ON_BATCH);
        operationTimeout = Duration.ofMillis(config.getLong(OPERATION_TIMEOUT_MS));
    }

    public String getObjectStoreUri() {
        return objectStoreUri;
    }

    public String getDatabasePath() {
        return databasePath;
    }

    public boolean isFlushOnBatch() {
        return flushOnBatch;
    }

    public Duration getOperationTimeout() {
        return operationTimeout;
    }
}
