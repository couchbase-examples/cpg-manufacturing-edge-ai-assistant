package com.couchbase.example.iot_device;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory class for generating simulated sensor readings for CPG manufacturing equipment
 * Creates realistic telemetry data for IoT device simulation
 * Based on CPG Manufacturing Equipment Technical Reference Manual v2.0
 */
public class SensorDataGenerator {
    
    /**
     * Creates a base simulated sensor reading map with common fields
     */
    public static Map<String, Object> createBaseSensorReading(String timestamp, String sensorType, String location,
                                                             int currentBatchNumber, String currentProductCode) {
        Map<String, Object> reading = new HashMap<>();
        reading.put("device_id", IoTDeviceConfig.DEVICE_ID);
        reading.put("machine_id", IoTDeviceConfig.MACHINE_ID);
        reading.put("machine_type", IoTDeviceConfig.MACHINE_TYPE);
        reading.put("sensor_type", sensorType);
        reading.put("timestamp", timestamp);
        reading.put("location", location);
        reading.put("batch_number", currentBatchNumber);
        reading.put("product_code", currentProductCode);
        reading.put("production_line", IoTDeviceConfig.PRODUCTION_LINE);
        reading.put("product_type", IoTDeviceConfig.PRODUCT_TYPE);
        return reading;
    }
    
    /**
     * Adds sensor value and unit to a reading map
     */
    public static void addSensorValue(Map<String, Object> reading, double value, String unit, int decimalPlaces) {
        double roundedValue = Math.round(value * Math.pow(10, decimalPlaces)) / Math.pow(10, decimalPlaces);
        reading.put("value", roundedValue);
        reading.put("unit", unit);
    }
    
    /**
     * Determines status based on threshold conditions
     */
    public static String getThresholdStatus(boolean isWarning) {
        return isWarning ? "warning" : "normal";
    }
    
    /**
     * Generates simulated fill level sensor reading
     */
    public static Map<String, Object> createFillLevelSensorReading(String timestamp, double currentFillLevel,
                                                                  int currentBatchNumber, String currentProductCode) {
        Map<String, Object> reading = createBaseSensorReading(timestamp, "fill_level", "filling_station",
                                                            currentBatchNumber, currentProductCode);
        addSensorValue(reading, currentFillLevel, "percent", 2);
        reading.put("status", getThresholdStatus(currentFillLevel < IoTDeviceConfig.FILL_LEVEL_WARNING_THRESHOLD));
        return reading;
    }
    
    /**
     * Generates simulated pressure sensor reading
     */
    public static Map<String, Object> createPressureSensorReading(String timestamp, double currentPressure,
                                                                 int currentBatchNumber, String currentProductCode) {
        Map<String, Object> reading = createBaseSensorReading(timestamp, "co2_pressure", "carbonation_system",
                                                            currentBatchNumber, currentProductCode);
        addSensorValue(reading, currentPressure, "bar", 2);
        boolean isWarning = currentPressure < IoTDeviceConfig.CO2_PRESSURE_WARNING_MIN || 
                           currentPressure > IoTDeviceConfig.CO2_PRESSURE_WARNING_MAX;
        reading.put("status", getThresholdStatus(isWarning));
        return reading;
    }
    
    /**
     * Generates simulated temperature sensor reading
     */
    public static Map<String, Object> createTemperatureSensorReading(String timestamp, double currentTemperature,
                                                                    int currentBatchNumber, String currentProductCode) {
        Map<String, Object> reading = createBaseSensorReading(timestamp, "product_temperature", "cooling_system",
                                                            currentBatchNumber, currentProductCode);
        addSensorValue(reading, currentTemperature, "celsius", 1);
        reading.put("status", getThresholdStatus(currentTemperature > IoTDeviceConfig.PRODUCT_TEMP_WARNING_THRESHOLD));
        return reading;
    }
    
    /**
     * Generates simulated speed sensor reading
     */
    public static Map<String, Object> createSpeedSensorReading(String timestamp, int currentSpeed,
                                                              int currentBatchNumber, String currentProductCode) {
        Map<String, Object> reading = createBaseSensorReading(timestamp, "line_speed", "conveyor_system",
                                                            currentBatchNumber, currentProductCode);
        reading.put("value", currentSpeed);
        reading.put("unit", "bottles_per_minute");
        reading.put("status", "normal");
        return reading;
    }
    
