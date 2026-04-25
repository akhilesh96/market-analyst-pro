package com.javaproject.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class StockAIConsumer {

    private final ChatLanguageModel aiModel;
    private final List<String> dailyHistory = new ArrayList<>();
    private static final int FULL_DAY_THRESHOLD = 25;
    @Autowired // This tells Spring to find the AnalysisStreamService bean
    private AnalysisStreamService streamService;
    // NEW: Prevent overlapping AI calls during the Python "burst"
    private boolean isProcessing = false;

    public StockAIConsumer() {
        this.aiModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3.1:8b-instruct-q2_K")
                .timeout(Duration.ofSeconds(120)) // Increased timeout for big batches
                .build();
    }

    @KafkaListener(topics = "stock-topic", groupId = "ai-analyst-group")
    public void consumeAndAnalyze(String message) {
        // 1. Add the message first so the count grows correctly
        dailyHistory.add(message);
        int count = dailyHistory.size();

        if (!isProcessing) {
            // 2. Trigger the analysis
            if (count % 5 == 0 || count >= FULL_DAY_THRESHOLD) {
                boolean isFinal = (count >= FULL_DAY_THRESHOLD);
                generateTrendAnalysis(isFinal);

                // 3. Clear ONLY after the Final Master Summary is generated
                if (isFinal) {
                    System.out.println("Final summary pushed. Clearing context for next ticker.");
                    dailyHistory.clear();
                }
            }
        }
    }
    private boolean isOllamaRunning() {
        try {
            // We use a simple 2-second timeout to check the connection
            URL url = new URL("http://localhost:11434/api/tags");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.connect();
            return (connection.getResponseCode() == 200);
        } catch (Exception e) {
            return false;
        }
    }
    private void generateTrendAnalysis(boolean isFinal) {

        if (!isOllamaRunning()) {
            streamService.broadcast("⚠️ ERROR: Local AI Server (Ollama) is stopped. Please start it to see the analysis.");
            return;
        }
        isProcessing = true;
        try {

            String context = String.join("\n", dailyHistory);

            // Updated System Prompt for professional formatting
            String systemInstruction = """
            You are a Professional Stock Market Analyst. 
            Analyze the provided 15-minute candle data and format your response EXACTLY like this:
            
            ### 🕒 Phase-by-Phase Breakdown
            - **[Time Range]**: [Brief Trend Description - e.g., Bullish Recovery]
            - **[Time Range]**: [Brief Trend Description - e.g., High Volume Sell-off]
            
            ### 📊 Key Technical Levels
            - **Day High**: ₹[Price] at [Time]
            - **Day Low**: ₹[Price] at [Time]
            
            ### 💡 Final Verdict
            [Provide a 1-sentence actionable insight: e.g., 'Stay Cautious for tomorrow's opening.']
            """;

            String task = isFinal ? "Provide a final Master Summary of the whole day using the Phase Breakdown for every 15-min candle."
                    : "Summarize the trend for the last hour.";

            String finalPrompt = String.format("%s\n\nData:\n%s\n\nTask: %s", systemInstruction, context, task);

            String response = aiModel.generate(finalPrompt);

            // 2. Push to UI immediately (No DB needed)
            streamService.broadcast(response);

            System.out.println("Broadcasted AI Insight to UI");
        } finally {
            isProcessing = false;
        }
    }
}
