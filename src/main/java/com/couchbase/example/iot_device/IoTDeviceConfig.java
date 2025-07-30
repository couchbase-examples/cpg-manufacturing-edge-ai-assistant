package com.couchbase.example.iot_device;

import java.util.Map;

/**
 * Configuration class for CPG Manufacturing IoT Device Simulation
 * Contains all operational parameters, thresholds, and specifications for realistic telemetry generation
 * Based on CPG Manufacturing Equipment Technical Reference Manual v2.0
 */
public class IoTDeviceConfig {
    
    // ===== DATABASE CONFIGURATION =====
    public static final String DATABASE_NAME = "cpg_manufacturing";
    public static final String SCOPE_NAME = "manufacturing";
    public static final String DATABASE_DIRECTORY = "./data/rpi-cpg-line1-01";
    
    // Collection Names
    public static final String TELEMETRY_COLLECTION = "telemetry";
    public static final String ALERTS_COLLECTION = "alerts";
    public static final String DEVICE_STATUS_COLLECTION = "device_status";
    public static final String QUALITY_COLLECTION = "quality_metrics";
    
    // ===== NETWORK CONFIGURATION =====
    public static final String EDGE_SERVER_URL = "ws://couchbase-edge-server:59840/cpg_manufacturing";
    
    // ===== TIMING CONFIGURATION =====
    public static final int TELEMETRY_INTERVAL_SECONDS = 30;
    public static final int ALERT_CHECK_INTERVAL_SECONDS = 15;
    public static final int DEVICE_STATUS_INTERVAL_SECONDS = 300;
    public static final int QUALITY_CHECK_INTERVAL_SECONDS = 120;
    public static final int WRITE_TIMEOUT_SECONDS = 10;
    public static final int SYNC_TIMEOUT_SECONDS = 60;
    public static final int SCHEDULER_THREAD_POOL_SIZE = 5;
    public static final int REPLICATION_HEARTBEAT_SECONDS = 60;
    public static final int REPLICATION_MAX_ATTEMPTS = 10;
    public static final int REPLICATION_MAX_WAIT_TIME_SECONDS = 300;
    
    // ===== OPERATIONAL RANGES - Based on Manual Technical Specifications =====
    
    // Speed Control (Section 9.2)
    public static final int MIN_SPEED_CONTAINERS_PER_MINUTE = 50;
    public static final int MAX_SPEED_CONTAINERS_PER_MINUTE = 600;
    public static final double SPEED_ACCURACY_TOLERANCE = 0.01;
    public static final int SPEED_VARIATION_RANGE = 10;
    public static final int IDEAL_SPEED_TARGET = 500;
    
    // Position and Motion Control
    public static final double POSITION_ACCURACY_INCHES = 0.001;
    public static final double TEMPERATURE_CONTROL_TOLERANCE = 2.0;
    public static final double PRESSURE_CONTROL_TOLERANCE = 2.0;
    
    // Environmental Limits (Section 13)
    public static final double MIN_OPERATING_TEMP_C = 10.0;
    public static final double MAX_OPERATING_TEMP_C = 40.0;
    public static final double TEMPERATURE_VARIATION_RANGE = 1.0;
    public static final double HUMIDITY_VARIATION_RANGE = 2.0;
    public static final double MIN_HUMIDITY_PERCENT = 10.0;
    public static final double MAX_HUMIDITY_PERCENT = 95.0;
    
    // Fluid Specifications (Section 9.3)
    public static final double MIN_AIR_PRESSURE_PSI = 100.0;
    public static final double MAX_AIR_PRESSURE_PSI = 120.0;
    public static final double MIN_WATER_PRESSURE_PSI = 40.0;
    public static final double MAX_WATER_PRESSURE_PSI = 80.0;
    public static final double AIR_PRESSURE_VARIATION_RANGE = 2.0;
    public static final double WATER_PRESSURE_VARIATION_RANGE = 1.0;
    
    // ===== PRODUCT SPECIFICATIONS =====
    
    // Fill Control
    public static final double FILL_LEVEL_MIN_PERCENT = 93.0;
    public static final double FILL_LEVEL_MAX_PERCENT = 97.0;
    public static final double FILL_LEVEL_VARIATION_RANGE = 0.5;
    public static final double FILL_LEVEL_WARNING_THRESHOLD = 94.0;
    public static final double FILL_LEVEL_CRITICAL_THRESHOLD = 93.5;
    
    // Carbonation Control
    public static final double CO2_PRESSURE_MIN_BAR = 3.0;
    public static final double CO2_PRESSURE_MAX_BAR = 3.5;
    public static final double CO2_PRESSURE_VARIATION_RANGE = 0.05;
    public static final double CO2_PRESSURE_WARNING_MIN = 3.1;
    public static final double CO2_PRESSURE_WARNING_MAX = 3.4;
    
