package io.debezium.server.slatedb;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.connect.runtime.standalone.StandaloneConfig;

import io.debezium.server.TestConfigSource;

/**
 *
 * @author vjuranek
 */
public class SlateDbTestConfigSource extends TestConfigSource {

    public SlateDbTestConfigSource() {
        Map<String, String> slateDbTest = new HashMap<>();

        slateDbTest.put("debezium.sink.type", "slatedb");

        slateDbTest.put("debezium.source.connector.class", "io.debezium.connector.postgresql.PostgresConnector");
        slateDbTest.put("debezium.source." + StandaloneConfig.OFFSET_STORAGE_FILE_FILENAME_CONFIG, OFFSET_STORE_PATH.toAbsolutePath().toString());
        slateDbTest.put("debezium.source.offset.flush.interval.ms", "0");
        slateDbTest.put("debezium.source.topic.prefix", "testc");
        slateDbTest.put("debezium.source.schema.include.list", "inventory");
        slateDbTest.put("debezium.source.table.include.list", "inventory.customers");

        config = slateDbTest;
    }

    @Override
    public int getOrdinal() {
        // Configuration property precedence is based on ordinal values and since we override the
        // properties in TestConfigSource, we should give this a higher priority.
        return super.getOrdinal() + 1;
    }
}
