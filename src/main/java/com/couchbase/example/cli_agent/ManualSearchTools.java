package com.couchbase.example.cli_agent;

import dev.langchain4j.agent.tool.Tool;
import com.couchbase.lite.*;
import com.couchbase.lite.Collection;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import static com.couchbase.example.cli_agent.ToolTraceLogger.*;

/**
 * LangChain4j Tools for performing vector search on CBLite manual collections.
 * 
 * Provides:
 * - Semantic vector search through manual content
 * - Manual data synchronization from Capella
 * - Local manual data management
 */
public class ManualSearchTools {
    
    // Constants
    private static final String CAPELLA_ENDPOINT =  System.getenv().getOrDefault("APP_SERVICES_ENDPOINT", "");
    private static final String CAPELLA_USERNAME = System.getenv().getOrDefault("APP_SERVICES_USERNAME", "");
    private static final String CAPELLA_PASSWORD = System.getenv().getOrDefault("APP_SERVICES_PASSWORD", "");

    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.1;
    
    // UI Constants
    private static final String SEPARATOR_LINE = "=".repeat(50);
    private static final String SHORT_SEPARATOR_LINE = "=".repeat(30);
    private static final String ERROR_PREFIX = "❌ Error ";
    private static final String SUCCESS_CHECKMARK = "✅";
    private static final String SEARCH_ICON = "🔍";
    private static final String SYNC_ICON = "🔄";
    private static final String BOOK_ICON = "📚";
    private static final String MANUAL_TYPE_FILTER_ANY = "any";
    
    // Logger and tracing configuration
    private static final Logger logger = com.couchbase.example.cli_agent.ToolTraceLogger.LOGGER;
    
    // Instance variables
    private final Collection manualsCollection;
    private final VectorSearchUtil vectorSearchUtil;
    private Replicator downwardReplicator;
    private volatile boolean isReplicationActive = false;

    
    public ManualSearchTools(Database database) throws CouchbaseLiteException {
        this.manualsCollection = initializeCollection(database);
        this.vectorSearchUtil = new VectorSearchUtil(database);
        
        initializeVectorIndexes();
        setupDownwardReplication();
    }
    
    @Tool("Search through equipment manuals and documentation using semantic search")
    public String searchManuals(String query, String machineType) {
        long startTime = System.currentTimeMillis();
        logToolStart("searchManuals", "query", query, "machineType", machineType);
        try {
            logToFile(String.format(SEARCH_ICON + " Searching manuals for: '%s' (machine type: %s)", query, machineType));
            
            long manualCount = manualsCollection.getCount();
            if (manualCount == 0) {
                String response = BOOK_ICON + " No manuals found locally. Manual sync may still be in progress. Please try again in a moment.";
                long duration = System.currentTimeMillis() - startTime;
                logToolEnd("searchManuals", response, duration);
                return response;
            }
            
            List<VectorSearchUtil.VectorSearchResult> vectorResults = vectorSearchUtil.vectorSearch(
                manualsCollection, query, "content", DEFAULT_SEARCH_LIMIT, DEFAULT_SIMILARITY_THRESHOLD
            );
            
            String response = formatSearchResults(query, vectorResults, manualCount, machineType);
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("searchManuals", response, duration);
            return response;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorMsg = String.format(ERROR_PREFIX + "searching manuals: %s", e.getMessage());
            logToolError("searchManuals", e, duration);
            logToFile(errorMsg);
            return errorMsg;
        }
    }
    
    @Tool("Get the status of manual data synchronization from Capella")
    public String getSyncStatus() {
        long startTime = System.currentTimeMillis();
        logToolStart("getSyncStatus");
        if (downwardReplicator == null) {
            String msg = logAndReturn(ERROR_PREFIX + "Manual replication not configured");
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("getSyncStatus", msg, duration);
            return msg;
        }
        
        ReplicatorStatus status = downwardReplicator.getStatus();
        long manualCount = manualsCollection.getCount();
        
        StringBuilder response = new StringBuilder();
        response.append("📡 Manual Data Sync Status:\n");
        response.append(SHORT_SEPARATOR_LINE).append("\n");
        response.append(String.format("Status: %s\n", status.getActivityLevel()));
        response.append(String.format("Progress: %d/%d documents\n", 
                                    status.getProgress().getCompleted(), 
                                    status.getProgress().getTotal()));
        response.append(String.format("Local manuals: %d\n", manualCount));
        
        if (status.getError() != null) {
            response.append(String.format("❌ Error: %s\n", status.getError().getMessage()));
        } else if (status.getActivityLevel() == ReplicatorActivityLevel.IDLE && manualCount > 0) {
            response.append(SUCCESS_CHECKMARK + " Manual data is synchronized\n");
        } else if (status.getActivityLevel() == ReplicatorActivityLevel.BUSY) {
            response.append(SYNC_ICON + " Synchronizing manual data...\n");
        }
        
        logToFile(String.format("Sync status requested - Status: %s, Local manuals: %d", 
                               status.getActivityLevel(), manualCount));
        long duration = System.currentTimeMillis() - startTime;
        logToolEnd("getSyncStatus", response.toString(), duration);
        return response.toString();
    }
    
