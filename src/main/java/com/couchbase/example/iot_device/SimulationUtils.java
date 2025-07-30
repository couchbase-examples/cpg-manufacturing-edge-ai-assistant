package com.couchbase.example.iot_device;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility class for common CPG manufacturing simulation operations
 * Provides helper methods for generating realistic sensor data and managing IoT device simulation
 */
public class SimulationUtils {
    
    private static final Random random = new Random();
    
    /**
     * Calculate expiration date by adding the specified days to current time
     */
    public static Date calculateExpirationDate(long days) {
        return new Date(System.currentTimeMillis() + days * 24 * 60 * 60 * 1000);
    }
    
    /**
     * Clamps a value between min and max bounds
     */
    public static double clampValue(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Clamps an integer value between min and max bounds
     */
    public static int clampValue(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Generates a random value in a range using Gaussian distribution
     */
    public static double generateGaussianVariation(double currentValue, double variationRange, double min, double max) {
        double newValue = currentValue + (random.nextGaussian() * variationRange);
        return clampValue(newValue, min, max);
    }
    
    /**
     * Generates a random integer in a range
     */
    public static int generateRandomInRange(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }
    
    /**
     * Gets the current shift based on hour of day
     */
    public static String getCurrentShift() {
        int hour = LocalDateTime.now().getHour();
        if (hour >= 6 && hour < 14) return "day";
        else if (hour >= 14 && hour < 22) return "evening";
        else return "night";
    }
    
    /**
     * Generates a random product code
     */
    public static String generateProductCode() {
        return IoTDeviceConfig.PRODUCT_CODES[random.nextInt(IoTDeviceConfig.PRODUCT_CODES.length)];
    }
    
    /**
     * Calculates quality grade based on average quality metrics
     */
    public static String calculateQualityGrade(double fillAccuracy, double labelAlignment) {
        double avgQuality = (fillAccuracy + labelAlignment) / 2.0;
        if (avgQuality >= 99.0) return "A+";
        else if (avgQuality >= 98.5) return "A";
        else if (avgQuality >= 98.0) return "B+";
        else if (avgQuality >= 97.5) return "B";
        else return "C";
    }
    
    /**
     * Executes a future with timeout and standardized error handling
     */
    public static <T> T executeWithTimeout(CompletableFuture<T> future, String operationName) {
        try {
            return future.get(IoTDeviceConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.err.println("⚠️ " + operationName + " operation timed out");
            future.cancel(true);
            return null;
        } catch (Exception e) {
            System.err.println("❌ Error during " + operationName + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Executes a void future with timeout and standardized error handling
     */
    public static boolean executeVoidWithTimeout(CompletableFuture<Void> future, String operationName) {
        try {
            future.get(IoTDeviceConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (TimeoutException e) {
            System.err.println("⚠️ " + operationName + " operation timed out");
            future.cancel(true);
            return false;
        } catch (Exception e) {
            System.err.println("❌ Error during " + operationName + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Logs errors with consistent formatting
     */
    public static void logError(String operation, Exception e) {
        System.err.println("❌ Error in " + operation + ": " + e.getMessage());
        if (e.getCause() != null) {
            System.err.println("   Caused by: " + e.getCause().getMessage());
        }
    }
    
    /**
     * Logs warnings with consistent formatting
     */
    public static void logWarning(String operation, String message) {
        System.err.println("⚠️ Warning in " + operation + ": " + message);
    }
    
    /**
     * Checks if escalation is required based on severity
     */
    public static boolean isEscalationRequired(String severity) {
        return "critical".equals(severity) || "high".equals(severity);
    }
    
    /**
     * Gets the manual section for a given error code
     */
    public static String getManualSectionForError(String errorCode) {
        switch (errorCode) {
            case "E001":
            case "E050":
                return "Section 6 - Error Code Troubleshooting - Memory Issues";
            case "E004":
            case "E022":
            case "E023":
            case "E036":
                return "Section 6 - Error Code Troubleshooting - Thermal Protection";
            case "E005":
            case "E048":
            case "E034":
            case "E037":
                return "Section 6 - Error Code Troubleshooting - Electrical Faults";
            case "E009":
            case "E010":
                return "Section 6 - Error Code Troubleshooting - Power Supply";
            case "E018":
            case "E024":
                return "Section 6 - Error Code Troubleshooting - Motion Control";
            case "E012":
            case "E019":
            case "E027":
                return "Section 6 - Error Code Troubleshooting - Position Control";
            case "E014":
            case "E052":
                return "Section 6 - Error Code Troubleshooting - Communications";
            case "E030":
            case "E045":
                return "Section 6 - Error Code Troubleshooting - Sensor Issues";
            case "E043":
                return "Section 6 - Error Code Troubleshooting - Safety Systems";
            default:
                return "Section 6 - Error Code Troubleshooting";
        }
    }
    
    /**
     * Gets maintenance duration based on maintenance type
     */
    public static int getMaintenanceDuration(String maintenanceType) {
        switch (maintenanceType) {
            case "thermal_imaging_inspection":
            case "vibration_analysis":
                return IoTDeviceConfig.THERMAL_INSPECTION_MIN_DURATION + 
                       random.nextInt(IoTDeviceConfig.THERMAL_INSPECTION_MAX_DURATION - 
                                     IoTDeviceConfig.THERMAL_INSPECTION_MIN_DURATION + 1);
            case "motor_current_signature_analysis":
            case "insulation_resistance_testing":
                return IoTDeviceConfig.MOTOR_ANALYSIS_MIN_DURATION + 
                       random.nextInt(IoTDeviceConfig.MOTOR_ANALYSIS_MAX_DURATION - 
                                     IoTDeviceConfig.MOTOR_ANALYSIS_MIN_DURATION + 1);
            case "conveyor_alignment_check":
            case "precision_equipment_calibration":
                return IoTDeviceConfig.CALIBRATION_MIN_DURATION + 
                       random.nextInt(IoTDeviceConfig.CALIBRATION_MAX_DURATION - 
                                     IoTDeviceConfig.CALIBRATION_MIN_DURATION + 1);
            case "control_system_parameter_verification":
            case "safety_system_proof_testing":
                return IoTDeviceConfig.SAFETY_CHECK_MIN_DURATION + 
                       random.nextInt(IoTDeviceConfig.SAFETY_CHECK_MAX_DURATION - 
                                     IoTDeviceConfig.SAFETY_CHECK_MIN_DURATION + 1);
            default:
                return IoTDeviceConfig.DEFAULT_MAINTENANCE_MIN_DURATION + 
                       random.nextInt(IoTDeviceConfig.DEFAULT_MAINTENANCE_MAX_DURATION - 
                                     IoTDeviceConfig.DEFAULT_MAINTENANCE_MIN_DURATION + 1);
        }
    }
    
    /**
     * Calculates next maintenance date based on maintenance type
     */
    public static String calculateNextMaintenanceDate(String maintenanceType) {
        int daysUntilNext;
        switch (maintenanceType) {
            case "lubrication_schedule_maintenance":
            case "bearing_temperature_check":
                daysUntilNext = 7; // Weekly
                break;
            case "belt_tension_adjustment":
            case "chain_lubrication":
                daysUntilNext = 30; // Monthly
                break;
            case "thermal_imaging_inspection":
            case "vibration_analysis":
            case "motor_current_signature_analysis":
                daysUntilNext = 90; // Quarterly
                break;
            default:
                daysUntilNext = 30; // Monthly default
        }
        
        LocalDateTime nextDate = LocalDateTime.now().plusDays(daysUntilNext);
        return nextDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
    
    // Private constructor to prevent instantiation
    private SimulationUtils() {}
} 