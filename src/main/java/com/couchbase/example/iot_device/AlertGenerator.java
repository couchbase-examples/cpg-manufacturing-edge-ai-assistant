package com.couchbase.example.iot_device;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manager class for generating simulated alerts in the CPG manufacturing system
 * Creates realistic alert scenarios for IoT device simulation
 * Based on CPG Manufacturing Equipment Technical Reference Manual v2.0
 */
public class AlertGenerator {
    
    private final AtomicLong alertCounter = new AtomicLong(0);
    
    /**
     * Creates a base simulated alert map with common fields
     */
    public Map<String, Object> createBaseAlert(String alertType, String errorCode, String severity, String message, String timestamp) {
        long alertId = alertCounter.incrementAndGet();
        String alertIdString = String.format("CPG_%s_%s_%06d", 
                                           alertType.toUpperCase(),
                                           LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), 
                                           alertId);
        
        Map<String, Object> alert = new HashMap<>();
        alert.put("alert_id", alertIdString);
        alert.put("device_id", IoTDeviceConfig.DEVICE_ID);
        alert.put("machine_id", IoTDeviceConfig.MACHINE_ID);
        alert.put("machine_type", IoTDeviceConfig.MACHINE_TYPE);
        alert.put("error_code", errorCode);
        alert.put("severity", severity);
        alert.put("status", "active");
        alert.put("message", message);
        alert.put("timestamp", timestamp);
        alert.put("production_line", IoTDeviceConfig.PRODUCTION_LINE);
        alert.put("product_type", IoTDeviceConfig.PRODUCT_TYPE);
        return alert;
    }
    
    /**
     * Adds batch and product information to an alert
     */
    public void addBatchInfo(Map<String, Object> alert, int currentBatchNumber, String currentProductCode) {
        alert.put("batch_number", currentBatchNumber);
        alert.put("product_code", currentProductCode);
    }
    
    /**
     * Creates simulated sensor context information for alerts
     */
    public Map<String, Object> createSensorContext(double currentFillLevel, double currentPressure, 
                                                  double currentTemperature, int currentSpeed, double currentWeight,
                                                  double ambientTemperature, double facilityHumidity, 
                                                  double airPressure, double waterPressure, double currentOEE,
                                                  double fillAccuracy, double labelAlignment) {
        Map<String, Object> context = new HashMap<>();
        context.put("fill_level", currentFillLevel);
        context.put("pressure", currentPressure);
        context.put("temperature", currentTemperature);
        context.put("speed", currentSpeed);
        context.put("weight", currentWeight);
        context.put("ambient_temperature", ambientTemperature);
        context.put("humidity", facilityHumidity);
        context.put("air_pressure", airPressure);
        context.put("water_pressure", waterPressure);
        context.put("oee", currentOEE);
        context.put("fill_accuracy", fillAccuracy);
        context.put("label_alignment", labelAlignment);
        return context;
    }
    
    /**
     * Checks for environmental alerts based on current conditions
     */
    public void checkEnvironmentalAlerts(List<Map<String, Object>> alerts, String timestamp, 
                                       double ambientTemperature, int currentBatchNumber, String currentProductCode) {
        if (ambientTemperature < IoTDeviceConfig.MIN_OPERATING_TEMP_C || 
            ambientTemperature > IoTDeviceConfig.MAX_OPERATING_TEMP_C) {
            alerts.add(createManualErrorCodeAlert("E036", timestamp, "Ambient temperature out of operating range", 
                                                ambientTemperature, (IoTDeviceConfig.MIN_OPERATING_TEMP_C + IoTDeviceConfig.MAX_OPERATING_TEMP_C) / 2,
                                                currentBatchNumber, currentProductCode));
        }
    }
    
    /**
     * Checks for utility system alerts
     */
    public void checkUtilitySystemAlerts(List<Map<String, Object>> alerts, String timestamp, 
                                       double airPressure, double waterPressure, int currentBatchNumber, String currentProductCode) {
        if (airPressure < IoTDeviceConfig.MIN_AIR_PRESSURE_PSI) {
            alerts.add(createManualErrorCodeAlert("E009", timestamp, "Compressed air pressure below minimum", 
                                                airPressure, IoTDeviceConfig.MIN_AIR_PRESSURE_PSI,
                                                currentBatchNumber, currentProductCode));
        }
        
        if (waterPressure < IoTDeviceConfig.MIN_WATER_PRESSURE_PSI || 
            waterPressure > IoTDeviceConfig.MAX_WATER_PRESSURE_PSI) {
            alerts.add(createManualErrorCodeAlert("E037", timestamp, "Facility water pressure out of range", 
                                                waterPressure, (IoTDeviceConfig.MIN_WATER_PRESSURE_PSI + IoTDeviceConfig.MAX_WATER_PRESSURE_PSI) / 2,
                                                currentBatchNumber, currentProductCode));
        }
    }
    
    /**
     * Checks for production alerts
     */
    public void checkProductionAlerts(List<Map<String, Object>> alerts, String timestamp, int currentSpeed,
                                    double currentFillLevel, double currentPressure, double currentTemperature,
                                    double currentWeight, int currentBatchNumber, String currentProductCode) {
        // Check speed control
        if (currentSpeed < IoTDeviceConfig.MIN_SPEED_CONTAINERS_PER_MINUTE || 
            currentSpeed > IoTDeviceConfig.MAX_SPEED_CONTAINERS_PER_MINUTE) {
            alerts.add(createManualErrorCodeAlert("E018", timestamp, "Line speed out of specification", 
                                                currentSpeed, IoTDeviceConfig.IDEAL_SPEED_TARGET,
                                                currentBatchNumber, currentProductCode));
        }
        
        // Check fill level
        if (currentFillLevel < IoTDeviceConfig.FILL_LEVEL_CRITICAL_THRESHOLD) {
            alerts.add(createManualErrorCodeAlert("E045", timestamp, "Fill level sensor critical threshold", 
                                                 currentFillLevel, IoTDeviceConfig.FILL_LEVEL_WARNING_THRESHOLD,
                                                 currentBatchNumber, currentProductCode));
        } else if (currentFillLevel < IoTDeviceConfig.FILL_LEVEL_WARNING_THRESHOLD) {
            alerts.add(createAlert("FILL_002", "medium", "Fill level approaching minimum threshold", 
                                 currentFillLevel, IoTDeviceConfig.FILL_LEVEL_WARNING_THRESHOLD, timestamp,
                                 currentBatchNumber, currentProductCode));
        }
        
        // Check pressure
        if (currentPressure < IoTDeviceConfig.CO2_PRESSURE_WARNING_MIN || 
            currentPressure > IoTDeviceConfig.CO2_PRESSURE_WARNING_MAX) {
            alerts.add(createManualErrorCodeAlert("E045", timestamp, "CO2 pressure sensor out of specification", 
                                                 currentPressure, IoTDeviceConfig.CO2_PRESSURE_WARNING_MIN,
                                                 currentBatchNumber, currentProductCode));
        }
        
        // Check temperature control
        if (Math.abs(currentTemperature - 4.5) > IoTDeviceConfig.TEMPERATURE_CONTROL_TOLERANCE) {
            alerts.add(createManualErrorCodeAlert("E004", timestamp, "Product temperature control deviation", 
                                                 currentTemperature, 4.5, currentBatchNumber, currentProductCode));
        }
        
        // Check package weight accuracy
        if (Math.abs(currentWeight - IoTDeviceConfig.PACKAGE_WEIGHT_TARGET_GRAMS) > 
            (IoTDeviceConfig.PACKAGE_WEIGHT_TARGET_GRAMS * IoTDeviceConfig.FILL_WEIGHT_ACCURACY_TOLERANCE)) {
            alerts.add(createManualErrorCodeAlert("E019", timestamp, "Package weight position error", 
                                                 currentWeight, IoTDeviceConfig.PACKAGE_WEIGHT_TARGET_GRAMS,
                                                 currentBatchNumber, currentProductCode));
        }
    }
    
    /**
     * Checks for quality alerts
     */
    public void checkQualityAlerts(List<Map<String, Object>> alerts, String timestamp, 
                                 double fillAccuracy, double labelAlignment, double capTorque,
                                 int currentBatchNumber, String currentProductCode) {
        if (fillAccuracy < IoTDeviceConfig.MIN_FILL_ACCURACY_PERCENT) {
            alerts.add(createQualityAlert("QUAL_001", "medium", "Fill accuracy below Manual target", timestamp,
                                        fillAccuracy, labelAlignment, capTorque, currentBatchNumber, currentProductCode));
        }
        
        if (labelAlignment < IoTDeviceConfig.MIN_LABEL_ALIGNMENT_PERCENT) {
            alerts.add(createQualityAlert("QUAL_002", "medium", "Label alignment below Manual specification", timestamp,
                                        fillAccuracy, labelAlignment, capTorque, currentBatchNumber, currentProductCode));
        }
        
        if (capTorque < IoTDeviceConfig.MIN_CAP_TORQUE_INCH_POUNDS || 
            capTorque > IoTDeviceConfig.MAX_CAP_TORQUE_INCH_POUNDS) {
            alerts.add(createQualityAlert("QUAL_003", "high", "Cap torque out of Manual specification", timestamp,
                                        fillAccuracy, labelAlignment, capTorque, currentBatchNumber, currentProductCode));
        }
    }
    
    /**
     * Checks for system alerts (communication, electrical)
     */
    public void checkSystemAlerts(List<Map<String, Object>> alerts, String timestamp,
                                int currentBatchNumber, String currentProductCode) {
        // Communication and network alerts (2% chance)
        if (SimulationUtils.generateRandomInRange(1, 100) <= IoTDeviceConfig.COMMUNICATION_ERROR_PROBABILITY_PERCENT) {
            String[] commErrors = {"E014", "E052"};
            String errorCode = commErrors[SimulationUtils.generateRandomInRange(0, commErrors.length - 1)];
            alerts.add(createManualErrorCodeAlert(errorCode, timestamp, "Network communication issue detected", 
                                                0, 0, currentBatchNumber, currentProductCode));
        }
        
        // Electrical system alerts (1% chance for critical errors)
        if (SimulationUtils.generateRandomInRange(1, 100) <= IoTDeviceConfig.ELECTRICAL_ERROR_PROBABILITY_PERCENT) {
            String[] electricalErrors = {"E034", "E048", "E050"};
            String errorCode = electricalErrors[SimulationUtils.generateRandomInRange(0, electricalErrors.length - 1)];
            alerts.add(createManualErrorCodeAlert(errorCode, timestamp, "Electrical system fault detected", 
                                                0, 0, currentBatchNumber, currentProductCode));
        }
    }
    
    /**
     * Checks for maintenance alerts
     */
    public void checkMaintenanceAlerts(List<Map<String, Object>> alerts, String timestamp) {
        if (SimulationUtils.generateRandomInRange(1, 100) <= IoTDeviceConfig.MAINTENANCE_ALERT_PROBABILITY_PERCENT) {
            alerts.add(createManualMaintenanceAlert(timestamp));
        }
    }
    
    /**
     * Creates a standard alert
     */
    public Map<String, Object> createAlert(String errorCode, String severity, String message, 
                                         double currentValue, double threshold, String timestamp,
                                         int currentBatchNumber, String currentProductCode) {
        Map<String, Object> alert = createBaseAlert("alert", errorCode, severity, message, timestamp);
        addBatchInfo(alert, currentBatchNumber, currentProductCode);
        alert.put("current_value", Math.round(currentValue * 100.0) / 100.0);
        alert.put("threshold", threshold);
        return alert;
    }
    
    /**
     * Creates a quality alert
     */
    public Map<String, Object> createQualityAlert(String errorCode, String severity, String message, String timestamp,
                                                 double fillAccuracy, double labelAlignment, double capTorque,
                                                 int currentBatchNumber, String currentProductCode) {
        Map<String, Object> alert = createBaseAlert("quality_alert", errorCode, severity, message, timestamp);
        addBatchInfo(alert, currentBatchNumber, currentProductCode);
        alert.put("fill_accuracy", fillAccuracy);
        alert.put("label_alignment", labelAlignment);
        alert.put("cap_torque", capTorque);
        return alert;
    }
    
    /**
     * Creates a manual error code alert
     */
    public Map<String, Object> createManualErrorCodeAlert(String errorCode, String timestamp, String message, 
                                                         double currentValue, double threshold,
                                                         int currentBatchNumber, String currentProductCode) {
        IoTDeviceConfig.ErrorCodeInfo errorInfo = IoTDeviceConfig.ERROR_CODES.get(errorCode);
        String severity = (errorInfo != null) ? errorInfo.severity : "medium";
        
        Map<String, Object> alert = createBaseAlert("manual_error_alert", errorCode, severity, message, timestamp);
        addBatchInfo(alert, currentBatchNumber, currentProductCode);
        alert.put("current_value", Math.round(currentValue * 100.0) / 100.0);
        alert.put("threshold", threshold);
        alert.put("escalation_required", SimulationUtils.isEscalationRequired(severity));
        
        // Override alert_id to include error code
        long alertId = alertCounter.get();
        alert.put("alert_id", String.format("CPG_ERR_%s_%s_%06d", 
                                         errorCode,
                                         LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), 
                                         alertId));
        return alert;
    }
    
    /**
     * Creates a manual maintenance alert
     */
    public Map<String, Object> createManualMaintenanceAlert(String timestamp) {
        String maintenanceType = IoTDeviceConfig.MAINTENANCE_TYPES[SimulationUtils.generateRandomInRange(0, IoTDeviceConfig.MAINTENANCE_TYPES.length - 1)];
        String[] maintenanceCodes = {"E027", "E043", "MAINT_001"};
        String errorCode = maintenanceCodes[SimulationUtils.generateRandomInRange(0, maintenanceCodes.length - 1)];
        String message = "Scheduled maintenance per Manual Section 7: " + maintenanceType;
        
        Map<String, Object> alert = createBaseAlert("manual_maintenance_alert", errorCode, "low", message, timestamp);
        alert.put("status", "scheduled"); // Override status for maintenance alerts
        alert.put("maintenance_type", maintenanceType);
        alert.put("estimated_duration_minutes", SimulationUtils.getMaintenanceDuration(maintenanceType));
        alert.put("manual_section", "Section 7 - Preventive Maintenance");
        alert.put("next_due_date", SimulationUtils.calculateNextMaintenanceDate(maintenanceType));
        
        // Override alert_id for maintenance alerts
        long alertId = alertCounter.get();
        alert.put("alert_id", String.format("CPG_MAINT_%s_%06d", 
                                         LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), 
                                         alertId));
        return alert;
    }
    
    /**
     * Gets the current alert counter value
     */
    public long getAlertCounter() {
        return alertCounter.get();
    }
} 