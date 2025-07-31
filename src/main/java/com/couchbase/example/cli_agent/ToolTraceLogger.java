package com.couchbase.example.cli_agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central logger for tool call tracing. All tool classes (EdgeServerTools, ManualSearchTools, etc.)
 * should use this logger so that log entries are grouped under the same name and
 * can be configured in one place via simplelogger.properties.
 */
public final class ToolTraceLogger {

    /**
     * Shared SLF4J logger instance for tool tracing.
     */
    public static final Logger LOGGER = LoggerFactory.getLogger("ToolTraceLogger");

    private static final java.time.format.DateTimeFormatter TIMESTAMP_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // ===== Common tracing helpers =====
    public static void logToolStart(String toolName, Object... params) {
        String timestamp = java.time.LocalDateTime.now().format(TIMESTAMP_FORMAT);
        StringBuilder logMessage = new StringBuilder();
        logMessage.append(String.format("[%s] TOOL_CALL_START: %s", timestamp, toolName));

        if (params != null && params.length > 0) {
            logMessage.append(" | Parameters: ");
            for (int i = 0; i < params.length; i += 2) {
                if (i + 1 < params.length) {
                    logMessage.append(String.format("%s=%s", params[i], params[i + 1]));
                    if (i + 2 < params.length) {
                        logMessage.append(", ");
                    }
                }
            }
        }
        LOGGER.info(logMessage.toString());
    }

    public static void logToolEnd(String toolName, Object result, long durationMs) {
        String timestamp = java.time.LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String resultSummary = getResultSummary(result);
        LOGGER.info(String.format("[%s] TOOL_CALL_END: %s | Duration: %dms | Result: %s", timestamp, toolName, durationMs, resultSummary));
    }

    public static void logToolError(String toolName, Exception error, long durationMs) {
        String timestamp = java.time.LocalDateTime.now().format(TIMESTAMP_FORMAT);
        LOGGER.error(String.format("[%s] TOOL_CALL_ERROR: %s | Duration: %dms | Error: %s", timestamp, toolName, durationMs, error.getMessage()), error);
    }

    private static String getResultSummary(Object result) {
        if (result == null) {
            return "null";
        }
        if (result instanceof String str) {
            return str.length() > 100 ? str.substring(0, 100) + "..." : str;
        }
        if (result instanceof java.util.List<?> list) {
            return String.format("List[%d items]", list.size());
        }
        if (result instanceof java.util.Map<?, ?> map) {
            return String.format("Map[%d keys]", map.size());
        }
        return result.getClass().getSimpleName();
    }

    // Prevent instantiation
    private ToolTraceLogger() {
    }
}