    /**
     * Generates simulated weight sensor reading
     */
    public static Map<String, Object> createWeightSensorReading(String timestamp, double currentWeight,
                                                               int currentBatchNumber, String currentProductCode) {
        Map<String, Object> reading = createBaseSensorReading(timestamp, "package_weight", "checkweigher",
                                                            currentBatchNumber, currentProductCode);
        addSensorValue(reading, currentWeight, "grams", 1);
        boolean isWarning = Math.abs(currentWeight - IoTDeviceConfig.PACKAGE_WEIGHT_TARGET_GRAMS) > 
                           IoTDeviceConfig.PACKAGE_WEIGHT_TOLERANCE_GRAMS;
        reading.put("status", getThresholdStatus(isWarning));
        return reading;
    }
    
    /**
     * Generates simulated production metrics reading
     */
    public static Map<String, Object> createProductionMetrics(String timestamp, int currentSpeed, double currentOEE,
                                                             int currentBatchNumber, String currentProductCode) {
        int bottlesProduced = currentSpeed; // Bottles per minute
        int defectCount = SimulationUtils.generateRandomInRange(0, 4); // 0-4 defects per minute
        int downtimeSeconds = SimulationUtils.generateRandomInRange(0, 14); // 0-14 seconds downtime
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("device_id", IoTDeviceConfig.DEVICE_ID);
        metrics.put("machine_id", IoTDeviceConfig.MACHINE_ID);
        metrics.put("machine_type", IoTDeviceConfig.MACHINE_TYPE);
        metrics.put("bottles_produced_per_minute", bottlesProduced);
        metrics.put("oee_percentage", Math.round(currentOEE * 1000.0) / 1000.0);
        metrics.put("defect_count_per_minute", defectCount);
        metrics.put("defect_rate_percentage", Math.round((defectCount / (double)bottlesProduced) * 10000.0) / 100.0);
        metrics.put("downtime_seconds", downtimeSeconds);
        metrics.put("availability_percentage", Math.round(((3600.0 - downtimeSeconds) / 3600.0) * 10000.0) / 100.0);
        metrics.put("performance_percentage", Math.round(((double)currentSpeed / IoTDeviceConfig.IDEAL_SPEED_TARGET) * 10000.0) / 100.0);
        metrics.put("quality_percentage", Math.round((1.0 - (defectCount / (double)bottlesProduced)) * 10000.0) / 100.0);
        metrics.put("timestamp", timestamp);
        metrics.put("batch_number", currentBatchNumber);
        metrics.put("product_code", currentProductCode);
        metrics.put("production_line", IoTDeviceConfig.PRODUCTION_LINE);
        metrics.put("product_type", IoTDeviceConfig.PRODUCT_TYPE);
        metrics.put("shift", SimulationUtils.getCurrentShift());
        metrics.put("manual_compliance", checkManualCompliance(currentSpeed, currentOEE));
        return metrics;
    }
    
    /**
     * Generates simulated environmental monitoring reading
     */
    public static Map<String, Object> createEnvironmentalMonitoringReading(String timestamp, double ambientTemperature, 
                                                                          double facilityHumidity) {
        Map<String, Object> reading = new HashMap<>();
        reading.put("device_id", IoTDeviceConfig.DEVICE_ID);
        reading.put("machine_id", IoTDeviceConfig.MACHINE_ID);
        reading.put("machine_type", IoTDeviceConfig.MACHINE_TYPE);
        reading.put("ambient_temperature_celsius", Math.round(ambientTemperature * 10.0) / 10.0);
        reading.put("facility_humidity_percent", Math.round(facilityHumidity * 10.0) / 10.0);
        reading.put("timestamp", timestamp);
        reading.put("location", "production_floor");
        reading.put("manual_section", "Section 13 - Environmental Compliance");
        
        // Environmental status based on Manual Section 9.2 specifications
        String tempStatus = (ambientTemperature >= IoTDeviceConfig.MIN_OPERATING_TEMP_C && 
                           ambientTemperature <= IoTDeviceConfig.MAX_OPERATING_TEMP_C) ? "normal" : "warning";
        String humidityStatus = (facilityHumidity >= IoTDeviceConfig.MIN_HUMIDITY_PERCENT && 
                               facilityHumidity <= IoTDeviceConfig.MAX_HUMIDITY_PERCENT) ? "normal" : "warning";
        
        reading.put("temperature_status", tempStatus);
        reading.put("humidity_status", humidityStatus);
        reading.put("overall_environmental_status", ("normal".equals(tempStatus) && "normal".equals(humidityStatus)) ? "normal" : "warning");
        
        reading.put("production_line", IoTDeviceConfig.PRODUCTION_LINE);
        reading.put("product_type", IoTDeviceConfig.PRODUCT_TYPE);
        return reading;
    }
    
