package com.couchbase.example.iot_device;

import com.couchbase.lite.*;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CPG Telemetry Data Writer for Raspberry Pi Devices (Refactored Version)
 * Generates real-time CPG production telemetry, quality alerts, and device status data
 * Based on CPG Manufacturing Equipment Technical Reference Manual v2.0
 * Syncs operational data upward: Pi → Edge Server → Capella
 */
public class CPGIoTTelemetryGenerator {
    
    // Component instances
    private final AlertGenerator alertGenerator = new AlertGenerator();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(IoTDeviceConfig.SCHEDULER_THREAD_POOL_SIZE);
    private final AtomicInteger documentCounter = new AtomicInteger(0);
    private volatile boolean running = true;
    
    // Database components
    private Database database;
    private com.couchbase.lite.Collection telemetryCollection;
    private com.couchbase.lite.Collection alertsCollection;
    private com.couchbase.lite.Collection deviceStatusCollection;
    private com.couchbase.lite.Collection qualityCollection;
    private Replicator upwardReplicator;
    
    // CPG production simulation state - Based on Manual Specifications
    private final Random random = new Random();
    private double currentFillLevel = 95.0;
    private double currentPressure = 3.2;
    private double currentTemperature = 4.5;
    private int currentSpeed = 450;
    private double currentWeight = 355.0;
    private int currentBatchNumber = 1001;
    private String currentProductCode = SimulationUtils.generateProductCode();
    
    // Quality metrics - Based on Manual Section 10 Quality Control
    private double fillAccuracy = 99.2;
    private double labelAlignment = 98.8;
    private double capTorque = 18.5;
    
    // Environmental monitoring - Based on Manual Section 13
    private double ambientTemperature = 22.0;
    private double facilityHumidity = 45.0;
    private double airPressure = 105.0;
    private double waterPressure = 60.0;
    
    // Operational Equipment Effectiveness (OEE) tracking
    private double currentOEE = 0.85;
    private int defectCount = 0;
    private int downtimeSeconds = 0;
    
    // ===== MAIN APPLICATION LIFECYCLE =====
    