    @Tool("Force manual data synchronization from Capella")
    public String forceSyncManuals() {
        long startTime = System.currentTimeMillis();
        logToolStart("forceSyncManuals");
        if (downwardReplicator == null) {
            String msg = logAndReturn(ERROR_PREFIX + "Manual replication not configured");
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("forceSyncManuals", msg, duration);
            return msg;
        }
        
        try {
            logToFile(SYNC_ICON + " Forcing manual data sync from Capella...");
            restartReplication();
            logToFile("Manual sync initiated successfully");
            String response = SYNC_ICON + " Manual sync initiated. Use getSyncStatus() to monitor progress.";
            long duration = System.currentTimeMillis() - startTime;
            logToolEnd("forceSyncManuals", response, duration);
            return response;
            
        } catch (TimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            String msg = "⚠️ Sync restart timed out, but may still be in progress";
            logToolError("forceSyncManuals", e, duration);
            return logAndReturn(msg);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logToolError("forceSyncManuals", e, duration);
            return logAndReturn(String.format(ERROR_PREFIX + "forcing sync: %s", e.getMessage()));
        }
    }
    
    // Private helper methods
    
    private Collection initializeCollection(Database database) throws CouchbaseLiteException {
        Collection tempCollection;
        try {
            tempCollection = database.createCollection("manuals", "manufacturing");
        } catch (CouchbaseLiteException e) {
            tempCollection = database.getCollection("manuals", "manufacturing");
            if (tempCollection == null) {
                throw new CouchbaseLiteException("Failed to create or get manuals collection", e);
            }
        }
        return tempCollection;
    }
    
    private String formatSearchResults(String query, List<VectorSearchUtil.VectorSearchResult> results, 
                                     long totalManuals, String machineType) {
        StringBuilder response = new StringBuilder();
        response.append(String.format("%s Manual Search Results for '%s' %d (searched %d local manuals):\n", 
                                     BOOK_ICON, query, results.size() ,totalManuals));
        response.append(SEPARATOR_LINE).append("\n");
        
        if (results.isEmpty()) {
            response.append("No relevant manuals found for the given query.\n");
            response.append("Try using different search terms or check if the manual data has been synced.\n");
            return response.toString();
        }
        
        int resultIndex = 1;
        for (VectorSearchUtil.VectorSearchResult result : results) {
            Map<String, Object> manual = result.getDocument();
            
            // Filter by machine type if specified
            if (machineType != null && !machineType.equals(MANUAL_TYPE_FILTER_ANY)) {
                String docMachineType = (String) manual.get("machine_type");
                if (docMachineType != null && !docMachineType.equalsIgnoreCase(machineType)) {
                    continue;
                }
            }
            
            response.append(formatSingleResult(resultIndex++, manual, result));
        }
        
        logToFile(String.format("Search completed. Found %d results for query: %s", results.size(), query));
        return response.toString();
    }
    
    private String formatSingleResult(int index, Map<String, Object> manual, 
                                    VectorSearchUtil.VectorSearchResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("   🏭 Machine Type: %s\n", manual.get("machine_type")));
        sb.append(String.format("   🎯 distance: %.7f%%\n", result.getDistance()));
        
