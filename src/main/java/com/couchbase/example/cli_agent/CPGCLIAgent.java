package com.couchbase.example.cli_agent;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import com.couchbase.lite.*;
import java.time.Duration;
import java.util.Scanner;

/**
 * CPG Troubleshooting CLI Agent using LangChain4j AI Services
 * 
 * This agent:
 * 1. Takes user input via command line for CPG production troubleshooting
 * 2. Uses AI to decide which tools to call for beverage production issues
 * 3. Returns operational and manual data from the appropriate tools
 * 4. Provides a clean, focused CLI experience for CPG troubleshooting
 */
public class CPGCLIAgent {
    
    /**
     * AI Service interface for the CPG CLI agent
     */
    public interface CLIAgent {
        @SystemMessage("""
            You are a CPG (Consumer Packaged Goods) production troubleshooting assistant specializing in beverage filling operations. 
            Based on the user's input, call the appropriate tools to get the requested data and return it in a clear, 
            structured format.
            
            Available tools:
            
            - Edge Server Tools: For real-time CPG operational data (telemetry, alerts, device status, quality metrics, production data, machine information)
            - Manual Search Tools: For searching CPG documentation, troubleshooting guides, and solutions
            
            CPG Production Context:
            - Focus on beverage filling lines (carbonated beverages, bottling, packaging)
            - Key metrics: fill level, CO2 pressure, temperature, line speed, package weight, quality grades
            - Common issues: fill accuracy, carbonation problems, temperature control, label alignment, weight variations
            - Machine types: beverage_filling_line, packaging_line, quality_control_station
            
            Guidelines:
            - Analyze the user input to determine what CPG data they need
            - Call the most relevant tools to gather operational and documentation data
            - Present the information in a clear, actionable format for production operators
            - If multiple tools are needed, use them to provide comprehensive troubleshooting information
            - Error Code Lookup: When an error code is returned by a tool and no fix message is available, search for the error code in both the manual and past solutions. Present the details of the error code in separate sections: one for manual documentation and one for solution history.
            - Focus on immediate actionable insights for CPG production issues
            """)
        String processRequest(@UserMessage String userInput);
    }
    
    private final CLIAgent agent;
    private final EdgeServerTools edgeServerTools;
    private final ManualSearchTools manualSearchTools;
    private final Database database;
    
    public CPGCLIAgent(String googleGeminiApiKey) throws CouchbaseLiteException {
        // Initialize CBLite database
        CouchbaseLite.init();
        try { 
            CouchbaseLite.enableVectorSearch(); 
            System.out.println("✅ Vector Search enabled");
        }
        catch (CouchbaseLiteException e) {
            throw new IllegalStateException("Could not enable vector search", e);
        }
        DatabaseConfiguration config = new DatabaseConfiguration();
        config.setDirectory("./data/cpg_cli_cblite");
        this.database = new Database("cpg_manufacturing", config);
        
        // Initialize tools
        this.edgeServerTools = new EdgeServerTools();
        this.manualSearchTools = new ManualSearchTools(database);
        
        // Configure AI model with edge device optimizations
        GoogleAiGeminiChatModel chatModel = GoogleAiGeminiChatModel.builder()
            .apiKey(googleGeminiApiKey)
            .modelName("gemini-2.5-pro")
            .temperature(0.1)  // Very low temperature for consistent tool usage
            .timeout(Duration.ofSeconds(120))  // Reduced timeout for edge devices
            .maxRetries(2)  // Add retry logic for transient failures
            .build();
        
        // Create AI service with CPG tools
        this.agent = AiServices.builder(CLIAgent.class)
            .chatModel(chatModel)
            .tools(edgeServerTools, manualSearchTools)
            .build();
        
        System.out.println("🤖 CPG Troubleshooting CLI Agent initialized");
        System.out.println("✅ Edge Server Tools ready for operational data");
        System.out.println("✅ Manual Search Tools ready for documentation");
        System.out.println("✅ AI Service connected");
    }
    
    /**
     * Process a single user request with robust error handling
     */
    public String processRequest(String userInput) {
        try {
            System.out.println("🔄 Processing CPG request: " + userInput);
            String response = agent.processRequest(userInput);
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Error processing CPG request: " + e.getMessage();
            
        }
    }
    
    /**
     * Start interactive CLI mode for CPG troubleshooting
     */
    public void startInteractiveCLI() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🍾 CPG Production Troubleshooting CLI Agent");
        System.out.println("=".repeat(60));
        System.out.println("Enter your CPG production requests and the AI will call appropriate tools.");
        System.out.println("Type 'help' for examples, 'quit' to exit.\n");
        
        while (true) {
            System.out.print("🍾 CPG Agent> ");
            String input = scanner.nextLine().trim();
            
            if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                System.out.println("👋 Goodbye from CPG Agent!");
                break;
            }
            
            if (input.equalsIgnoreCase("help")) {
                showCPGHelp();
                continue;
            }
            
            if (input.isEmpty()) {
                continue;
            }
            
