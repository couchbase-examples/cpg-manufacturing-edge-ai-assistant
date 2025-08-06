package com.couchbase.example.cli_agent;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import static com.couchbase.example.cli_agent.ToolTraceLogger.*;

/**
 * LangChain4j Tools for accessing CPG operational data from Couchbase Edge Server API
 * 
 * These tools allow the AI agent to retrieve real-time CPG operational data including:
 * - Recent telemetry data from beverage filling equipment
 * - Active alerts and quality alarms
 * - Device status information
 * - Quality metrics and production data
 * - Historical data queries for troubleshooting
 */
public class EdgeServerTools {
    // Constants
    private static final String ERROR_PREFIX = "❌ Error retrieving CPG ";
    private static final String SEPARATOR_LINE = "=".repeat(55);
    private static final String SHORT_SEPARATOR_LINE = "=".repeat(35);
    private static final String MEDIUM_SEPARATOR_LINE = "=".repeat(45);
    private static final String LARGE_SEPARATOR_LINE = "=".repeat(50);
    private static final String SMALL_SEPARATOR_LINE = "=".repeat(40);
    private static final String DEFAULT_STATUS_NORMAL = "normal";
    
    // Use shared logger for all tool tracing
    private static final Logger logger = com.couchbase.example.cli_agent.ToolTraceLogger.LOGGER;
    
    private final String bucketName = "cpg_manufacturing";
    private final String scopeName = "manufacturing";
    private final String telemetryCollection = "telemetry";
    private final String alertsCollection = "alerts";
    private final String deviceStatusCollection = "device_status";
    private final String qualityMetricsCollection = "quality_metrics";
    private final String machinesCollection = "machines";
    private final String productionLinesCollection = "production_lines";
    private final String plantsCollection = "plants";
    private final String solutionsCollection = "solutions";
    private final String edgeServerBaseUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public EdgeServerTools(String edgeServerHost, int edgeServerPort) {
        this.edgeServerBaseUrl = String.format("http://%s:%d", edgeServerHost, edgeServerPort);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }
    
    public EdgeServerTools() {
        this("localhost", 59840); // Default configuration
    }
    
    @Tool("Get recent telemetry data from CPG production equipment")
    public String getRecentTelemetry(String machineId, 
            @P(value = "Maximum number of results to return", required = false) Integer limitResults) {
        long startTime = System.currentTimeMillis();
        logToolStart("getRecentTelemetry", "machineId", machineId, "limitResults", limitResults);
        
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("MACHINE_ID", machineId);
            if (limitResults != null) {
                params.put("maxResults", limitResults);
            }
            
            List<Map<String, Object>> results = executeNamedQuery("telemetry_by_machine", params);
            
            String response;
            if (results.isEmpty()) {
                response = String.format("No recent telemetry data found for CPG machine: %s", machineId);
            } else {
                StringBuilder responseBuilder = new StringBuilder();
                responseBuilder.append(String.format("📊 Recent CPG Telemetry for Machine %s (%d entries):\n", machineId, results.size()));
                responseBuilder.append(SEPARATOR_LINE).append("\n");
                
                for (Map<String, Object> telemetry : results) {
                    responseBuilder.append(String.format("🕒 %s | ", telemetry.get("timestamp")));
                    responseBuilder.append(String.format("Sensor: %s | ", telemetry.get("sensor_type")));
                    responseBuilder.append(String.format("Value: %s %s | ", telemetry.get("value"), telemetry.get("unit")));
                    responseBuilder.append(String.format("Status: %s", telemetry.get("status")));
                    
                    // Add location context for CPG equipment
                    if (telemetry.get("location") != null) {
                        responseBuilder.append(String.format(" | Location: %s", telemetry.get("location")));
                    }
                    
                    // Add product context for CPG
                    if (telemetry.get("product_code") != null) {
                        responseBuilder.append(String.format(" | Product: %s", telemetry.get("product_code")));
                    }
                    if (telemetry.get("batch_number") != null) {
                        responseBuilder.append(String.format(" | Batch: %s", telemetry.get("batch_number")));
                    }
                    
                    // Add production line context
                    if (telemetry.get("production_line") != null) {
                        responseBuilder.append(String.format(" | Line: %s", telemetry.get("production_line")));
                    }
                    responseBuilder.append("\n");
                }
                response = responseBuilder.toString();
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("getRecentTelemetry", response, duration);
            return response;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("getRecentTelemetry", e, duration);
            return String.format(ERROR_PREFIX + "telemetry data: %s", e.getMessage());
        }
    }
    