    public static void main(String[] args) {
        CPGIoTTelemetryGenerator app = new CPGIoTTelemetryGenerator();
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
        
        try {
            app.run();
        } catch (Exception e) {
            System.err.println("❌ Error running CPG production telemetry generator: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void run() throws CouchbaseLiteException {
        printStartupInfo();
        
        // Initialize CBLite
        CouchbaseLite.init();
        
        // Create database and collections
        createDatabaseAndCollections();
        
        // Setup upward replication (operational data to Edge Server)
        setupUpwardReplication();
        
        // Start data generation tasks
        startDataGenerationTasks();
        
        // Wait for shutdown signal
        waitForShutdown();
        
        System.out.println("\n✅ CPG production telemetry generation completed!");
        printDatabaseSummary();
    }
    
    private void printStartupInfo() {
        System.out.println("🏭 Starting CPG Production Telemetry Data Generation");
        System.out.println("=".repeat(70));
        System.out.println("📊 CPG Device Configuration:");
        System.out.println("   - Device ID: " + IoTDeviceConfig.DEVICE_ID);
        System.out.println("   - Machine ID: " + IoTDeviceConfig.MACHINE_ID);
        System.out.println("   - Machine Type: " + IoTDeviceConfig.MACHINE_TYPE);
        System.out.println("   - Production Line: " + IoTDeviceConfig.PRODUCTION_LINE);
        System.out.println("   - Product Type: " + IoTDeviceConfig.PRODUCT_TYPE);
        System.out.println("   - Current Batch: " + currentBatchNumber);
        System.out.println("   - Product Code: " + currentProductCode);
        System.out.println("📡 Sync Configuration:");
        System.out.println("   - Edge Server: " + IoTDeviceConfig.EDGE_SERVER_URL);
        System.out.println("   - Telemetry interval: " + IoTDeviceConfig.TELEMETRY_INTERVAL_SECONDS + " seconds");
        System.out.println("   - Alert check interval: " + IoTDeviceConfig.ALERT_CHECK_INTERVAL_SECONDS + " seconds");
        System.out.println("   - Quality check interval: " + IoTDeviceConfig.QUALITY_CHECK_INTERVAL_SECONDS + " seconds");
        System.out.println("=".repeat(70));
    }
    
    // ===== DATABASE SETUP AND OPERATIONS =====
    
    private void createDatabaseAndCollections() throws CouchbaseLiteException {
        createDatabase();
        createOperationalCollections();
        printCollectionSummary();
    }
    
    private void createDatabase() throws CouchbaseLiteException {
        DatabaseConfiguration config = new DatabaseConfiguration();
        config.setDirectory(IoTDeviceConfig.DATABASE_DIRECTORY);
        
        database = new Database(IoTDeviceConfig.DATABASE_NAME, config);
        System.out.println("✅ Database created/opened: " + database.getName());
    }
    
    private void createOperationalCollections() throws CouchbaseLiteException {
        telemetryCollection = createOrOpenCollection(IoTDeviceConfig.TELEMETRY_COLLECTION, "Telemetry");
        alertsCollection = createOrOpenCollection(IoTDeviceConfig.ALERTS_COLLECTION, "Alerts");
        deviceStatusCollection = createOrOpenCollection(IoTDeviceConfig.DEVICE_STATUS_COLLECTION, "Device status");
        qualityCollection = createOrOpenCollection(IoTDeviceConfig.QUALITY_COLLECTION, "Quality metrics");
    }
    
    private com.couchbase.lite.Collection createOrOpenCollection(String collectionName, String displayName) throws CouchbaseLiteException {
        com.couchbase.lite.Collection collection = database.getCollection(collectionName, IoTDeviceConfig.SCOPE_NAME);
        if (collection == null) {
            collection = database.createCollection(collectionName, IoTDeviceConfig.SCOPE_NAME);
            System.out.println("✅ " + displayName + " collection created: " + collectionName);
        } else {
            System.out.println("✅ " + displayName + " collection opened: " + collectionName);
        }
        return collection;
    }
    
    private void printCollectionSummary() throws CouchbaseLiteException {
        System.out.println("📊 Collection info:");
        System.out.println("   - Telemetry documents: " + telemetryCollection.getCount());
        System.out.println("   - Alert documents: " + alertsCollection.getCount());
        System.out.println("   - Device status documents: " + deviceStatusCollection.getCount());
        System.out.println("   - Quality metrics documents: " + qualityCollection.getCount());
    }
    
    // ===== DATA GENERATION SCHEDULING =====
    
    private void startDataGenerationTasks() {
        startTelemetryGeneration();
        startAlertGeneration();
        startDeviceStatusGeneration();
        startQualityDataGeneration();
    }
    
    private void startTelemetryGeneration() {
        System.out.println("\n📊 Starting CPG telemetry data generation every " + IoTDeviceConfig.TELEMETRY_INTERVAL_SECONDS + " seconds");
        
        scheduler.scheduleAtFixedRate(() -> {
            if (running) {
                try {
                    generateTelemetryData();
                } catch (Exception e) {
                    SimulationUtils.logError("telemetry data generation", e);
                }
            }
        }, 0, IoTDeviceConfig.TELEMETRY_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    private void startAlertGeneration() {
        System.out.println("🚨 Starting alert generation every " + IoTDeviceConfig.ALERT_CHECK_INTERVAL_SECONDS + " seconds");
        
        scheduler.scheduleAtFixedRate(() -> {
            if (running) {
                try {
                    generateAlerts();
                } catch (Exception e) {
                    SimulationUtils.logError("alert generation", e);
                }
            }
        }, IoTDeviceConfig.ALERT_CHECK_INTERVAL_SECONDS, IoTDeviceConfig.ALERT_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    private void startDeviceStatusGeneration() {
        System.out.println("💻 Starting device status generation every " + IoTDeviceConfig.DEVICE_STATUS_INTERVAL_SECONDS + " seconds");
        
        scheduler.scheduleAtFixedRate(() -> {
            if (running) {
                try {
                    generateDeviceStatus();
                } catch (Exception e) {
                    SimulationUtils.logError("device status generation", e);
                }
            }
        }, IoTDeviceConfig.DEVICE_STATUS_INTERVAL_SECONDS, IoTDeviceConfig.DEVICE_STATUS_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    private void startQualityDataGeneration() {
        System.out.println("🔍 Starting quality metrics generation every " + IoTDeviceConfig.QUALITY_CHECK_INTERVAL_SECONDS + " seconds");
        
        scheduler.scheduleAtFixedRate(() -> {
            if (running) {
                try {
                    generateQualityMetrics();
                } catch (Exception e) {
                    SimulationUtils.logError("quality metrics generation", e);
                }
            }
        }, IoTDeviceConfig.QUALITY_CHECK_INTERVAL_SECONDS, IoTDeviceConfig.QUALITY_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    // ===== DATA GENERATION METHODS =====
    
    private void generateTelemetryData() throws CouchbaseLiteException {
        String timestamp = Instant.now().toString();
        System.out.println("\n📊 Generating CPG telemetry data at " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME));
        
        // Update sensor readings
        simulateSensorReadings();
        
        // Create telemetry batch using SensorDataGenerator
        List<Map<String, Object>> telemetryBatch = Arrays.asList(
            SensorDataGenerator.createFillLevelSensorReading(timestamp, currentFillLevel, currentBatchNumber, currentProductCode),
            SensorDataGenerator.createPressureSensorReading(timestamp, currentPressure, currentBatchNumber, currentProductCode),
            SensorDataGenerator.createTemperatureSensorReading(timestamp, currentTemperature, currentBatchNumber, currentProductCode),
            SensorDataGenerator.createSpeedSensorReading(timestamp, currentSpeed, currentBatchNumber, currentProductCode),
            SensorDataGenerator.createWeightSensorReading(timestamp, currentWeight, currentBatchNumber, currentProductCode),
            SensorDataGenerator.createProductionMetrics(timestamp, currentSpeed, currentOEE, currentBatchNumber, currentProductCode),
            SensorDataGenerator.createEnvironmentalMonitoringReading(timestamp, ambientTemperature, facilityHumidity),
            SensorDataGenerator.createUtilitySystemReading(timestamp, airPressure, waterPressure)
        );
        
        // Write telemetry data with timeout
        CompletableFuture<Integer> writeFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return persistTelemetryData(telemetryBatch);
            } catch (CouchbaseLiteException e) {
                throw new RuntimeException("Telemetry write failed", e);
            }
        });
        
        Integer documentsWritten = SimulationUtils.executeWithTimeout(writeFuture, "telemetry write");
        if (documentsWritten != null) {
            System.out.println("✅ CPG telemetry data generated: " + documentsWritten + " documents");
        }
    }
    
    private void simulateSensorReadings() {
        updateProductionSensorReadings();
        updateQualityMetrics();
        updateEnvironmentalConditions();
        updateUtilityPressures();
        calculateOEEMetrics();
        handlePeriodicResets();
        handleBatchChanges();
    }
    
    private void updateProductionSensorReadings() {
        currentFillLevel = SimulationUtils.generateGaussianVariation(currentFillLevel, IoTDeviceConfig.FILL_LEVEL_VARIATION_RANGE, 
                                                             IoTDeviceConfig.FILL_LEVEL_MIN_PERCENT, IoTDeviceConfig.FILL_LEVEL_MAX_PERCENT);
        currentPressure = SimulationUtils.generateGaussianVariation(currentPressure, IoTDeviceConfig.CO2_PRESSURE_VARIATION_RANGE, 
                                                            IoTDeviceConfig.CO2_PRESSURE_MIN_BAR, IoTDeviceConfig.CO2_PRESSURE_MAX_BAR);
        currentTemperature = SimulationUtils.generateGaussianVariation(currentTemperature, IoTDeviceConfig.PRODUCT_TEMP_VARIATION_RANGE, 
                                                               IoTDeviceConfig.PRODUCT_TEMP_MIN_C, IoTDeviceConfig.PRODUCT_TEMP_MAX_C);
        
        currentSpeed += (int)(random.nextGaussian() * IoTDeviceConfig.SPEED_VARIATION_RANGE);
        currentSpeed = SimulationUtils.clampValue(currentSpeed, IoTDeviceConfig.MIN_SPEED_CONTAINERS_PER_MINUTE, IoTDeviceConfig.MAX_SPEED_CONTAINERS_PER_MINUTE);
        
        currentWeight = SimulationUtils.generateGaussianVariation(currentWeight, IoTDeviceConfig.PACKAGE_WEIGHT_VARIATION_RANGE, 
                                                          IoTDeviceConfig.PACKAGE_WEIGHT_MIN_GRAMS, IoTDeviceConfig.PACKAGE_WEIGHT_MAX_GRAMS);
    }
    
    private void updateQualityMetrics() {
        fillAccuracy = SimulationUtils.generateGaussianVariation(fillAccuracy, IoTDeviceConfig.FILL_ACCURACY_VARIATION_RANGE, 
                                                          IoTDeviceConfig.MIN_FILL_ACCURACY_PERCENT, 100.0);
        labelAlignment = SimulationUtils.generateGaussianVariation(labelAlignment, IoTDeviceConfig.LABEL_ALIGNMENT_VARIATION_RANGE, 
                                                           IoTDeviceConfig.MIN_LABEL_ALIGNMENT_PERCENT, 100.0);
        capTorque = SimulationUtils.generateGaussianVariation(capTorque, IoTDeviceConfig.CAP_TORQUE_VARIATION_RANGE, 
                                                      IoTDeviceConfig.MIN_CAP_TORQUE_INCH_POUNDS, IoTDeviceConfig.MAX_CAP_TORQUE_INCH_POUNDS);
    }
    
    private void updateEnvironmentalConditions() {
        ambientTemperature = SimulationUtils.generateGaussianVariation(ambientTemperature, IoTDeviceConfig.TEMPERATURE_VARIATION_RANGE, 
                                                               IoTDeviceConfig.MIN_OPERATING_TEMP_C, IoTDeviceConfig.MAX_OPERATING_TEMP_C);
        facilityHumidity = SimulationUtils.generateGaussianVariation(facilityHumidity, IoTDeviceConfig.HUMIDITY_VARIATION_RANGE, 
                                                             IoTDeviceConfig.MIN_HUMIDITY_PERCENT, IoTDeviceConfig.MAX_HUMIDITY_PERCENT);
    }
    
    private void updateUtilityPressures() {
        airPressure = SimulationUtils.generateGaussianVariation(airPressure, IoTDeviceConfig.AIR_PRESSURE_VARIATION_RANGE, 
                                                        IoTDeviceConfig.MIN_AIR_PRESSURE_PSI, IoTDeviceConfig.MAX_AIR_PRESSURE_PSI);
        waterPressure = SimulationUtils.generateGaussianVariation(waterPressure, IoTDeviceConfig.WATER_PRESSURE_VARIATION_RANGE, 
                                                          IoTDeviceConfig.MIN_WATER_PRESSURE_PSI, IoTDeviceConfig.MAX_WATER_PRESSURE_PSI);
    }
    
    private void calculateOEEMetrics() {
        double availability = (3600.0 - downtimeSeconds) / 3600.0;
        double performance = (double)currentSpeed / IoTDeviceConfig.IDEAL_SPEED_TARGET;
        double quality = 1.0 - (defectCount / (double)currentSpeed);
        currentOEE = availability * performance * quality;
    }
    
    private void handlePeriodicResets() {
        if (random.nextInt(IoTDeviceConfig.COUNTER_RESET_PROBABILITY) < 1) {
            defectCount = 0;
            downtimeSeconds = 0;
        }
    }
    
    private void handleBatchChanges() {
        if (random.nextInt(100) < IoTDeviceConfig.BATCH_CHANGE_PROBABILITY_PERCENT) {
            currentBatchNumber++;
            currentProductCode = SimulationUtils.generateProductCode();
            System.out.println("🔄 Started new batch: " + currentBatchNumber + " - " + currentProductCode);
        }
    }
    
    private void generateAlerts() throws CouchbaseLiteException {
        String timestamp = Instant.now().toString();
        List<Map<String, Object>> alerts = new ArrayList<>();
        
        // Use AlertGenerator to check for different types of alerts
        alertGenerator.checkEnvironmentalAlerts(alerts, timestamp, ambientTemperature, currentBatchNumber, currentProductCode);
        alertGenerator.checkUtilitySystemAlerts(alerts, timestamp, airPressure, waterPressure, currentBatchNumber, currentProductCode);
        alertGenerator.checkProductionAlerts(alerts, timestamp, currentSpeed, currentFillLevel, currentPressure, 
                                         currentTemperature, currentWeight, currentBatchNumber, currentProductCode);
        alertGenerator.checkQualityAlerts(alerts, timestamp, fillAccuracy, labelAlignment, capTorque, currentBatchNumber, currentProductCode);
        alertGenerator.checkSystemAlerts(alerts, timestamp, currentBatchNumber, currentProductCode);
        alertGenerator.checkMaintenanceAlerts(alerts, timestamp);
        
        if (!alerts.isEmpty()) {
            persistAlertData(alerts);
            System.out.println("🚨 Generated " + alerts.size() + " CPG alert(s) based on Manual specifications");
        }
    }
    
    private void generateQualityMetrics() throws CouchbaseLiteException {
        String timestamp = Instant.now().toString();
        
        Map<String, Object> qualityData = new HashMap<>();
        qualityData.put("device_id", IoTDeviceConfig.DEVICE_ID);
        qualityData.put("machine_id", IoTDeviceConfig.MACHINE_ID);
        qualityData.put("machine_type", IoTDeviceConfig.MACHINE_TYPE);
        qualityData.put("fill_accuracy_percentage", Math.round(fillAccuracy * 100.0) / 100.0);
        qualityData.put("label_alignment_percentage", Math.round(labelAlignment * 100.0) / 100.0);
        qualityData.put("cap_torque_inch_pounds", Math.round(capTorque * 10.0) / 10.0);
        qualityData.put("timestamp", timestamp);
        qualityData.put("batch_number", currentBatchNumber);
        qualityData.put("product_code", currentProductCode);
        qualityData.put("production_line", IoTDeviceConfig.PRODUCTION_LINE);
        qualityData.put("product_type", IoTDeviceConfig.PRODUCT_TYPE);
        qualityData.put("quality_grade", SimulationUtils.calculateQualityGrade(fillAccuracy, labelAlignment));
        
        String docId = UUID.randomUUID().toString();
        CompletableFuture<Void> writeFuture = writeDocumentAsync(qualityCollection, docId, qualityData, IoTDeviceConfig.QUALITY_EXPIRATION_DAYS);
        
        if (SimulationUtils.executeVoidWithTimeout(writeFuture, "quality metrics generation")) {
            System.out.println("🔍 Quality metrics generated - Grade: " + SimulationUtils.calculateQualityGrade(fillAccuracy, labelAlignment));
        }
    }
    
    private void generateDeviceStatus() throws CouchbaseLiteException {
        String timestamp = Instant.now().toString();
        
        Map<String, Object> deviceStatus = new HashMap<>();
        deviceStatus.put("device_id", IoTDeviceConfig.DEVICE_ID);
        deviceStatus.put("timestamp", timestamp);
        deviceStatus.put("status", "online");
        deviceStatus.put("uptime_seconds", System.currentTimeMillis() / 1000);
        deviceStatus.put("memory_usage_mb", SimulationUtils.generateRandomInRange(IoTDeviceConfig.MIN_MEMORY_USAGE_MB, IoTDeviceConfig.MAX_MEMORY_USAGE_MB));
        deviceStatus.put("cpu_usage_percent", SimulationUtils.generateRandomInRange(IoTDeviceConfig.MIN_CPU_USAGE_PERCENT, IoTDeviceConfig.MAX_CPU_USAGE_PERCENT));
        deviceStatus.put("disk_usage_percent", SimulationUtils.generateRandomInRange(IoTDeviceConfig.MIN_DISK_USAGE_PERCENT, IoTDeviceConfig.MAX_DISK_USAGE_PERCENT));
        deviceStatus.put("network_status", "connected");
        deviceStatus.put("sync_status", upwardReplicator != null ? upwardReplicator.getStatus().getActivityLevel().toString() : "stopped");
        deviceStatus.put("documents_pending_sync", upwardReplicator != null ? upwardReplicator.getStatus().getProgress().getTotal() - 
                                                                             upwardReplicator.getStatus().getProgress().getCompleted() : 0);
        
        String docId = UUID.randomUUID().toString();
        CompletableFuture<Void> writeFuture = writeDocumentAsync(deviceStatusCollection, docId, deviceStatus, IoTDeviceConfig.DEVICE_STATUS_EXPIRATION_DAYS);
        
        if (SimulationUtils.executeVoidWithTimeout(writeFuture, "device status generation")) {
            System.out.println("💻 Device status generated");
        }
    }
    
    // ===== DATA WRITING OPERATIONS =====
    
    private int persistTelemetryData(List<Map<String, Object>> telemetryData) throws CouchbaseLiteException {
        database.inBatch(() -> {
            for (Map<String, Object> data : telemetryData) {
                try {
                    String docId = UUID.randomUUID().toString();
                    MutableDocument document = new MutableDocument(docId, data);
                    telemetryCollection.save(document);
                    setDocumentExpiration(telemetryCollection, docId, SimulationUtils.calculateExpirationDate(IoTDeviceConfig.TELEMETRY_EXPIRATION_DAYS));
                } catch (CouchbaseLiteException e) {
                    System.err.println("   ❌ Error saving telemetry document: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            }
        });
        
        return telemetryData.size();
    }
    
    private void persistAlertData(List<Map<String, Object>> alerts) throws CouchbaseLiteException {
        database.inBatch(() -> {
            for (Map<String, Object> alert : alerts) {
                try {
                    String docId = (String) alert.get("alert_id");
                    // Remove alert_id from the body before persisting so it only exists as the document key.
                    Map<String, Object> alertCopy = new HashMap<>(alert);
                    alertCopy.remove("alert_id");

                    MutableDocument document = new MutableDocument(docId, alertCopy);
                    alertsCollection.save(document);
                    setDocumentExpiration(alertsCollection, docId, SimulationUtils.calculateExpirationDate(IoTDeviceConfig.ALERT_EXPIRATION_DAYS));
                } catch (CouchbaseLiteException e) {
                    System.err.println("   ❌ Error saving alert document: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            }
        });
    }
    
    private CompletableFuture<Void> writeDocumentAsync(com.couchbase.lite.Collection collection, String docId, 
                                                      Map<String, Object> data, long expirationDays) {
        return CompletableFuture.runAsync(() -> {
            try {
                MutableDocument document = new MutableDocument(docId, data);
                collection.save(document);
                setDocumentExpiration(collection, docId, SimulationUtils.calculateExpirationDate(expirationDays));
            } catch (CouchbaseLiteException e) {
                throw new RuntimeException("Error saving document " + docId, e);
            }
        });
    }
    
    private void setDocumentExpiration(com.couchbase.lite.Collection collection, String docId, Date expiration) {
        try {
            collection.setDocumentExpiration(docId, expiration);
        } catch (CouchbaseLiteException e) {
            System.err.println("⚠️ Warning: Could not set expiration for document " + docId + ": " + e.getMessage());
        }
    }
    
    // ===== REPLICATION SETUP AND HANDLING =====
    
    private void setupUpwardReplication() throws CouchbaseLiteException {
        System.out.println("\n📡 Setting up upward replication for CPG operational data");
        
        URI edgeServerUri = URI.create(IoTDeviceConfig.EDGE_SERVER_URL).normalize();
        URLEndpoint endpoint = new URLEndpoint(edgeServerUri);
        
        ReplicatorConfiguration config = new ReplicatorConfiguration(endpoint);
        
        config.addCollection(telemetryCollection, null);
        config.addCollection(alertsCollection, null);
        config.addCollection(deviceStatusCollection, null);
        config.addCollection(qualityCollection, null);
        
        config.setType(ReplicatorType.PUSH);
        config.setContinuous(true);
        
        config.setHeartbeat(IoTDeviceConfig.REPLICATION_HEARTBEAT_SECONDS);
        config.setMaxAttempts(IoTDeviceConfig.REPLICATION_MAX_ATTEMPTS);
        config.setMaxAttemptWaitTime(IoTDeviceConfig.REPLICATION_MAX_WAIT_TIME_SECONDS);
        
        upwardReplicator = new Replicator(config);
        upwardReplicator.addChangeListener(this::handleUpwardReplicationChange);
        upwardReplicator.start();
        
        System.out.println("✅ CPG upward replication started");
        System.out.println("📊 Upward sync configuration:");
        System.out.println("   - Type: PUSH (device → edge server → capella)");
        System.out.println("   - Collections: telemetry, alerts, device_status, quality_metrics");
        System.out.println("   - Target: " + IoTDeviceConfig.EDGE_SERVER_URL);
        System.out.println("   - Continuous: true");
    }
    
    private void handleUpwardReplicationChange(ReplicatorChange change) {
        ReplicatorStatus status = change.getStatus();
        String activityLevel = status.getActivityLevel().toString();
        
        System.out.println("📡 Upward sync status: " + activityLevel + 
                          " | Progress: " + status.getProgress().getCompleted() + 
                          "/" + status.getProgress().getTotal());
        
        if (status.getError() != null) {
            System.err.println("❌ Upward replication error: " + status.getError().getMessage());
        }
        
        if (status.getActivityLevel() == ReplicatorActivityLevel.IDLE && 
            status.getProgress().getCompleted() > 0) {
            System.out.println("✅ Operational data synchronized successfully");
        }
    }
    
    // ===== LIFECYCLE MANAGEMENT =====
    
    private void waitForShutdown() {
        System.out.println("\n🔄 CPG production telemetry generation is running. Press Ctrl+C to stop.");
        System.out.println("📊 Simulating " + IoTDeviceConfig.MACHINE_TYPE + " on " + IoTDeviceConfig.PRODUCTION_LINE);
        System.out.println("🚨 Active alert generation and quality monitoring");
        System.out.println("🍾 Generating data for " + IoTDeviceConfig.PRODUCT_TYPE + " - Batch " + currentBatchNumber);
        
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void shutdown() {
        System.out.println("\n🛑 Shutting down CPG production telemetry generator...");
        running = false;
        
        // Stop scheduled tasks
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Stop upward replication
        if (upwardReplicator != null) {
            System.out.println("⏹️ Stopping upward replication...");
            upwardReplicator.stop();
            
            try {
                CompletableFuture<Void> stopFuture = CompletableFuture.runAsync(() -> {
                    while (upwardReplicator.getStatus().getActivityLevel() != ReplicatorActivityLevel.STOPPED) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                });
                
                stopFuture.get(IoTDeviceConfig.SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                System.out.println("✅ Upward replication stopped successfully");
            } catch (TimeoutException e) {
                System.err.println("⚠️ Replication stop timed out");
            } catch (Exception e) {
                System.err.println("❌ Error stopping replication: " + e.getMessage());
            }
        }
        
        // Close database
        if (database != null) {
            try {
                database.close();
                System.out.println("✅ Database closed successfully");
            } catch (CouchbaseLiteException e) {
                System.err.println("❌ Error closing database: " + e.getMessage());
            }
        }
        
        System.out.println("✅ CPG production telemetry generator shutdown completed");
    }
    
    private void printDatabaseSummary() {
        System.out.println("\n📊 Final CPG Database Summary:");
        System.out.println("   - Device ID: " + IoTDeviceConfig.DEVICE_ID);
        System.out.println("   - Machine ID: " + IoTDeviceConfig.MACHINE_ID);
        System.out.println("   - Machine Type: " + IoTDeviceConfig.MACHINE_TYPE);
        System.out.println("   - Product Type: " + IoTDeviceConfig.PRODUCT_TYPE);
        System.out.println("   - Database name: " + database.getName());
        System.out.println("   - Telemetry documents: " + telemetryCollection.getCount());
        System.out.println("   - Alert documents: " + alertsCollection.getCount());
        System.out.println("   - Device status documents: " + deviceStatusCollection.getCount());
        System.out.println("   - Quality metrics documents: " + qualityCollection.getCount());
        System.out.println("   - Database path: " + database.getPath());
        System.out.println("   - Edge Server: " + IoTDeviceConfig.EDGE_SERVER_URL);
        System.out.println("   - Documents generated: " + documentCounter.get());
        System.out.println("   - Alerts generated: " + alertGenerator.getAlertCounter());
        System.out.println("   - Current batch: " + currentBatchNumber);
        System.out.println("   - Current product: " + currentProductCode);
    }
} 