        String content = (String) manual.get("content");
        sb.append(String.format("   📄 Content: %s\n", content));
        sb.append("\n");
        return sb.toString();
    }
    
    private void restartReplication() throws TimeoutException, InterruptedException {
        // Stop current replicator if running
        if (downwardReplicator.getStatus().getActivityLevel() != ReplicatorActivityLevel.STOPPED) {
            downwardReplicator.stop();
            waitForReplicationStop();
        }
        
        // Restart replication
        downwardReplicator.start();
    }
    
    private void waitForReplicationStop() throws TimeoutException, InterruptedException {
        CompletableFuture<Void> stopFuture = CompletableFuture.runAsync(() -> {
            while (downwardReplicator.getStatus().getActivityLevel() != ReplicatorActivityLevel.STOPPED) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        try {
            stopFuture.get(10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new InterruptedException("Execution failed: " + e.getMessage());
        }
    }
    
        private void logToFile(String message) {
        // File logging removed; now delegate to shared logger
        logger.info(message);
    }
    
    private String logAndReturn(String message) {
        logToFile(message);
        return message;
    }
    
    private void setupDownwardReplication() {
        try {
            System.out.println("📡 Setting up downward replication for manual data from Capella");
            
            URI capellaUri = URI.create(CAPELLA_ENDPOINT).normalize();
            URLEndpoint endpoint = new URLEndpoint(capellaUri);
            
            ReplicatorConfiguration config = createReplicationConfig(endpoint);
            
            downwardReplicator = new Replicator(config);
            downwardReplicator.addChangeListener(this::handleDownwardReplicationChange);
            downwardReplicator.start();
            isReplicationActive = true;
            
            System.out.println("✅ Downward replication started for manual data");
            
        } catch (Exception e) {
            System.err.println("❌ Error setting up manual data replication: " + e.getMessage());
        }
    }
    
    private ReplicatorConfiguration createReplicationConfig(URLEndpoint endpoint) {
        ReplicatorConfiguration config = new ReplicatorConfiguration(endpoint);
        
        // Configure collection
        CollectionConfiguration collectionConfig = new CollectionConfiguration();
        collectionConfig.setChannels(Arrays.asList("manuals"));
        config.addCollection(manualsCollection, collectionConfig);
        
        // Configure replication settings
        config.setType(ReplicatorType.PULL);
        config.setContinuous(true);
        config.setAutoPurgeEnabled(false);
        config.setHeartbeat(60);
        config.setMaxAttempts(10);
        config.setMaxAttemptWaitTime(300);
        config.setAcceptOnlySelfSignedServerCertificate(false);
        
        // Configure authentication if available
        if (!CAPELLA_USERNAME.isEmpty() && !CAPELLA_PASSWORD.isEmpty()) {
            config.setAuthenticator(new BasicAuthenticator(CAPELLA_USERNAME, CAPELLA_PASSWORD.toCharArray()));
            System.out.println("✅ Authentication configured for Capella sync");
        } else {
            System.out.println("⚠️ No Capella credentials provided - using anonymous access");
        }
        
        return config;
    }
    
    private void handleDownwardReplicationChange(ReplicatorChange change) {
        ReplicatorStatus status = change.getStatus();
        
        logToFile(String.format("📡 Manual sync status: %s | Progress: %d/%d | Local manuals: %d", 
                               status.getActivityLevel(),
                               status.getProgress().getCompleted(),
                               status.getProgress().getTotal(),
                               manualsCollection.getCount()));
        
        if (status.getError() != null) {
            logToFile("❌ Manual sync error: " + status.getError().getMessage());
        } else if (status.getActivityLevel() == ReplicatorActivityLevel.IDLE && 
                   status.getProgress().getCompleted() > 0) {
            logToFile("✅ Manual data synchronized successfully");
        }
    }
    
    private void initializeVectorIndexes() {
        try {
            if (manualsCollection != null) {
                vectorSearchUtil.createDefaultVectorIndex(manualsCollection, "content");
                System.out.println("✅ Vector indexes initialized for manual collection");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Could not initialize vector indexes: " + e.getMessage());
        }
    }
    
    /**
     * Shutdown the replicator and clean up resources
     */
    public void shutdown() {
        if (downwardReplicator != null && isReplicationActive) {
            logger.info("⏹️ Stopping manual data replication...");
            downwardReplicator.stop();
            try {
                waitForReplicationStop();
                logger.info("✅ Manual data replication stopped successfully");
            } catch (Exception e) {
                logger.warn("⚠️ Issue while stopping replication: {}", e.getMessage());
            }
            isReplicationActive = false;
        }
    }
    
    /**
     * Get the current count of manuals in the local collection
     */
    public long getManualCount() {
        return manualsCollection.getCount();
    }
} 