    @Tool("Get all active alerts and quality alarms from CPG production systems")
    public List<Map<String, Object>> getActiveAlerts() {
        long startTime = System.currentTimeMillis();
        logToolStart("getActiveAlerts");
        
        try {
            List<Map<String, Object>> results = executeNamedQuery("active_alerts", new HashMap<>());
            
            if (results.isEmpty()) {
                results = new ArrayList<>();
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("getActiveAlerts", results, duration);
            return results;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("getActiveAlerts", e, duration);
            return new ArrayList<>();
        }
    }

    @Tool("Get all active alerts and quality alarms for a specific CPG machine")
    public List<Map<String, Object>> getActiveAlertsForMachine(String machineId) {
        long startTime = System.currentTimeMillis();
        logToolStart("getActiveAlertsForMachine", "machineId", machineId);
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("MACHINE_ID", machineId);
            List<Map<String, Object>> results = executeNamedQuery("active_alerts", params);
            if (results.isEmpty()) {
                results = new ArrayList<>();
            }
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("getActiveAlertsForMachine", results, duration);
            return results;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("getActiveAlertsForMachine", e, duration);
            return new ArrayList<>();
        }
    }
    
    @Tool("Get alerts by specific error code for CPG troubleshooting")
    public String getAlertsByErrorCode(String errorCode, 
            @P(value = "Maximum number of results to return", required = false) Integer limitResults) {
        long startTime = System.currentTimeMillis();
        logToolStart("getAlertsByErrorCode", "errorCode", errorCode, "limitResults", limitResults);
        
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("ERROR_CODE", errorCode);
            if (limitResults != null) {
                params.put("maxResults", limitResults);
            }
            
            List<Map<String, Object>> results = executeNamedQuery("alerts_by_error_code", params);
            
            if (results.isEmpty()) {
                return String.format("No CPG alerts found for error code: %s", errorCode);
            }
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("🔍 CPG Alerts for Error Code %s (%d entries):\n", errorCode, results.size()));
            response.append(SEPARATOR_LINE).append("\n");
            