    // Temperature Control
    public static final double PRODUCT_TEMP_MIN_C = 3.0;
    public static final double PRODUCT_TEMP_MAX_C = 6.0;
    public static final double PRODUCT_TEMP_VARIATION_RANGE = 0.2;
    public static final double PRODUCT_TEMP_WARNING_THRESHOLD = 5.5;
    
    // Weight Control
    public static final double PACKAGE_WEIGHT_MIN_GRAMS = 350.0;
    public static final double PACKAGE_WEIGHT_MAX_GRAMS = 360.0;
    public static final double PACKAGE_WEIGHT_TARGET_GRAMS = 355.0;
    public static final double PACKAGE_WEIGHT_VARIATION_RANGE = 2.0;
    public static final double PACKAGE_WEIGHT_TOLERANCE_GRAMS = 5.0;
    
    // ===== QUALITY CONTROL SPECIFICATIONS (Section 10) =====
    public static final double FILL_WEIGHT_ACCURACY_TOLERANCE = 0.01;
    public static final double MIN_FILL_ACCURACY_PERCENT = 98.5;
    public static final double MIN_LABEL_ALIGNMENT_PERCENT = 98.0;
    public static final double MIN_CAP_TORQUE_INCH_POUNDS = 17.0;
    public static final double MAX_CAP_TORQUE_INCH_POUNDS = 20.0;
    public static final double FILL_ACCURACY_VARIATION_RANGE = 0.1;
    public static final double LABEL_ALIGNMENT_VARIATION_RANGE = 0.15;
    public static final double CAP_TORQUE_VARIATION_RANGE = 0.3;
    
    // ===== UTILITY SYSTEM SPECIFICATIONS =====
    public static final int MIN_AIR_CONSUMPTION_SCFM = 15;
    public static final int MAX_AIR_CONSUMPTION_SCFM = 25;
    public static final int MIN_WATER_FLOW_RATE_GPM = 5;
    public static final int MAX_WATER_FLOW_RATE_GPM = 15;
    public static final int MIN_UTILITY_EFFICIENCY_PERCENT = 85;
    public static final int MAX_UTILITY_EFFICIENCY_PERCENT = 100;
    
    // ===== DEVICE STATUS THRESHOLDS =====
    public static final int MIN_MEMORY_USAGE_MB = 150;
    public static final int MAX_MEMORY_USAGE_MB = 250;
    public static final int MIN_CPU_USAGE_PERCENT = 15;
    public static final int MAX_CPU_USAGE_PERCENT = 35;
    public static final int MIN_DISK_USAGE_PERCENT = 45;
    public static final int MAX_DISK_USAGE_PERCENT = 55;
    
    // ===== ALERT PROBABILITIES =====
    public static final int COMMUNICATION_ERROR_PROBABILITY_PERCENT = 2;
    public static final int ELECTRICAL_ERROR_PROBABILITY_PERCENT = 1;
    public static final int MAINTENANCE_ALERT_PROBABILITY_PERCENT = 3;
    public static final int BATCH_CHANGE_PROBABILITY_PERCENT = 1;
    public static final int COUNTER_RESET_PROBABILITY = 120;
    
    // ===== DOCUMENT EXPIRATION DAYS =====
    public static final long TELEMETRY_EXPIRATION_DAYS = 30;
    public static final long QUALITY_EXPIRATION_DAYS = 60;
    public static final long ALERT_EXPIRATION_DAYS = 90;
    public static final long DEVICE_STATUS_EXPIRATION_DAYS = 7;
    
    // ===== MAINTENANCE DURATIONS (Minutes) =====
    public static final int THERMAL_INSPECTION_MIN_DURATION = 60;
    public static final int THERMAL_INSPECTION_MAX_DURATION = 30;
    public static final int MOTOR_ANALYSIS_MIN_DURATION = 45;
    public static final int MOTOR_ANALYSIS_MAX_DURATION = 45;
    public static final int CALIBRATION_MIN_DURATION = 90;
    public static final int CALIBRATION_MAX_DURATION = 60;
    public static final int SAFETY_CHECK_MIN_DURATION = 30;
    public static final int SAFETY_CHECK_MAX_DURATION = 30;
    public static final int DEFAULT_MAINTENANCE_MIN_DURATION = 45;
    public static final int DEFAULT_MAINTENANCE_MAX_DURATION = 90;
    
    // ===== DEVICE CONFIGURATION =====
    public static final String DEVICE_ID = System.getenv().getOrDefault("DEVICE_ID", "rpi-cpg-line1-01");
    public static final String MACHINE_ID = System.getenv().getOrDefault("MACHINE_ID", "MACHINE_001");
    public static final String MACHINE_TYPE = System.getenv().getOrDefault("MACHINE_TYPE", "beverage_filling_line");
    public static final String PRODUCTION_LINE = System.getenv().getOrDefault("PRODUCTION_LINE", "BEVERAGE_LINE_A");
    public static final String PRODUCT_TYPE = System.getenv().getOrDefault("PRODUCT_TYPE", "carbonated_beverage");
    