    /**
     * Generates simulated utility system reading
     */
    public static Map<String, Object> createUtilitySystemReading(String timestamp, double airPressure, double waterPressure) {
        Map<String, Object> reading = new HashMap<>();
        reading.put("device_id", IoTDeviceConfig.DEVICE_ID);
        reading.put("machine_id", IoTDeviceConfig.MACHINE_ID);
        reading.put("machine_type", IoTDeviceConfig.MACHINE_TYPE);
        reading.put("compressed_air_pressure_psi", Math.round(airPressure * 10.0) / 10.0);
        reading.put("facility_water_pressure_psi", Math.round(waterPressure * 10.0) / 10.0);
        reading.put("timestamp", timestamp);
        reading.put("location", "utility_distribution");
        reading.put("manual_section", "Section 9.3 - Fluid Specifications");
        
        // Utility status based on Manual Section 9.3 specifications
        String airStatus = (airPressure >= IoTDeviceConfig.MIN_AIR_PRESSURE_PSI) ? "normal" : "warning";
        String waterStatus = (waterPressure >= IoTDeviceConfig.MIN_WATER_PRESSURE_PSI && 
                            waterPressure <= IoTDeviceConfig.MAX_WATER_PRESSURE_PSI) ? "normal" : "warning";
        
        reading.put("air_pressure_status", airStatus);
        reading.put("water_pressure_status", waterStatus);
        reading.put("overall_utility_status", ("normal".equals(airStatus) && "normal".equals(waterStatus)) ? "normal" : "warning");
        
        // Additional utility metrics
        reading.put("air_consumption_scfm", SimulationUtils.generateRandomInRange(IoTDeviceConfig.MIN_AIR_CONSUMPTION_SCFM, 
                                                                          IoTDeviceConfig.MAX_AIR_CONSUMPTION_SCFM));
        reading.put("water_flow_rate_gpm", SimulationUtils.generateRandomInRange(IoTDeviceConfig.MIN_WATER_FLOW_RATE_GPM, 
                                                                         IoTDeviceConfig.MAX_WATER_FLOW_RATE_GPM));
        reading.put("utility_efficiency_percent", SimulationUtils.generateRandomInRange(IoTDeviceConfig.MIN_UTILITY_EFFICIENCY_PERCENT, 
                                                                                IoTDeviceConfig.MAX_UTILITY_EFFICIENCY_PERCENT));
        
        reading.put("production_line", IoTDeviceConfig.PRODUCTION_LINE);
        reading.put("product_type", IoTDeviceConfig.PRODUCT_TYPE);
        return reading;
    }
    
    /**
     * Checks manual compliance for production metrics
     */
    private static String checkManualCompliance(int currentSpeed, double currentOEE) {
        boolean speedCompliant = (currentSpeed >= IoTDeviceConfig.MIN_SPEED_CONTAINERS_PER_MINUTE && 
                                currentSpeed <= IoTDeviceConfig.MAX_SPEED_CONTAINERS_PER_MINUTE);
        boolean oeeCompliant = currentOEE >= 0.8; // 80% OEE threshold
        
        if (speedCompliant && oeeCompliant) {
            return "fully_compliant";
        } else if (speedCompliant || oeeCompliant) {
            return "partially_compliant";
        } else {
            return "non_compliant";
        }
    }
    
    // Private constructor to prevent instantiation
    private SensorDataGenerator() {}
} 