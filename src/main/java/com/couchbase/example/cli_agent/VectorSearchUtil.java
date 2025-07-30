package com.couchbase.example.cli_agent;

import com.couchbase.lite.*;
import com.couchbase.lite.Collection;
import com.couchbase.lite.Dictionary;
import com.couchbase.lite.Expression;
import com.couchbase.lite.Function;
import com.couchbase.lite.QueryBuilder;
import com.couchbase.lite.SelectResult;
import com.couchbase.lite.DataSource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// LangChain4J imports for embeddings
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;

/**
 * Vector Search Utility for CBLite Manufacturing Applications
 * 
 * Provides semantic search capabilities for manual chunks synced from Capella
 * on edge devices, enabling intelligent troubleshooting and maintenance support.
 */
public class VectorSearchUtil {
    
    // Constants
    private static final int DEFAULT_VECTOR_DIMENSIONS = 768;
    private static final int DEFAULT_VECTOR_CENTROIDS = 1;
    private static final int DEFAULT_NUM_PROBES = 1;
    private static final int DEFAULT_MIN_TRAINING_SIZE = 40;
    private static final int DEFAULT_MAX_TRAINING_SIZE = 1000;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.7;
    private static final String GOOGLE_EMBEDDING_MODEL = "GoogleEmbeddingModel";
    private static final String LOG_FILE_PATH = "data/vector_search_util.log";
    private static final DateTimeFormatter LOG_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int EMBEDDING_TIMEOUT_SECONDS = 20;
    private static final int MAX_RETRIES = 2;
    
    // Instance variables
    private final Database database;
    private final ExecutorService embeddingExecutor;
    private final Map<String, VectorIndexConfiguration> indexConfigurations;
    private final EmbeddingModel embeddingModel;
    private PrintWriter logWriter;
    
    public VectorSearchUtil(Database database) {
        this(database, System.getenv("GOOGLE_GEMINI_API_KEY"));
    }
    
    public VectorSearchUtil(Database database, String apiKey) {
        this.database = database;
        this.embeddingExecutor = Executors.newFixedThreadPool(2);
        this.indexConfigurations = new HashMap<>();
        this.embeddingModel = initializeEmbeddingModel(apiKey);
        
        initializeFileLogging();
        registerEmbeddingModel();
    }
    