    // ===== ERROR CODES =====
    public static final Map<String, ErrorCodeInfo> ERROR_CODES = Map.ofEntries(
        Map.entry("E001", new ErrorCodeInfo("E001", "Non-volatile Memory Endurance Exceeded", "medium", "EEPROM/Flash memory write cycles exceeded design limit")),
        Map.entry("E004", new ErrorCodeInfo("E004", "Motor Overtemperature", "critical", "Motor temperature exceeded safe operating limits (>155°C)")),
        Map.entry("E005", new ErrorCodeInfo("E005", "IPM Fault", "critical", "Intelligent Power Module fault condition detected")),
        Map.entry("E009", new ErrorCodeInfo("E009", "Bus Undervoltage", "high", "DC bus voltage below minimum operating threshold (<350V typical)")),
        Map.entry("E010", new ErrorCodeInfo("E010", "Bus Overvoltage", "high", "DC bus voltage exceeded maximum safe limit (>800V typical)")),
        Map.entry("E012", new ErrorCodeInfo("E012", "Home Search Failed", "high", "Home position switch not detected during homing sequence")),
        Map.entry("E014", new ErrorCodeInfo("E014", "Communications Network Problem", "high", "Network cable disconnected or damaged")),
        Map.entry("E018", new ErrorCodeInfo("E018", "Overspeed Fault", "critical", "Motor speed exceeded maximum safe operating limit")),
        Map.entry("E019", new ErrorCodeInfo("E019", "Excess Position Error", "high", "Actual position deviates significantly from commanded position")),
        Map.entry("E022", new ErrorCodeInfo("E022", "Motor Thermal Protection Fault", "critical", "Motor winding temperature exceeded thermal protection limit")),
        Map.entry("E023", new ErrorCodeInfo("E023", "IPM Thermal Protection Fault", "critical", "Intelligent Power Module temperature exceeded safe limit")),
        Map.entry("E024", new ErrorCodeInfo("E024", "Excess Velocity Error", "high", "Actual velocity significantly different from commanded velocity")),
        Map.entry("E027", new ErrorCodeInfo("E027", "Axis Not Homed", "medium", "Homing sequence not completed after power-up")),
        Map.entry("E030", new ErrorCodeInfo("E030", "Encoder Communication Fault", "high", "Communication lost with absolute encoder")),
        Map.entry("E034", new ErrorCodeInfo("E034", "Ground Fault", "critical", "Ground fault condition detected in motor circuit")),
        Map.entry("E036", new ErrorCodeInfo("E036", "Power Circuitry Overtemperature", "critical", "Power semiconductors exceeded thermal limits")),
        Map.entry("E037", new ErrorCodeInfo("E037", "AC Line Loss", "critical", "Loss of incoming AC power supply")),
        Map.entry("E043", new ErrorCodeInfo("E043", "Drive Enable Input", "medium", "Drive enable signal not active")),
        Map.entry("E045", new ErrorCodeInfo("E045", "Sensor Failure", "medium", "Sensor output out of range or zero")),
        Map.entry("E048", new ErrorCodeInfo("E048", "Overcurrent Trip", "high", "Motor current exceeded drive protection limit")),
        Map.entry("E050", new ErrorCodeInfo("E050", "Memory Parity Error", "critical", "Corrupted memory in control system")),
        Map.entry("E052", new ErrorCodeInfo("E052", "Communication Timeout", "medium", "Communication link timeout with external device"))
    );
    
    // ===== MAINTENANCE TYPES =====
    public static final String[] MAINTENANCE_TYPES = {
        "conveyor_alignment_check", "precision_equipment_calibration", "control_system_parameter_verification",
        "safety_system_proof_testing", "thermal_imaging_inspection", "vibration_analysis",
        "motor_current_signature_analysis", "insulation_resistance_testing", "network_performance_testing",
        "backup_system_verification", "lubrication_schedule_maintenance", "bearing_temperature_check",
        "belt_tension_adjustment", "chain_lubrication", "coupling_alignment_verification"
    };
    
    // ===== PRODUCT CODES =====
    public static final String[] PRODUCT_CODES = {
        "CB_COLA_355", "CB_ORANGE_355", "CB_LEMON_355", "CB_GINGER_355"
    };
    
    /**
     * Error Code Information class
     */
    public static class ErrorCodeInfo {
        public final String code;
        public final String description;
        public final String severity;
        public final String cause;
        
        public ErrorCodeInfo(String code, String description, String severity, String cause) {
            this.code = code;
            this.description = description;
            this.severity = severity;
            this.cause = cause;
        }
    }
    
    // Private constructor to prevent instantiation
    private IoTDeviceConfig() {}
} 