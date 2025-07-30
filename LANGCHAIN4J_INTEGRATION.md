# LangChain4j AI Agent for Manufacturing Troubleshooting

This integration combines **LangChain4j AI Services**, **Couchbase Edge Server APIs**, and **CBLite Vector Search** to create an intelligent manufacturing troubleshooting agent. The AI automatically decides which tools to use based on user queries.

## 🏗️ Architecture Overview

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   User Query    │───▶│  LangChain4j     │───▶│  AI Decision    │
│                 │    │  AI Service      │    │  Engine         │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                                ▼
              ┌─────────────────────────────────────┐
              │           Tool Selection            │
              └─────────────────────────────────────┘
                        │                │
                        ▼                ▼
              ┌─────────────────┐ ┌─────────────────┐
              │ EdgeServerTools │ │ManualSearchTools│
              │                 │ │                 │
              │ • Real-time     │ │ • Vector Search │
              │   telemetry     │ │ • Manual RAG    │
              │ • Active alerts │ │ • Solutions DB  │
              │ • Device status │ │ • Procedures    │
              │ • Production    │ │ • Maintenance   │
              │   metrics       │ │   guides        │
              └─────────────────┘ └─────────────────┘
                        │                │
                        ▼                ▼
              ┌─────────────────┐ ┌─────────────────┐
              │  Edge Server    │ │   CBLite DB     │
              │     API         │ │ Vector Search   │
              │ (Port 59840)    │ │   Enabled       │
              └─────────────────┘ └─────────────────┘
```

## 🚀 Quick Start

### 1. Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker and Docker Compose
- OpenAI API key (for real AI agent)

### 2. Setup

```bash
# Set OpenAI API key
export OPENAI_API_KEY=your_openai_api_key_here

# Start Edge Server and populate data
docker-compose up -d

# Compile the project
mvn clean compile

# Run the AI agent demo
mvn exec:java -Dexec.mainClass="com.couchbase.example.LangChain4jDemo"
```

### 3. Run Modes

#### Interactive Mode (Default)
```bash
mvn exec:java -Dexec.mainClass="com.couchbase.example.LangChain4jDemo"
```

#### Automated Demo Mode
```bash
mvn exec:java -Dexec.mainClass="com.couchbase.example.LangChain4jDemo" -Dexec.args="--demo"
```

#### Mock Demo (No API Key Required)
```bash
# Will automatically use mock responses if OPENAI_API_KEY is not set
mvn exec:java -Dexec.mainClass="com.couchbase.example.LangChain4jDemo"
```

## 🤖 AI Agent Capabilities

### Automatic Tool Selection

The AI agent intelligently chooses which tools to use based on the query context:

#### EdgeServerTools
- `getRecentTelemetry()` - Machine sensor data
- `getActiveAlerts()` - Current alarms and alerts
- `getAlertsByErrorCode()` - Historical error patterns
- `getDeviceStatus()` - Device health and connectivity
- `getProductionMetrics()` - Performance data
- `getTelemetryBySensorType()` - Sensor-specific data

#### ManualSearchTools  
- `searchManuals()` - Semantic search through documentation
- `searchSolutions()` - Find proven troubleshooting solutions
- `getTroubleshootingProcedures()` - Step-by-step procedures
- `getMaintenanceInfo()` - Maintenance schedules and best practices
- `getSolutionsByErrorCode()` - Error-specific solutions

### Query Examples

#### Error Code Diagnosis
```
Query: "Machine PACK_01 has error code TEMP_001"

AI Decision:
1. Check active alerts for TEMP_001
2. Get recent telemetry for PACK_01
3. Search solutions by error code
4. Find manual procedures for thermal issues
5. Provide diagnosis and action plan
```

#### Performance Issues
```
Query: "Packaging line running slower than normal with vibration"

AI Decision:
1. Check production metrics for efficiency
2. Search telemetry for vibration sensors
3. Find manual sections on vibration troubleshooting
4. Search solutions for performance degradation
5. Recommend mechanical inspection procedures
```

#### Maintenance Planning
```
Query: "What preventive maintenance is due for packaging_line?"

AI Decision:
1. Search maintenance procedures for packaging_line
2. Check device status for operating hours
3. Find best practices for preventive maintenance
4. Review upcoming maintenance schedules
5. Provide prioritized maintenance plan
```

## 🛠️ Implementation Details

### LangChain4j Integration

```java
// AI Service Interface with System Messages
public interface ManufacturingTroubleshootingAgent {
    