            for (Map<String, Object> alert : results) {
                response.append(String.format("🕒 %s | Machine: %s (%s)\n", 
                    alert.get("timestamp"), alert.get("machine_id"), alert.get("machine_type")));
                response.append(String.format("⚠️ Severity: %s | Status: %s\n", 
                    alert.get("severity"), alert.get("status")));
                
                // Add CPG context
                if (alert.get("product_code") != null) {
                    response.append(String.format("🍾 Product: %s | Batch: %s\n", 
                        alert.get("product_code"), alert.get("batch_number")));
                }
                
                // Add production line and product type
                if (alert.get("production_line") != null) {
                    response.append(String.format("🏭 Production Line: %s | Product Type: %s\n", 
                        alert.get("production_line"), alert.get("product_type")));
                }
                
                // Add current/threshold values if available
                if (alert.get("current_value") != null && alert.get("threshold") != null) {
                    response.append(String.format("📊 Current: %s | Threshold: %s\n", 
                        alert.get("current_value"), alert.get("threshold")));
                }
                
                // Add solution comment if available
                if (alert.get("solution_comment") != null) {
                    response.append(String.format("💡 Solution: %s\n", alert.get("solution_comment")));
                }
                
                // Add escalation status
                if (alert.get("escalation_required") != null) {
                    response.append(String.format("🚨 Escalation Required: %s\n", alert.get("escalation_required")));
                }
                
                // Add acknowledgment and resolution info
                if (alert.get("acknowledged_by") != null) {
                    response.append(String.format("✅ Acknowledged by: %s\n", alert.get("acknowledged_by")));
                }
                if (alert.get("resolved_by") != null) {
                    response.append(String.format("🔧 Resolved by: %s\n", alert.get("resolved_by")));
                }
                if (alert.get("resolution_time") != null) {
                    response.append(String.format("⏰ Resolution Time: %s\n", alert.get("resolution_time")));
                }
                
                response.append("\n");
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("getAlertsByErrorCode", response.toString(), duration);
            return response.toString();
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("getAlertsByErrorCode", e, duration);
            return String.format("❌ Error retrieving CPG alerts for error code %s: %s", errorCode, e.getMessage());
        }
    }
    
    @Tool("Get current raspberry pi device status and connectivity information for CPG equipment")
    public String getDeviceStatus(String deviceId) {
        long startTime = System.currentTimeMillis();
        logToolStart("getDeviceStatus", "deviceId", deviceId);
        
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("DEVICE_ID", deviceId);
            
            List<Map<String, Object>> results = executeNamedQuery("device_status", params);
            
            if (results.isEmpty()) {
                return String.format("No status information found for CPG device: %s", deviceId);
            }
            
            Map<String, Object> status = results.get(0);
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("📱 CPG Device Status for %s:\n", deviceId));
            response.append(SHORT_SEPARATOR_LINE).append("\n");
            response.append(String.format("🔌 Status: %s\n", status.get("status")));
            response.append(String.format("📊 Memory Usage: %s MB\n", status.get("memory_usage_mb")));
            response.append(String.format("⚡ CPU Usage: %s%%\n", status.get("cpu_usage_percent")));
            response.append(String.format("💾 Disk Usage: %s%%\n", status.get("disk_usage_percent")));
            response.append(String.format("🌐 Network: %s\n", status.get("network_status")));
            response.append(String.format("🔄 Sync Status: %s\n", status.get("sync_status")));
            
            if (status.get("uptime_seconds") != null) {
                long uptimeSeconds = ((Number) status.get("uptime_seconds")).longValue();
                long hours = uptimeSeconds / 3600;
                long minutes = (uptimeSeconds % 3600) / 60;
                response.append(String.format("⏰ Uptime: %d hours, %d minutes\n", hours, minutes));
            }
            
            if (status.get("documents_pending_sync") != null) {
                response.append(String.format("📄 Pending Sync: %s docs\n", status.get("documents_pending_sync")));
            }
            
            response.append(String.format("🕒 Last Update: %s\n", status.get("timestamp")));
            
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("getDeviceStatus", response.toString(), duration);
            return response.toString();
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("getDeviceStatus", e, duration);
            return String.format("❌ Error retrieving CPG device status: %s", e.getMessage());
        }
    }
    
    @Tool("Get production metrics for a specific CPG production line")
    public String getProductionMetrics(String productionLine, 
            @P(value = "Maximum number of results to return", required = false) Integer limitResults) {
        long startTime = System.currentTimeMillis();
        logToolStart("getProductionMetrics", "productionLine", productionLine, "limitResults", limitResults);
        
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("PRODUCTION_LINE", productionLine);
            if (limitResults != null) {
                params.put("maxResults", limitResults);
            }
            
            List<Map<String, Object>> results = executeNamedQuery("production_metrics_by_line", params);
            
            if (results.isEmpty()) {
                return String.format("No production metrics found for CPG line: %s", productionLine);
            }
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("📈 CPG Production Metrics for Line %s:\n", productionLine));
            response.append(MEDIUM_SEPARATOR_LINE).append("\n");
            
            for (Map<String, Object> metric : results) {
                response.append(String.format("🕒 %s\n", metric.get("timestamp")));
                response.append(String.format("🍾 Bottles Produced: %s/min\n", metric.get("bottles_produced_per_minute")));
                response.append(String.format("⚡ OEE: %s%%\n", metric.get("oee_percentage")));
                response.append(String.format("❌ Defects: %s/min (%.2f%%)\n", 
                    metric.get("defect_count_per_minute"), metric.get("defect_rate_percentage")));
                response.append(String.format("⏱️ Downtime: %s seconds\n", metric.get("downtime_seconds")));
                
                // Add detailed OEE breakdown
                if (metric.get("availability_percentage") != null) {
                    response.append(String.format("🔧 Availability: %s%%\n", metric.get("availability_percentage")));
                }
                if (metric.get("performance_percentage") != null) {
                    response.append(String.format("🚀 Performance: %s%%\n", metric.get("performance_percentage")));
                }
                if (metric.get("quality_percentage") != null) {
                    response.append(String.format("⭐ Quality: %s%%\n", metric.get("quality_percentage")));
                }
                
                // Add CPG-specific info
                if (metric.get("product_code") != null) {
                    response.append(String.format("🏷️ Product: %s | Batch: %s\n", 
                        metric.get("product_code"), metric.get("batch_number")));
                }
                if (metric.get("shift") != null) {
                    response.append(String.format("👥 Shift: %s\n", metric.get("shift")));
                }
                if (metric.get("manual_compliance") != null) {
                    response.append(String.format("📋 Manual Compliance: %s\n", metric.get("manual_compliance")));
                }
                response.append("\n");
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("getProductionMetrics", response.toString(), duration);
            return response.toString();
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("getProductionMetrics", e, duration);
            return String.format("❌ Error retrieving CPG production metrics: %s", e.getMessage());
        }
    }
    
    @Tool("Get quality metrics for CPG production monitoring")
    public String getQualityMetrics(String machineId, 
            @P(value = "Maximum number of results to return", required = false) Integer limitResults) {
        long startTime = System.currentTimeMillis();
        logToolStart("getQualityMetrics", "machineId", machineId, "limitResults", limitResults);
        
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("MACHINE_ID", machineId);
            if (limitResults != null) {
                params.put("maxResults", limitResults);
            }
            
            List<Map<String, Object>> results = executeNamedQuery("quality_metrics_by_machine", params);
            
            if (results.isEmpty()) {
                return String.format("No quality metrics found for CPG machine: %s", machineId);
            }
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("🔍 CPG Quality Metrics for Machine %s:\n", machineId));
            response.append(LARGE_SEPARATOR_LINE).append("\n");
            
            for (Map<String, Object> quality : results) {
                response.append(String.format("🕒 %s\n", quality.get("timestamp")));
                response.append(String.format("📊 Fill Accuracy: %s%%\n", quality.get("fill_accuracy_percentage")));
                response.append(String.format("🏷️ Label Alignment: %s%%\n", quality.get("label_alignment_percentage")));
                response.append(String.format("🔧 Cap Torque: %s inch-lbs\n", quality.get("cap_torque_inch_pounds")));
                response.append(String.format("⭐ Quality Grade: %s\n", quality.get("quality_grade")));
                
                if (quality.get("product_code") != null) {
                    response.append(String.format("🍾 Product: %s | Batch: %s\n", 
                        quality.get("product_code"), quality.get("batch_number")));
                }
                
                // Add production line and product type context
                if (quality.get("production_line") != null) {
                    response.append(String.format("🏭 Production Line: %s\n", quality.get("production_line")));
                }
                if (quality.get("product_type") != null) {
                    response.append(String.format("🧪 Product Type: %s\n", quality.get("product_type")));
                }
                response.append("\n");
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("getQualityMetrics", response.toString(), duration);
            return response.toString();
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("getQualityMetrics", e, duration);
            return String.format("❌ Error retrieving CPG quality metrics: %s", e.getMessage());
        }
    }
    
    @Tool("Get telemetry data for specific sensor type across CPG machines")
    public String getTelemetryBySensorType(String sensorType, 
            @P(value = "Maximum number of results to return", required = false) Integer limitResults) {
        long startTime = System.currentTimeMillis();
        logToolStart("getTelemetryBySensorType", "sensorType", sensorType, "limitResults", limitResults);
        
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("SENSOR_TYPE", sensorType);
            if (limitResults != null) {
                params.put("maxResults", limitResults);
            }
            
            List<Map<String, Object>> results = executeNamedQuery("telemetry_by_sensor_type", params);
            
            if (results.isEmpty()) {
                return String.format("No CPG telemetry data found for sensor type: %s", sensorType);
            }
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("🔧 CPG %s Sensor Data (%d readings):\n", sensorType, results.size()));
            response.append(SEPARATOR_LINE).append("\n");
            
            Map<String, List<Map<String, Object>>> groupedByMachine = groupByMachine(results);
            
            for (Map.Entry<String, List<Map<String, Object>>> entry : groupedByMachine.entrySet()) {
                String machineId = entry.getKey();
                List<Map<String, Object>> readings = entry.getValue();
                
                response.append(String.format("🏭 Machine %s (%d readings):\n", machineId, readings.size()));
                
                for (Map<String, Object> reading : readings.subList(0, Math.min(3, readings.size()))) {
                    response.append(String.format("   %s: %s %s", 
                        reading.get("timestamp"), reading.get("value"), reading.get("unit")));
                    
                    if (reading.get("status") != null && !DEFAULT_STATUS_NORMAL.equals(reading.get("status"))) {
                        response.append(String.format(" ⚠️ %s", reading.get("status")));
                    }
                    
                    if (reading.get("product_code") != null) {
                        response.append(String.format(" [%s]", reading.get("product_code")));
                    }
                    response.append("\n");
                }
                response.append("\n");
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("getTelemetryBySensorType", response.toString(), duration);
            return response.toString();
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("getTelemetryBySensorType", e, duration);
            return String.format("❌ Error retrieving CPG sensor data: %s", e.getMessage());
        }
    }
    
    @Tool("Get maintenance alerts and schedules for CPG equipment")
    public String getMaintenanceAlerts(
            @P(value = "Maximum number of results to return", required = false) Integer limitResults) {
        long startTime = System.currentTimeMillis();
        logToolStart("getMaintenanceAlerts", "limitResults", limitResults);
        
        try {
            Map<String, Object> params = new HashMap<>();
            if (limitResults != null) {
                params.put("maxResults", limitResults);
            }
            
            List<Map<String, Object>> results = executeNamedQuery("maintenance_alerts", params);
            
            if (results.isEmpty()) {
                return "✅ No maintenance alerts found. All CPG equipment maintenance up to date.";
            }
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("🔧 CPG Maintenance Alerts (%d total):\n", results.size()));
            response.append(SMALL_SEPARATOR_LINE).append("\n");
            
            for (Map<String, Object> alert : results) {
                response.append(String.format("🔧 %s\n", alert.get("message")));
                response.append(String.format("   Machine: %s | Type: %s\n", 
                    alert.get("machine_id"), alert.get("maintenance_type")));
                response.append(String.format("   Status: %s | Estimated Duration: %s min\n", 
                    alert.get("status"), alert.get("estimated_duration_minutes")));
                response.append(String.format("   Scheduled: %s\n\n", alert.get("timestamp")));
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("getMaintenanceAlerts", response.toString(), duration);
            return response.toString();
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("getMaintenanceAlerts", e, duration);
            return String.format("❌ Error retrieving CPG maintenance alerts: %s", e.getMessage());
        }
    }
    
    @Tool("Get comprehensive production summary for CPG troubleshooting")
    public String getProductionSummary(String productionLine) {
        long startTime = System.currentTimeMillis();
        logToolStart("getProductionSummary", "productionLine", productionLine);
        
        try {
            StringBuilder response = new StringBuilder();
            response.append(String.format("📊 CPG Production Summary for %s:\n", productionLine));
            response.append(LARGE_SEPARATOR_LINE).append("\n");
            
            // Get recent production metrics
            Map<String, Object> metricsParams = new HashMap<>();
            metricsParams.put("PRODUCTION_LINE", productionLine);
            List<Map<String, Object>> metrics = executeNamedQuery("production_summary_metrics", metricsParams);
            
            if (!metrics.isEmpty()) {
                response.append("📈 RECENT PRODUCTION METRICS:\n");
                Map<String, Object> latest = metrics.get(0);
                response.append(String.format("   Bottles/min: %s | OEE: %s%% | Defects: %s%%\n", 
                    latest.get("bottles_produced_per_minute"), 
                    latest.get("oee_percentage"),
                    latest.get("defect_rate_percentage")));
                response.append(String.format("   Product: %s | Batch: %s\n\n", 
                    latest.get("product_code"), latest.get("batch_number")));
            }
            
            // Get active alerts for this line
            Map<String, Object> alertsParams = new HashMap<>();
            alertsParams.put("PRODUCTION_LINE", productionLine);
            List<Map<String, Object>> alerts = executeNamedQuery("production_summary_alerts", alertsParams);
            
            if (!alerts.isEmpty()) {
                response.append("🚨 ACTIVE ALERTS:\n");
                for (Map<String, Object> alert : alerts) {
                    response.append(String.format("   %s %s [%s]\n", 
                        getSeverityEmoji((String) alert.get("severity")),
                        alert.get("message"), 
                        alert.get("error_code")));
                }
                response.append("\n");
            } else {
                response.append("✅ No active alerts for this production line.\n\n");
            }
            
            // Get quality status
            Map<String, Object> qualityParams = new HashMap<>();
            qualityParams.put("PRODUCTION_LINE", productionLine);
            List<Map<String, Object>> quality = executeNamedQuery("production_summary_quality", qualityParams);
            
            if (!quality.isEmpty()) {
                Map<String, Object> latestQuality = quality.get(0);
                response.append("🔍 QUALITY STATUS:\n");
                response.append(String.format("   Fill Accuracy: %s%% | Label Alignment: %s%%\n", 
                    latestQuality.get("fill_accuracy_percentage"),
                    latestQuality.get("label_alignment_percentage")));
                response.append(String.format("   Quality Grade: %s\n\n", latestQuality.get("quality_grade")));
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("getProductionSummary", response.toString(), duration);
            return response.toString();
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("getProductionSummary", e, duration);
            return String.format("❌ Error retrieving CPG production summary: %s", e.getMessage());
        }
    }
    
    @Tool("List all machines in the CPG manufacturing facility")
    public String listAllMachines() {
        long startTime = System.currentTimeMillis();
        logToolStart("listAllMachines");
        
        try {
            List<Map<String, Object>> results = executeNamedQuery("list_all_machines", new HashMap<>());
            
            if (results.isEmpty()) {
                return "No machines found in the CPG manufacturing facility.";
            }
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("🏭 CPG Manufacturing Machines (%d total):\n", results.size()));
            response.append(SEPARATOR_LINE).append("\n");
            
            for (Map<String, Object> rawMachine : results) {
                Map<String, Object> machine = extractDocumentData(rawMachine);
                response.append(String.format("🔧 %s | %s (%s)\n", 
                    machine.get("machine_id"), machine.get("name"), machine.get("machine_type")));
                response.append(String.format("   Plant: %s | Line: %s | Status: %s\n", 
                    machine.get("plant_id"), machine.get("production_line_id"), machine.get("status")));
                response.append(String.format("   Model: %s | Serial: %s\n\n", 
                    machine.get("model"), machine.get("serial_number")));
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("listAllMachines", response.toString(), duration);
            return response.toString();
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("listAllMachines", e, duration);
            return String.format("❌ Error retrieving CPG machines: %s", e.getMessage());
        }
    }
    
    @Tool("Get machine details by machine ID")
    public String getMachineById(String machineId) {
        long startTime = System.currentTimeMillis();
        logToolStart("getMachineById", "machineId", machineId);
        
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("MACHINE_ID", machineId);
            
            List<Map<String, Object>> results = executeNamedQuery("machine_by_id", params);
            
            if (results.isEmpty()) {
                return String.format("No machine found with ID: %s", machineId);
            }
            
            Map<String, Object> rawMachine = results.get(0);
            Map<String, Object> machine = extractDocumentData(rawMachine);
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("🔧 CPG Machine Details: %s\n", machineId));
            response.append(SMALL_SEPARATOR_LINE).append("\n");
            response.append(String.format("📋 Name: %s\n", machine.get("name")));
            response.append(String.format("🏭 Plant ID: %s\n", machine.get("plant_id")));
            response.append(String.format("📈 Production Line: %s\n", machine.get("production_line_id")));
            response.append(String.format("⚙️ Type: %s\n", machine.get("machine_type")));
            response.append(String.format("🍾 Product Type: %s\n", machine.get("product_type")));
            response.append(String.format("🔌 Device ID: %s\n", machine.get("device_id")));
            response.append(String.format("📦 Model: %s\n", machine.get("model")));
            response.append(String.format("🔢 Serial Number: %s\n", machine.get("serial_number")));
            response.append(String.format("📊 Status: %s\n", machine.get("status")));
            response.append(String.format("📅 Created: %s\n", machine.get("created_date")));
            
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("getMachineById", response.toString(), duration);
            return response.toString();
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("getMachineById", e, duration);
            return String.format("❌ Error retrieving machine %s: %s", machineId, e.getMessage());
        }
    }
    
    @Tool("List all production lines in the CPG manufacturing facility")
    public String listAllProductionLines() {
        long startTime = System.currentTimeMillis();
        logToolStart("listAllProductionLines");
        
        try {
            List<Map<String, Object>> results = executeNamedQuery("list_all_production_lines", new HashMap<>());
            
            if (results.isEmpty()) {
                return "No production lines found in the CPG manufacturing facility.";
            }
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("📈 CPG Production Lines (%d total):\n", results.size()));
            response.append(SEPARATOR_LINE).append("\n");
            
            for (Map<String, Object> rawLine : results) {
                Map<String, Object> line = extractDocumentData(rawLine);
                response.append(String.format("📈 %s | %s\n", 
                    line.get("production_line_id"), line.get("line_type")));
                response.append(String.format("   Plant: %s | Status: %s\n", 
                    line.get("plant_id"), line.get("status")));
                response.append(String.format("   Current Product: %s\n", line.get("primary_product")));
                response.append(String.format("   Name: %s\n", line.get("name")));
                response.append(String.format("   Capacity: %s units/hour | Efficiency: %s%%\n\n", 
                    line.get("capacity_units_per_hour"), line.get("efficiency_percentage")));
            }
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("listAllProductionLines", response.toString(), duration);
            return response.toString();
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("listAllProductionLines", e, duration);
            return String.format("❌ Error retrieving CPG production lines: %s", e.getMessage());
        }
    }
    
    @Tool("Get specific production line details by production line ID")
    public String getProductionLineById(String productionLineId) {
        long startTime = System.currentTimeMillis();
        logToolStart("getProductionLineById", "productionLineId", productionLineId);
        
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("PRODUCTION_LINE_ID", productionLineId);
            
            List<Map<String, Object>> results = executeNamedQuery("production_line_by_id", params);
            
            if (results.isEmpty()) {
                return String.format("No production line found with ID: %s", productionLineId);
            }
            
            Map<String, Object> rawLine = results.get(0);
            Map<String, Object> line = extractDocumentData(rawLine);
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("📈 Production Line Details: %s\n", productionLineId));
            response.append(MEDIUM_SEPARATOR_LINE).append("\n");
            response.append(String.format("🏭 Plant ID: %s\n", line.get("plant_id")));
            response.append(String.format("🔧 Line Type: %s\n", line.get("line_type")));
            response.append(String.format("📊 Status: %s\n", line.get("status")));
            response.append(String.format("🔧 Name: %s\n", line.get("name")));
            response.append(String.format("🍾 Primary Product: %s\n", line.get("primary_product")));
            response.append(String.format("⚡ Capacity: %s units/hour\n", line.get("capacity_units_per_hour")));
            response.append(String.format("📈 Efficiency: %s%%\n", line.get("efficiency_percentage")));
            response.append(String.format("👥 Shift Schedule: %s\n", line.get("shift_schedule")));
            response.append(String.format("📅 Created: %s\n", line.get("created_date")));
            
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("getProductionLineById", response.toString(), duration);
            return response.toString();
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("getProductionLineById", e, duration);
            return String.format("❌ Error retrieving production line %s: %s", productionLineId, e.getMessage());
        }
    }
    
    @Tool("List all plants in the CPG manufacturing organization")
    public String listAllPlants() {
        long startTime = System.currentTimeMillis();
        logToolStart("listAllPlants");
        
        try {
            List<Map<String, Object>> results = executeNamedQuery("list_all_plants", new HashMap<>());
            
            if (results.isEmpty()) {
                return "No plants found in the CPG manufacturing organization.";
            }
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("🏭 CPG Manufacturing Plants (%d total):\n", results.size()));
            response.append(SEPARATOR_LINE).append("\n");
            
            for (Map<String, Object> rawPlant : results) {
                Map<String, Object> plant = extractDocumentData(rawPlant);
                response.append(String.format("🏭 %s | %s\n", 
                    plant.get("plant_id"), plant.get("name")));
                response.append(String.format("   📍 Location: %s\n", plant.get("location")));
                response.append(String.format("   📊 Status: %s | Code: %s\n", 
                    plant.get("status"), plant.get("plant_code")));
                response.append(String.format("   🏢 Type: %s | Capacity: %s\n", 
                    plant.get("facility_type"), plant.get("production_capacity")));
                response.append(String.format("   ⭐ Certification: %s\n\n", plant.get("quality_certification")));
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("listAllPlants", response.toString(), duration);
            return response.toString();
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("listAllPlants", e, duration);
            return String.format("❌ Error retrieving CPG plants: %s", e.getMessage());
        }
    }
    
    @Tool("Get specific plant details by plant ID")
    public String getPlantById(String plantId) {
        long startTime = System.currentTimeMillis();
        logToolStart("getPlantById", "plantId", plantId);
        
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("PLANT_ID", plantId);
            
            List<Map<String, Object>> results = executeNamedQuery("plant_by_id", params);
            
            if (results.isEmpty()) {
                return String.format("No plant found with ID: %s", plantId);
            }
            
            Map<String, Object> rawPlant = results.get(0);
            Map<String, Object> plant = extractDocumentData(rawPlant);
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("🏭 Plant Details: %s\n", plantId));
            response.append(SHORT_SEPARATOR_LINE).append("\n");
            response.append(String.format("📋 Name: %s\n", plant.get("name")));
            response.append(String.format("📍 Location: %s\n", plant.get("location")));
            response.append(String.format("📊 Status: %s\n", plant.get("status")));
            response.append(String.format("🏷️ Plant Code: %s\n", plant.get("plant_code")));
            response.append(String.format("🏢 Facility Type: %s\n", plant.get("facility_type")));
            response.append(String.format("⚡ Production Capacity: %s\n", plant.get("production_capacity")));
            response.append(String.format("⭐ Quality Certification: %s\n", plant.get("quality_certification")));
            response.append(String.format("👥 Shift Pattern: %s\n", plant.get("shift_pattern")));
            response.append(String.format("🌍 Timezone: %s\n", plant.get("timezone")));
            response.append(String.format("📅 Created: %s\n", plant.get("created_date")));
            
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("getPlantById", response.toString(), duration);
            return response.toString();
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("getPlantById", e, duration);
            return String.format("❌ Error retrieving plant %s: %s", plantId, e.getMessage());
        }
    }

    @Tool("List all solutions for a specific error code from CPG troubleshooting history")
    public String listAllSolutionsForErrorCode(String errorCode) {
        long startTime = System.currentTimeMillis();
        logToolStart("listAllSolutionsForErrorCode", "errorCode", errorCode);
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("ERROR_CODE", errorCode);
            List<Map<String, Object>> results = executeNamedQuery("list_all_solutions_for_error_code", params);
            if (results.isEmpty()) {
                return String.format("📚 No solutions found for error code %s.", errorCode);
            }
            StringBuilder response = new StringBuilder();
            response.append(String.format("📚 Solutions for Error Code %s (%d found):\n", errorCode, results.size()));
            response.append("=".repeat(50)).append("\n");
            int idx = 1;
            for (Map<String, Object> solution : results) {
                response.append(String.format("%d. 💡 %s\n", idx++, solution.getOrDefault("solution_comment", "No details available")));
            }
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("listAllSolutionsForErrorCode", response.toString(), duration);
            return response.toString();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("listAllSolutionsForErrorCode", e, duration);
            return String.format("❌ Error retrieving solutions for error code %s: %s", errorCode, e.getMessage());
        }
    }
    
    // Helper methods
    
    private List<Map<String, Object>> executeNamedQuery(String queryName, Map<String, Object> parameters) throws Exception {
        long startTime = System.currentTimeMillis();
        
        // Map query names to their target collections
        String collectionName = getCollectionForQuery(queryName);
        String url = String.format("%s/%s.%s.%s/_query/%s", 
            edgeServerBaseUrl, bucketName, scopeName, collectionName, queryName);
        
        logger.debug("Executing named query: {} with parameters: {} at URL: {}", queryName, parameters, url);
        
        String jsonRequest = objectMapper.writeValueAsString(parameters);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
            .timeout(Duration.ofSeconds(30))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            logger.error("Named query failed: {} returned status {}", queryName, response.statusCode());
            throw new RuntimeException("CPG named query failed with status: " + response.statusCode() + " for query: " + queryName);
        }
        
        JsonNode responseJson = objectMapper.readTree(response.body());
        List<Map<String, Object>> results = new ArrayList<>();
        
        if (responseJson.isArray()) {
            // Direct array response
            for (JsonNode result : responseJson) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.convertValue(result, Map.class);
                results.add(map);
            }
        } else if (responseJson.has("results")) {
            // Wrapped in results object
            JsonNode resultsArray = responseJson.get("results");
            for (JsonNode result : resultsArray) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.convertValue(result, Map.class);
                results.add(map);
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        logger.debug("Named query {} completed in {}ms, returned {} results", queryName, duration, results.size());
        
        return results;
    }
    
    private String getCollectionForQuery(String queryName) {
        switch (queryName) {
            case "telemetry_by_machine":
            case "telemetry_by_sensor_type":
            case "production_metrics_by_line":
            case "production_summary_metrics":
                return telemetryCollection;
            
            case "active_alerts":
            case "alerts_by_error_code":
            case "maintenance_alerts":
            case "production_summary_alerts":
                return alertsCollection;
            
            case "device_status":
                return deviceStatusCollection;
            
            case "quality_metrics_by_machine":
            case "production_summary_quality":
                return qualityMetricsCollection;
            
            case "list_all_machines":
            case "machine_by_id":
                return machinesCollection;
            
            case "list_all_production_lines":
            case "production_line_by_id":
                return productionLinesCollection;
            
            case "list_all_plants":
            case "plant_by_id":
                return plantsCollection;

            case "list_all_solutions_for_error_code":
                return solutionsCollection;
            
            default:
                throw new IllegalArgumentException("Unknown query name: " + queryName);
        }
    }
    
    private String getSeverityEmoji(String severity) {
        if (severity == null) return "ℹ️";
        
        switch (severity.toUpperCase()) {
            case "CRITICAL": return "🔴";
            case "HIGH": return "🟠";
            case "MEDIUM": return "🟡";
            case "LOW": return "🟢";
            default: return "ℹ️";
        }
    }
    
    private Map<String, List<Map<String, Object>>> groupByMachine(List<Map<String, Object>> data) {
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
        
        for (Map<String, Object> item : data) {
            String machineId = (String) item.get("machine_id");
            grouped.computeIfAbsent(machineId, k -> new ArrayList<>()).add(item);
        }
        
        return grouped;
    }
    
    /**
     * Helper method to extract data from nested query results.
     * Couchbase query results often nest document data under a "_" key.
     * This method checks for the nested structure and returns the actual data.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDocumentData(Map<String, Object> queryResult) {
        if (queryResult == null) {
            return new HashMap<>();
        }
        
        // Check if data is nested under "_" key
        if (queryResult.containsKey("_") && queryResult.get("_") instanceof Map) {
            return (Map<String, Object>) queryResult.get("_");
        }
        
        // Return the original map if not nested
        return queryResult;
    }
    

} 