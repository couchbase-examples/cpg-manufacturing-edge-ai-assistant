package com.couchbase.example.cli_agent;

import dev.langchain4j.agent.tool.Tool;
import com.couchbase.lite.*;
import com.couchbase.lite.Collection;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
    private static final String CAPELLA_ENDPOINT = System.getenv().getOrDefault("CAPELLA_APP_SERVICES_ENDPOINT", "");
    private static final String CAPELLA_USERNAME = System.getenv().getOrDefault("CAPELLA_USERNAME", "");
    private static final String CAPELLA_PASSWORD = System.getenv().getOrDefault("CAPELLA_PASSWORD", "");
    private static final int SYNC_TIMEOUT_SECONDS = 60;
    private static final String LOG_FILE_PATH = "data/manual_search_tools.log";
    private static final DateTimeFormatter LOG_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.6;
    
    // Instance variables
    private final Collection manualsCollection;
    private final VectorSearchUtil vectorSearchUtil;
    private Replicator downwardReplicator;
    private volatile boolean isReplicationActive = false;
    private PrintWriter logWriter;
    
    public ManualSearchTools(Database database) throws CouchbaseLiteException {
        this.manualsCollection = initializeCollection(database);
        this.vectorSearchUtil = new VectorSearchUtil(database);
        
        initializeFileLogging();
        initializeVectorIndexes();
        setupDownwardReplication();
    }
    
    @Tool("Search through equipment manuals and documentation using semantic search")
    public String searchManuals(String query, String machineType) {
        try {
            logToFile(String.format("🔍 Searching manuals for: '%s' (machine type: %s)", query, machineType));
            
            long manualCount = manualsCollection.getCount();
            if (manualCount == 0) {
                return "📚 No manuals found locally. Manual sync may still be in progress. Please try again in a moment.";
            }
            
            List<VectorSearchUtil.VectorSearchResult> vectorResults = vectorSearchUtil.vectorSearch(
                manualsCollection, query, "content", DEFAULT_SEARCH_LIMIT, DEFAULT_SIMILARITY_THRESHOLD
            );
            
            return formatSearchResults(query, vectorResults, manualCount, machineType);
            
        } catch (Exception e) {
            String errorMsg = String.format("❌ Error searching manuals: %s", e.getMessage());
            logToFile(errorMsg);
            return errorMsg;
        }
    }
    
    @Tool("Get the status of manual data synchronization from Capella")
    public String getSyncStatus() {
        if (downwardReplicator == null) {
            return logAndReturn("❌ Manual replication not configured");
        }
        
        ReplicatorStatus status = downwardReplicator.getStatus();
        long manualCount = manualsCollection.getCount();
        
        StringBuilder response = new StringBuilder();
        response.append("📡 Manual Data Sync Status:\n");
        response.append("=".repeat(30)).append("\n");
        response.append(String.format("Status: %s\n", status.getActivityLevel()));
        response.append(String.format("Progress: %d/%d documents\n", 
                                    status.getProgress().getCompleted(), 
                                    status.getProgress().getTotal()));
        response.append(String.format("Local manuals: %d\n", manualCount));
        
        if (status.getError() != null) {
            response.append(String.format("❌ Error: %s\n", status.getError().getMessage()));
        } else if (status.getActivityLevel() == ReplicatorActivityLevel.IDLE && manualCount > 0) {
            response.append("✅ Manual data is synchronized\n");
        } else if (status.getActivityLevel() == ReplicatorActivityLevel.BUSY) {
            response.append("🔄 Synchronizing manual data...\n");
        }
        
        logToFile(String.format("Sync status requested - Status: %s, Local manuals: %d", 
                               status.getActivityLevel(), manualCount));
        return response.toString();
    }
    
    @Tool("Force manual data synchronization from Capella")
    public String forceSyncManuals() {
        if (downwardReplicator == null) {
            return logAndReturn("❌ Manual replication not configured");
        }
        
        try {
            logToFile("🔄 Forcing manual data sync from Capella...");
            restartReplication();
            logToFile("Manual sync initiated successfully");
            return "🔄 Manual sync initiated. Use getSyncStatus() to monitor progress.";
            
        } catch (TimeoutException e) {
            return logAndReturn("⚠️ Sync restart timed out, but may still be in progress");
        } catch (Exception e) {
            return logAndReturn(String.format("❌ Error forcing sync: %s", e.getMessage()));
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
        response.append(String.format("📚 Manual Search Results for '%s' (searched %d local manuals):\n", 
                                     query, totalManuals));
        response.append("=".repeat(50)).append("\n");
        
        if (results.isEmpty()) {
            response.append("No relevant manuals found for the given query.\n");
            response.append("Try using different search terms or check if the manual data has been synced.\n");
            return response.toString();
        }
        
        int resultIndex = 1;
        for (VectorSearchUtil.VectorSearchResult result : results) {
            Map<String, Object> manual = result.getDocument();
            
            // Filter by machine type if specified
            if (machineType != null && !machineType.equals("any")) {
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
        sb.append(String.format("%d. 📖 %s\n", index, manual.get("title")));
        sb.append(String.format("   🏭 Machine Type: %s\n", manual.get("machine_type")));
        sb.append(String.format("   📝 Version: %s\n", manual.get("manual_version")));
        sb.append(String.format("   🎯 Relevance: %.1f%%\n", result.getSimilarity() * 100));
        
        String content = (String) manual.get("content");
        if (content != null) {
            String preview = content.length() > 200 ? content.substring(0, 200) + "..." : content;
            sb.append(String.format("   📄 Content: %s\n", preview));
        }
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
    
    private void initializeFileLogging() {
        try {
            new java.io.File("data").mkdirs();
            logWriter = new PrintWriter(new FileWriter(LOG_FILE_PATH, true));
            logToFile("=== ManualSearchTools logging session started ===");
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (logWriter != null) {
                    logToFile("=== ManualSearchTools logging session ended ===");
                    logWriter.close();
                }
            }));
            
        } catch (IOException e) {
            System.err.println("❌ Failed to initialize file logging: " + e.getMessage());
        }
    }
    
    private void logToFile(String message) {
        if (logWriter != null) {
            String timestamp = LocalDateTime.now().format(LOG_TIMESTAMP_FORMAT);
            logWriter.println(String.format("[%s] %s", timestamp, message));
            logWriter.flush();
        }
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
            logToFile("⏹️ Stopping manual data replication...");
            downwardReplicator.stop();
            
            try {
                waitForReplicationStop();
                logToFile("✅ Manual data replication stopped successfully");
            } catch (TimeoutException e) {
                logToFile("⚠️ Manual replication stop timed out");
            } catch (Exception e) {
                logToFile("❌ Error stopping manual replication: " + e.getMessage());
            }
            
            isReplicationActive = false;
        }
        
        if (logWriter != null) {
            logToFile("=== ManualSearchTools shutdown completed ===");
            logWriter.close();
            logWriter = null;
        }
    }
    
    /**
     * Get the current count of manuals in the local collection
     */
    public long getManualCount() {
        return manualsCollection.getCount();
    }
} 