    @SystemMessage("""
        You are an expert manufacturing troubleshooting AI assistant...
        Available Tools:
        - Edge Server Tools: Access real-time telemetry, alerts, device status
        - Manual Search Tools: Search manuals, solutions, procedures using vector search
        """)
    String troubleshoot(@UserMessage String userQuery);
}

// Service Implementation with Tools
TroubleshootingAIService service = AiServices.builder(ManufacturingTroubleshootingAgent.class)
    .chatLanguageModel(chatModel)
    .tools(edgeServerTools, manualSearchTools)  // AI decides which tools to use
    .build();
```

### Tool Implementation Example

```java
@Tool("Get recent telemetry data from manufacturing equipment")
public String getRecentTelemetry(String machineId, int limitResults) {
    // Execute SQL++ query against Edge Server
    String query = String.format(
        "SELECT * FROM manufacturing.telemetry WHERE machine_id = '%s' ORDER BY timestamp DESC LIMIT %d",
        machineId, limitResults
    );
    
    List<Map<String, Object>> results = executeQuery(query);
    // Format results for AI consumption
    return formatTelemetryResponse(results);
}
```

### Vector Search Integration

```java
@Tool("Search through equipment manuals using semantic search")
public String searchManuals(String query, String machineType) {
    // Perform vector similarity search
    List<VectorSearchResult> vectorResults = vectorSearchUtil.vectorSearch(
        manualsCollection, query, "content_embedding", 5, 0.6
    );
    
    // Format results with relevance scores
    return formatManualSearchResults(vectorResults);
}
```

## 📊 Data Flow

### Operational Data (Edge Server → AI)
1. **Real-time queries** via HTTP API to Edge Server
2. **SQL++ queries** against manufacturing collections
3. **Structured responses** formatted for AI consumption

### Knowledge Base (CBLite → AI)
1. **Vector search** through local manual collection
2. **Semantic similarity** matching for relevant content
3. **Solution ranking** based on success rates and relevance

### AI Decision Making
1. **Context analysis** of user query
2. **Tool selection** based on query type and content
3. **Multi-source correlation** of operational and knowledge data
4. **Structured response** with diagnosis and recommendations

## 🎯 Example AI Interactions

### Scenario 1: Critical Alert
```
User: "We have a critical temperature alert on PACK_01"

AI Process:
1. getActiveAlerts() → Find TEMP_001 alert
2. getRecentTelemetry("PACK_01", 10) → Get temperature readings
3. searchSolutions("TEMP_001", "temperature") → Find proven solutions
4. searchManuals("thermal management", "packaging_line") → Get procedures

AI Response:
🚨 Critical Temperature Alert Analysis
Temperature reading: 85°C (normal: 45-65°C)
Root cause: Cooling system malfunction
Immediate actions: Reduce speed to 50%, check air filters
Procedure: Manual section 4.2 - Thermal Management
Expected resolution: 30-60 minutes
```

### Scenario 2: Performance Analysis
```
User: "Analyze performance of LINE_001 this week"

AI Process:
1. getProductionMetrics("LINE_001", 50) → Get recent production data
2. getTelemetryBySensorType("efficiency", 20) → Check efficiency sensors
3. searchManuals("performance optimization", "any") → Find best practices

AI Response:
📈 Performance Analysis - LINE_001
Efficiency: 87% (target: 90%)
Bottleneck: Conveyor system (2.3 sec cycle time)
Recommendations: Belt tension adjustment, sensor calibration
Maintenance needed: Quarterly optimization procedure
```

## 🔧 Configuration

### Environment Variables

```bash
# Required for AI functionality
export OPENAI_API_KEY=your_api_key_here

# Edge Server Configuration (optional)
export EDGE_SERVER_HOST=localhost
export EDGE_SERVER_PORT=59840

# Database Configuration (optional)
export CBLITE_DATABASE_PATH=./data/cblite
export VECTOR_SEARCH_THRESHOLD=0.6
```

### AI Model Configuration

```java
ChatLanguageModel chatModel = OpenAiChatModel.builder()
    .apiKey(openAiApiKey)
    .modelName("gpt-4")                    // Use GPT-4 for better reasoning
    .temperature(0.2)                      // Lower temperature for technical accuracy
    .timeout(Duration.ofSeconds(60))       // Generous timeout for complex queries
    .maxRetries(3)                         // Retry on failures
    .build();
```

## 📚 Dependencies Added

```xml
<!-- LangChain4j AI Services -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.35.0</version>
</dependency>

