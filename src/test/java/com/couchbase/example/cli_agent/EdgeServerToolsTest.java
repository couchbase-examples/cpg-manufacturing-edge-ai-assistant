package com.couchbase.example.cli_agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises EdgeServerTools against a stub Edge Server that returns rows
 * the way "SELECT * FROM _" does: document fields nested under "_".
 */
class EdgeServerToolsTest {

    private HttpServer server;
    private EdgeServerTools tools;

    @BeforeEach
    void startStubEdgeServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Bare-array response shape
        stub("/cpg_manufacturing.manufacturing.alerts/_query/active_alerts",
            "[{\"_\":{\"machine_id\":\"M1\",\"error_code\":\"E42\",\"severity\":\"HIGH\"}}]");
        // {"results": [...]} response shape
        stub("/cpg_manufacturing.manufacturing.telemetry/_query/telemetry_by_machine",
            "{\"results\":[{\"_\":{\"timestamp\":\"2025-08-06T10:00:00Z\",\"sensor_type\":\"pressure\","
                + "\"value\":3.2,\"unit\":\"bar\",\"status\":\"normal\"}}]}");
        // Rows that are already flat must pass through unchanged
        stub("/cpg_manufacturing.manufacturing.solutions/_query/list_all_solutions_for_error_code",
            "[{\"error_code\":\"E42\",\"solution_comment\":\"Replace the seal\"}]");
        server.start();
        tools = new EdgeServerTools("127.0.0.1", server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void listReturningToolExposesUnwrappedDocuments() {
        List<Map<String, Object>> alerts = tools.getActiveAlertsForMachine("M1");

        assertEquals(1, alerts.size());
        assertEquals("E42", alerts.get(0).get("error_code"));
        assertFalse(alerts.get(0).containsKey("_"), "rows must not leak the '_' wrapper");
    }

    @Test
    void formattedToolRendersNestedFieldsFromResultsEnvelope() {
        String out = tools.getRecentTelemetry("M1", 5);

        assertTrue(out.contains("Sensor: pressure"), out);
        assertTrue(out.contains("Value: 3.2 bar"), out);
        assertFalse(out.contains("null"), out);
    }

    @Test
    void flatRowsPassThroughUnchanged() {
        String out = tools.listAllSolutionsForErrorCode("E42");

        assertTrue(out.contains("Replace the seal"), out);
    }

    private void stub(String path, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }
}