            // Process the CPG request
            String response = processRequest(input);
            System.out.println("\n📋 CPG Response:");
            System.out.println("-".repeat(50));
            System.out.println(response);
            System.out.println("-".repeat(50));
            System.out.println();
        }
        
        scanner.close();
    }
    
    private void showCPGHelp() {
        System.out.println("""
            
            💡 CPG Production Troubleshooting Examples:
            
            📊 Operational Data (Edge Server):
            • "Get recent telemetry for MACHINE_001"
            • "Show active alerts for beverage filling line"
            • "Check device status for rpi-cpg-line1-01"
            • "Get production metrics for BEVERAGE_LINE_A"
            • "Show alerts for error code FILL_001"
            • "Get quality metrics for MACHINE_001"
            • "Check CO2 pressure readings"
            • "Show fill level sensor data"
            • "Get maintenance alerts"
            • "Production summary for BEVERAGE_LINE_A"
            
            📚 Documentation & Solutions (Manual Search):
            • "Search manuals for CO2 pressure calibration"
            • "Find solutions for fill level problems"
            • "Get troubleshooting procedures for beverage_filling_line"
            • "Show maintenance info for carbonation systems"
            • "Find solutions for error code TEMP_001"
            • "Search for label alignment procedures"
            • "Get quality control documentation"
            • "Find filling accuracy solutions"
            
            🔧 Combined Troubleshooting:
            • "Analyze FILL_001 error on MACHINE_001" (uses both operational data + solutions)
            • "Help with CO2 pressure issues on beverage line" (searches data + manuals)
            • "Troubleshoot temperature problems" (checks alerts + procedures)
            • "Quality issues on BEVERAGE_LINE_A" (gets metrics + solutions)
            • "Maintenance needed for filling equipment" (checks status + procedures)
            
            The AI will automatically decide which tools to use based on your request.
            Focus on specific CPG production issues for best results.
            """);
    }
    
    /**
     * Close resources
     */
    public void close() {
        try {
            if (database != null) {
                database.close();
            }
            System.out.println("🔒 CPG CLI Agent closed");
        } catch (CouchbaseLiteException e) {
            System.err.println("⚠️ Error closing CPG database: " + e.getMessage());
        }
    }
    
    /**
     * Main method - entry point for CPG CLI agent
     */
    public static void main(String[] args) {
        // Check for Google Gemini API key
        String googleGeminiApiKey = System.getenv("GOOGLE_GEMINI_API_KEY");
        if (googleGeminiApiKey == null || googleGeminiApiKey.trim().isEmpty()) {
            System.err.println("❌ Error: GOOGLE_GEMINI_API_KEY environment variable not set");
            System.err.println("💡 Set your API key: export GOOGLE_GEMINI_API_KEY=your_key_here");
            
            // Run mock demo instead
            runCPGMockDemo(args);
            return;
        }
        
        CPGCLIAgent agent = null;
        
        try {
            agent = new CPGCLIAgent(googleGeminiApiKey);
            
            // Check if single command mode
            if (args.length > 0) {
                String command = String.join(" ", args);
                System.out.println("🔧 CPG Single Command Mode");
                System.out.println("Command: " + command);
                System.out.println("-".repeat(50));
                
                String response = agent.processRequest(command);
                System.out.println(response);
            } else {
                // Interactive mode
                agent.startInteractiveCLI();
            }
            
        } catch (Exception e) {
            System.err.println("❌ Failed to initialize CPG CLI agent: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (agent != null) {
                agent.close();
            }
        }
    }
    
    /**
     * Mock demo when API key is not available
     */
    private static void runCPGMockDemo(String[] args) {
        System.out.println("\n🎭 CPG Mock Demo Mode (No Google Gemini API Key)");
        System.out.println("=".repeat(55));
        
        String[] sampleCommands = {
            "Get recent telemetry for MACHINE_001",
            "Show active alerts for beverage filling", 
            "Search manuals for CO2 pressure calibration",
            "Analyze FILL_001 error on MACHINE_001"
        };
        
        String[] mockResponses = {
            getCPGMockTelemetryResponse(),
            getCPGMockAlertsResponse(),
            getCPGMockManualSearchResponse(),
            getCPGMockAnalysisResponse()
        };
        
        // If single command provided, try to match it
        if (args.length > 0) {
            String command = String.join(" ", args);
            System.out.println("Command: " + command);
            System.out.println("-".repeat(30));
            System.out.println(getCPGMockResponseForCommand(command));
            return;
        }
        
        // Show sample interactions
        for (int i = 0; i < sampleCommands.length; i++) {
            System.out.println("\n🍾 CPG Agent> " + sampleCommands[i]);
            System.out.println("\n📋 Response:");
            System.out.println("-".repeat(30));
            System.out.println(mockResponses[i]);
            
            if (i < sampleCommands.length - 1) {
                System.out.println("\n" + "·".repeat(55));
            }
        }
        
        System.out.println("\n💡 To use real CPG AI agent, set GOOGLE_GEMINI_API_KEY environment variable");
    }
    
    private static String getCPGMockTelemetryResponse() {
        return """
            📊 Recent CPG Telemetry for Machine MACHINE_001 (6 entries):
            =======================================================
            🕒 2024-01-20 14:30:15 | Sensor: fill_level | Value: 95.2 % | Status: normal | Product: CB_COLA_355 | Batch: 1023
            🕒 2024-01-20 14:29:45 | Sensor: co2_pressure | Value: 3.2 bar | Status: normal | Product: CB_COLA_355 | Batch: 1023
            🕒 2024-01-20 14:29:15 | Sensor: product_temperature | Value: 4.1 °C | Status: normal | Product: CB_COLA_355 | Batch: 1023
            🕒 2024-01-20 14:28:45 | Sensor: line_speed | Value: 465 bottles_per_minute | Status: normal | Product: CB_COLA_355 | Batch: 1023
            🕒 2024-01-20 14:28:15 | Sensor: package_weight | Value: 356.2 grams | Status: normal | Product: CB_COLA_355 | Batch: 1023
            🕒 2024-01-20 14:27:45 | Sensor: fill_level | Value: 94.8 % | Status: warning | Product: CB_COLA_355 | Batch: 1023
            """;
    }
    
    private static String getCPGMockAlertsResponse() {
        return """
            🚨 Active CPG Alerts (3 total):
            =============================================
            🟡 Fill level approaching minimum threshold | Machine: MACHINE_001
               Error Code: FILL_002 | Severity: medium | Type: alert
               Product: CB_COLA_355 | Batch: 1023
               Time: 2024-01-20 14:25:00
            
            🟠 CO2 pressure out of specification | Machine: MACHINE_001
               Error Code: PRESS_001 | Severity: high | Type: alert
               Product: CB_COLA_355 | Batch: 1023
               Time: 2024-01-20 14:20:00
            
            🟢 Scheduled CPG maintenance required: filling_head_cleaning | Machine: MACHINE_002
               Error Code: MAINT_001 | Severity: low | Type: maintenance_alert
               Time: 2024-01-20 13:45:00
            """;
    }
    
    private static String getCPGMockManualSearchResponse() {
        return """
            📚 Manual Search Results for 'CO2 pressure calibration':
            ==================================================
            1. 📖 CO2 Pressure Management
               🏭 Machine Type: beverage_filling_line
               📝 Version: BEVERAGE_FILLING_LINE_V3.2
               🎯 Relevance: 95.8%
               📄 Content: Optimal CO2 pressure maintenance (3.2 ±0.2 bar): 1) Check gas supply pressure daily. 2) Inspect pressure regulators for wear. 3) Monitor carbonation tank levels...
            
            2. 📖 Carbonation System Troubleshooting
               🏭 Machine Type: beverage_filling_line  
               📝 Version: BEVERAGE_FILLING_LINE_V3.2
               🎯 Relevance: 88.3%
               📄 Content: For carbonation system issues: 1) Verify CO2 supply tank levels. 2) Check pressure regulator calibration. 3) Inspect for leaks in carbonation piping...
            """;
    }
    
    private static String getCPGMockAnalysisResponse() {
        return """
            🔍 Analysis Complete - FILL_001 Error on MACHINE_001
            
            📊 Current CPG Status:
            • Fill level reading: 93.8% (target: 95% ±0.5%)
            • Alert status: ACTIVE since 14:25:00
            • System response: Line speed reduced to 85%
            • Product affected: CB_COLA_355, Batch 1023
            
            📚 Knowledge Base Results:
            • Found fill level calibration procedures in manual v3.2
            • Identified 4 solutions with 92%+ success rate for FILL_001
            • Retrieved filling system maintenance protocols
            
            🎯 Recommended CPG Actions:
            1. IMMEDIATE: Check fill valve calibration and adjust if necessary
            2. Verify product supply pressure and flow rate consistency
            3. Inspect filling heads for blockages or wear
            4. Follow manual section 3.1 - Fill Volume Accuracy procedures
            
            ⚠️ CPG Safety: Monitor batch quality - may need to quarantine affected products
            🕒 Expected resolution time: 20-35 minutes
            📊 Impact: Estimated 15% production loss during troubleshooting
            """;
    }
    
    private static String getCPGMockResponseForCommand(String command) {
        String lowerCommand = command.toLowerCase();
        
        if (lowerCommand.contains("telemetry")) {
            return getCPGMockTelemetryResponse();
        } else if (lowerCommand.contains("alert")) {
            return getCPGMockAlertsResponse();
        } else if (lowerCommand.contains("manual") || lowerCommand.contains("search") || lowerCommand.contains("co2") || lowerCommand.contains("pressure")) {
            return getCPGMockManualSearchResponse();
        } else if (lowerCommand.contains("analyze") || lowerCommand.contains("error") || lowerCommand.contains("fill")) {
            return getCPGMockAnalysisResponse();
        } else {
            return "📝 CPG Mock response for: " + command + "\n\n" +
                   "This would normally use AI to determine the appropriate CPG tools and return real beverage production data.\n" +
                   "Set GOOGLE_GEMINI_API_KEY to use the real CPG AI agent.";
        }
    }
} 