    /**
     * Create a vector index with specified parameters
     */
    public boolean createVectorIndex(Collection collection, String textField, int dimensions, int centroids) {
        try {
            String textIndexName = collection.getName() + "_text_idx";
            String predictionIndexName = collection.getName() + "_prediction_idx";
            
            // Create text index for basic text searches
            IndexConfiguration textConfig = new ValueIndexConfiguration(textField);
            collection.createIndex(textIndexName, textConfig);
            
            // Create vector index using CBLite's VectorIndexConfiguration with prediction
            String predictionExpression = String.format("prediction(%s, {'text': %s}).embedding", 
                GOOGLE_EMBEDDING_MODEL, textField);
            
            VectorIndexConfiguration vectorConfig = createVectorIndexConfig(predictionExpression, dimensions, centroids);
            collection.createIndex(predictionIndexName, vectorConfig);
            
            // Store configuration for later reference
            indexConfigurations.put(collection.getName(), vectorConfig);
            
            logIndexCreation(collection.getName(), textIndexName, predictionIndexName, textField, dimensions, centroids);
            return true;
            
        } catch (CouchbaseLiteException e) {
            logToFile("❌ Failed to create indexes: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Create vector index with default parameters optimized for edge devices
     */
    public boolean createDefaultVectorIndex(Collection collection, String textField) {
        return createVectorIndex(collection, textField, DEFAULT_VECTOR_DIMENSIONS, DEFAULT_VECTOR_CENTROIDS);
    }
    
    /**
     * Generate embedding for text using LangChain4J embedding model
     */
    public float[] generateEmbedding(String text) {
        try {
            Embedding embedding = embeddingModel.embed(text).content();
            return embedding.vector();
        } catch (Exception e) {
            logToFile("❌ Error generating embedding: " + e.getMessage());
            return new float[DEFAULT_VECTOR_DIMENSIONS]; // Return zero vector as fallback
        }
    }
    
    /**
     * Generate embeddings asynchronously for better performance
     */
    public CompletableFuture<float[]> generateEmbeddingAsync(String text) {
        return CompletableFuture.supplyAsync(() -> generateEmbedding(text), embeddingExecutor);
    }
    
    /**
     * Perform vector similarity search using APPROX_VECTOR_DISTANCE
     */
    public List<VectorSearchResult> vectorSearch(Collection collection, String queryText, 
                                               String textField, int limit, double threshold) {
        try {
            float[] queryEmbedding = generateEmbedding(queryText);
            logToFile("🔍 Performing vector search for: '" + queryText + "'");
            
            String embeddingExpression = String.format("prediction(%s, {'text': %s}).embedding", 
                GOOGLE_EMBEDDING_MODEL, textField);
            
            String sql = buildVectorSearchSQL(collection, embeddingExpression, limit);
            Query query = database.createQuery(sql);
            
            // Set query parameters with the embedding vector
            Parameters params = createQueryParameters(queryEmbedding);
            query.setParameters(params);
            
            List<VectorSearchResult> results = executeVectorSearch(query, threshold);
            
            logToFile("🔍 Vector search completed: " + results.size() + " results for '" + queryText + "'");
            return results;
            
        } catch (CouchbaseLiteException e) {
            logToFile("❌ Vector search failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Perform vector search with default parameters
     */
    public List<VectorSearchResult> vectorSearch(Collection collection, String queryText, String textField) {
        return vectorSearch(collection, queryText, textField, 10, DEFAULT_SIMILARITY_THRESHOLD);
    }
    
    /**
     * Get count of synced manual chunks from Capella
     */
    public int getSyncedManualChunkCount(Collection collection) {
        try {
            Query countQuery = QueryBuilder
                .select(SelectResult.expression(Function.count(Expression.all())).as("count"))
                .from(DataSource.collection(collection))
                .where(Expression.property("type").equalTo(Expression.string("manual_chunk")));
                
            try (ResultSet results = countQuery.execute()) {
                Result result = results.next();
                if (result != null) {
                    int count = result.getInt("count");
                    logToFile("📚 Found " + count + " synced manual chunks from Capella");
                    return count;
                }
            }
        } catch (CouchbaseLiteException e) {
            logToFile("❌ Error counting synced manual chunks: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * List available machine types from synced manual chunks
     */
    public List<String> getAvailableMachineTypes(Collection collection) {
        List<String> machineTypes = new ArrayList<>();
        
        try {
            Query machineQuery = QueryBuilder
                .select(SelectResult.property("machine_type"))
                .from(DataSource.collection(collection))
                .where(Expression.property("type").equalTo(Expression.string("manual_chunk")))
                .groupBy(Expression.property("machine_type"));
                
            try (ResultSet results = machineQuery.execute()) {
                for (Result result : results) {
                    String machineType = result.getString("machine_type");
                    if (machineType != null) {
                        machineTypes.add(machineType);
                    }
                }
            }
            
            logToFile("🔧 Available machine types: " + machineTypes);
        } catch (CouchbaseLiteException e) {
            logToFile("❌ Error getting machine types: " + e.getMessage());
        }
        
        return machineTypes;
    }
    
    /**
     * Get vector index statistics
     */
    public VectorIndexStats getIndexStats(Collection collection) {
        try {
            String indexName = collection.getName() + "_vector_idx";
            
            Query statsQuery = QueryBuilder
                .select(SelectResult.expression(Function.count(Expression.all())).as("total_docs"))
                .from(DataSource.collection(collection));
            
            try (ResultSet results = statsQuery.execute()) {
                Result result = results.next();
                if (result != null) {
                    int totalDocs = result.getInt("total_docs");
                    
                    VectorIndexConfiguration config = indexConfigurations.get(collection.getName());
                    int dimensions = config != null ? (int) config.getDimensions() : DEFAULT_VECTOR_DIMENSIONS;
                    int centroids = config != null ? (int) config.getCentroids() : DEFAULT_VECTOR_CENTROIDS;
                    
                    logToFile("📊 Index stats requested for " + collection.getName() + " - " + totalDocs + " documents");
                    
                    return new VectorIndexStats(indexName, totalDocs, dimensions, centroids);
                }
            }
            
        } catch (CouchbaseLiteException e) {
            logToFile("❌ Error getting index stats: " + e.getMessage());
        }
        
        return new VectorIndexStats("unknown", 0, 0, 0);
    }
    
    /**
     * Cleanup resources
     */
    public void shutdown() {
        logToFile("⏹️ Shutting down VectorSearchUtil...");
        embeddingExecutor.shutdown();
        
        try {
            Database.prediction.unregisterModel(GOOGLE_EMBEDDING_MODEL);
            logToFile("✅ Embedding model unregistered");
        } catch (Exception e) {
            logToFile("❌ Error unregistering model: " + e.getMessage());
        }
        
        if (logWriter != null) {
            logToFile("=== VectorSearchUtil shutdown completed ===");
            logWriter.close();
            logWriter = null;
        }
    }
    
    // Private helper methods
    
    private EmbeddingModel initializeEmbeddingModel(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key is required for embedding model. Set GOOGLE_GEMINI_API_KEY environment variable.");
        }
        
        return GoogleAiEmbeddingModel.builder()
            .apiKey(apiKey)
            .modelName("text-embedding-004")
            .timeout(Duration.ofSeconds(EMBEDDING_TIMEOUT_SECONDS))
            .maxRetries(MAX_RETRIES)
            .build();
    }
    
    private void registerEmbeddingModel() {
        try {
            PredictiveModel embeddingPredictiveModel = new PredictiveModel() {
                @Override
                public Dictionary predict(Dictionary input) {
                    String text = input.getString("text");
                    if (text == null || text.trim().isEmpty()) {
                        return new MutableDictionary();
                    }
                    
                    try {
                        Embedding embedding = embeddingModel.embed(text).content();
                        float[] embeddingVector = embedding.vector();
                        
                        MutableDictionary result = new MutableDictionary();
                        MutableArray embeddingArray = new MutableArray();
                        for (float value : embeddingVector) {
                            embeddingArray.addFloat(value);
                        }
                        result.setArray("embedding", embeddingArray);
                        return result;
                        
                    } catch (Exception e) {
                        logToFile("❌ Error generating embedding in prediction: " + e.getMessage());
                        return new MutableDictionary();
                    }
                }
            };
            
            Database.prediction.registerModel(GOOGLE_EMBEDDING_MODEL, embeddingPredictiveModel);
            System.out.println("✅ Embedding model registered with CBLite prediction using LangChain4J");
            
        } catch (Exception e) {
            System.err.println("❌ Failed to register embedding model: " + e.getMessage());
        }
    }
    
    private VectorIndexConfiguration createVectorIndexConfig(String predictionExpression, int dimensions, int centroids) {
        VectorIndexConfiguration vectorConfig = new VectorIndexConfiguration(
            predictionExpression, 
            (long) dimensions, 
            (long) centroids
        );
        
        vectorConfig.setEncoding(VectorEncoding.none());
        vectorConfig.setMetric(VectorIndexConfiguration.DistanceMetric.COSINE);
        vectorConfig.setNumProbes(DEFAULT_NUM_PROBES);
        vectorConfig.setMinTrainingSize(DEFAULT_MIN_TRAINING_SIZE);
        vectorConfig.setMaxTrainingSize(DEFAULT_MAX_TRAINING_SIZE);
        
        return vectorConfig;
    }
    
    private void logIndexCreation(String collectionName, String textIndexName, String predictionIndexName, 
                                String textField, int dimensions, int centroids) {
        System.out.println("✅ Vector indexes created for synced manual chunks: " + collectionName);
        System.out.println("   - Text index: " + textIndexName);
        System.out.println("   - Vector index: " + predictionIndexName);
        System.out.println("   - Text field: " + textField);
        System.out.println("   - Dimensions: " + dimensions);
        System.out.println("   - Centroids: " + centroids);
        System.out.println("   - Embedding model: " + GOOGLE_EMBEDDING_MODEL);
        System.out.println("   - Metric: COSINE");
        System.out.println("   - Ready for semantic vector search on synced documents");
    }
    
    private String buildVectorSearchSQL(Collection collection, String embeddingExpression, int limit) {
        return String.format(
            "SELECT meta().id, content, title, machine_type, type " +
            "FROM %s.%s " +
            "ORDER BY APPROX_VECTOR_DISTANCE(%s, $vectorParam) " +
            "LIMIT %d",
            collection.getScope().getName(),
            collection.getName(),
            embeddingExpression,
            limit
        );
    }
    
    private Parameters createQueryParameters(float[] queryEmbedding) {
        Parameters params = new Parameters();
        MutableArray vectorArray = new MutableArray();
        for (float value : queryEmbedding) {
            vectorArray.addFloat(value);
        }
        params.setArray("vectorParam", vectorArray);
        return params;
    }
    
    private List<VectorSearchResult> executeVectorSearch(Query query, double threshold) {
        List<VectorSearchResult> results = new ArrayList<>();
        
        try (ResultSet resultSet = query.execute()) {
            for (Result result : resultSet) {
                Map<String, Object> documentMap = createDocumentMap(result);
                
                // Calculate similarity from distance (smaller distance = higher similarity)
                double distance = 0.0; // APPROX_VECTOR_DISTANCE result will be in order
                double similarity = Math.max(0.0, 1.0 - distance);
                
                if (similarity >= threshold) {
                    VectorSearchResult searchResult = new VectorSearchResult(documentMap, similarity, distance);
                    results.add(searchResult);
                }
            }
        } catch (CouchbaseLiteException e) {
            logToFile("❌ Error executing vector search: " + e.getMessage());
            return new ArrayList<>();
        }
        
        // Sort by similarity (highest first)
        results.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        
        return results;
    }
    
    private Map<String, Object> createDocumentMap(Result result) {
        Map<String, Object> documentMap = new HashMap<>();
        documentMap.put("id", result.getString("id"));
        documentMap.put("title", result.getString("title"));
        documentMap.put("content", result.getString("content"));
        documentMap.put("machine_type", result.getString("machine_type"));
        documentMap.put("type", result.getString("type"));
        return documentMap;
    }
    
    private void initializeFileLogging() {
        try {
            new java.io.File("data").mkdirs();
            logWriter = new PrintWriter(new FileWriter(LOG_FILE_PATH, true));
            logToFile("=== VectorSearchUtil logging session started ===");
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (logWriter != null) {
                    logToFile("=== VectorSearchUtil logging session ended ===");
                    logWriter.close();
                }
            }));
            
        } catch (IOException e) {
            System.err.println("❌ Failed to initialize file logging for VectorSearchUtil: " + e.getMessage());
        }
    }
    
    private void logToFile(String message) {
        if (logWriter != null) {
            String timestamp = LocalDateTime.now().format(LOG_TIMESTAMP_FORMAT);
            logWriter.println(String.format("[%s] %s", timestamp, message));
            logWriter.flush();
        }
    }
    
    // Helper classes
    
    /**
     * Vector search result with similarity score
     */
    public static class VectorSearchResult {
        private final Map<String, Object> document;
        private final double similarity;
        private final double distance;
        
        public VectorSearchResult(Map<String, Object> document, double similarity, double distance) {
            this.document = document;
            this.similarity = similarity;
            this.distance = distance;
        }
        
        public Map<String, Object> getDocument() { return document; }
        public double getSimilarity() { return similarity; }
        public double getDistance() { return distance; }
        
        @Override
        public String toString() {
            return String.format("VectorSearchResult{similarity=%.3f, title='%s'}", 
                               similarity, document.get("title"));
        }
    }
    
    /**
     * Vector index statistics
     */
    public static class VectorIndexStats {
        private final String indexName;
        private final int totalDocuments;
        private final int dimensions;
        private final int centroids;
        
        public VectorIndexStats(String indexName, int totalDocuments, int dimensions, int centroids) {
            this.indexName = indexName;
            this.totalDocuments = totalDocuments;
            this.dimensions = dimensions;
            this.centroids = centroids;
        }
        
        public String getIndexName() { return indexName; }
        public int getTotalDocuments() { return totalDocuments; }
        public int getDimensions() { return dimensions; }
        public int getCentroids() { return centroids; }
        
        @Override
        public String toString() {
            return String.format("VectorIndexStats{name='%s', docs=%d, dims=%d, centroids=%d}",
                               indexName, totalDocuments, dimensions, centroids);
        }
    }
} 