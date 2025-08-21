package io.debezium.server.slatedb;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.DebeziumException;
import io.debezium.server.DebeziumServer;
import io.debezium.server.events.ConnectorCompletedEvent;
import io.debezium.testing.testcontainers.PostgresTestResourceLifecycleManager;
import io.debezium.util.Testing;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 *
 * @author vjuranek
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResourceLifecycleManager.class)
@QuarkusTestResource(SlateDbTestResourceLifecycleManager.class)
public class SlateDbIT {

    @Inject
    DebeziumServer server;

    private static final Logger LOGGER = LoggerFactory.getLogger(SlateDbIT.class);
    private static final int MESSAGE_COUNT = 4;
    private static final int EXPECTED_RETRIES = 5;
    private boolean expectServerFail = false;
    private String expectedErrorMessage;

    {
        Testing.Files.delete(SlateDbTestConfigSource.OFFSET_STORE_PATH);
        Testing.Files.createTestingFile(SlateDbTestConfigSource.OFFSET_STORE_PATH);

        Testing.Files.delete(SlateDbTestResourceLifecycleManager.SLATE_DB_PATH);
        Testing.Files.createTestingDirectory(SlateDbTestResourceLifecycleManager.SLATE_DB_PATH);
    }

    void connectorCompleted(@Observes ConnectorCompletedEvent event) throws Exception {
        if (!event.isSuccess()) {
            Exception e = (Exception) event.getError().get();
            if (e instanceof DebeziumException && expectServerFail && e.getMessage().equals(expectedErrorMessage)) {
                LOGGER.info("Expected server failure: {}", e);
                return;
            }
            throw e;
        }
    }

    @Test
    public void testSlateDbSink() {
        Testing.Print.enable();
        expectServerFail = false;

        List events = new ArrayList<>();
        SlateDb db = SlateDbTestResourceLifecycleManager.getDb();

        // Awaitility.await().atMost(Duration.ofSeconds(60)).until(() -> {
        // db.gList<ServeEvent> currentEvents = getAllServeEvents();
        // events.addAll(currentEvents);
        // // Remove already added events, if e.g. 3 out of 4 events are added, in next attempt all 4 events
        // // are added again and test fails.
        // for (ServeEvent e : currentEvents) {
        // removeServeEvent(e.getId());
        // }
        //
        // return events.size() == MESSAGE_COUNT;
        // });
        //
        // assertEvents(events, MESSAGE_COUNT);
    }
}