<!-- OpenAI Integration -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.35.0</version>
</dependency>

<!-- Local Embeddings (for vector search) -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-embeddings-all-minilm-l6-v2</artifactId>
    <version>0.35.0</version>
</dependency>
```

## 🔍 Vector Search Setup

### Manual Collection Schema
```json
{
  "type": "manual_chunk",
  "title": "Temperature Sensor Calibration",
  "content": "To calibrate the temperature sensor...",
  "machine_type": "packaging_line",
  "manual_version": "v2.1",
  "content_embedding": [0.1, 0.2, ...], // 768-dimensional vector
  "metadata": {
    "section": "calibration",
    "page": 45
  }
}
```

### Solution Collection Schema
```json
{
  "type": "solution",
  "title": "Temperature Alert Resolution",
  "error_code": "TEMP_001",
  "success_rate": "90%",
  "average_resolution_time": 30,
  "solution_comment": "Check cooling system and air filters...",
  "solution_embedding": [0.3, 0.4, ...], // 768-dimensional vector
  "machine_type": "packaging_line"
}
```

## 🎮 Interactive Commands

When running in interactive mode:

```
🤖 AI Assistant> help

Available Commands:
• Troubleshooting: "Machine X has error Y" or describe symptoms
• Maintenance: "What maintenance is due for machine type Z?"  
• Performance: "Analyze performance of production line A"
• Health Check: "Check status of device ID"

Example Queries:
• "PACK_01 is overheating with error code TEMP_001"
• "Packaging line making unusual vibration noises"  
• "What preventive maintenance needed for conveyor systems?"
• "Performance analysis for LINE_001 this week"
• "Device rpi-line1-01 connectivity issues"

Special Commands:
• help - Show this help
• quit/exit - Close the application
```

## 🚨 Error Handling

### Graceful Degradation
- **No OpenAI API key**: Falls back to mock responses
- **Edge Server unavailable**: Uses cached data and knowledge base only
- **Vector search failure**: Falls back to text-based search
- **Network issues**: Provides offline troubleshooting guidance

### Fallback Responses
```java
catch (Exception e) {
    return """
        ❌ Error during troubleshooting: Connection failed
        
        🛠️ Fallback recommendations:
        1. Check system logs for error details
        2. Verify network connectivity to edge server  
        3. Review recent maintenance activities
        4. Contact technical support if issue persists
        """;
}
```

## 🎓 Benefits of This Approach

### 1. **Intelligent Tool Selection**
- AI automatically chooses relevant data sources
- No need to manually specify which APIs to call
- Context-aware decision making

### 2. **Multi-Modal RAG**
- Combines real-time operational data
- With historical knowledge base search
- Vector similarity for semantic matching

### 3. **Scalable Architecture**
- Easy to add new tools and data sources
- LangChain4j handles tool orchestration
- CBLite provides offline capabilities

### 4. **Domain-Specific Intelligence**
- System messages tuned for manufacturing
- Technical accuracy with safety considerations
- Step-by-step troubleshooting guidance

## 🔮 Future Enhancements

### 1. **Multi-Agent Workflows**
```java
// Specialist agents for different domains
DiagnosisAgent diagnosisAgent = ...;
MaintenanceAgent maintenanceAgent = ...;
SafetyAgent safetyAgent = ...;

// Orchestrate multiple agents
WorkflowAgent orchestrator = AiServices.builder(WorkflowAgent.class)
    .tools(diagnosisAgent, maintenanceAgent, safetyAgent)
    .build();
```

### 2. **Memory and Learning**
```java
// Add conversation memory
ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(4000);

// Build service with memory
agent = AiServices.builder(ManufacturingTroubleshootingAgent.class)
    .chatLanguageModel(chatModel)
    .chatMemory(chatMemory)
    .tools(edgeServerTools, manualSearchTools)
    .build();
```

### 3. **Advanced RAG**
```java
// Add document retrieval and reranking
DocumentRetriever retriever = EmbeddingStoreDocumentRetriever.builder()
    .embeddingStore(embeddingStore)
    .embeddingModel(embeddingModel)
    .maxResults(10)
    .minScore(0.7)
    .build();

// Content enhancement
DocumentTransformer transformer = DocumentByParagraphSplitter.builder()
    .maxParagraphSizeInTokens(500)
    .maxOverlapSizeInTokens(50)
    .build();
```

This LangChain4j integration provides a powerful foundation for intelligent manufacturing troubleshooting, combining the best of real-time operational data, semantic knowledge search, and AI-powered reasoning. 