package com.javaproject.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper; // Added for JSON parsing
import com.fasterxml.jackson.databind.JsonNode;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class StockAIConsumer {

    private final ChatLanguageModel aiModel;
    private final AnalysisStreamService streamService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Context Map: Ticker -> List of Price Data
    private final Map<String, List<String>> tickerContexts = new ConcurrentHashMap<>();

    // Processing Lock Map: Ticker -> Is currently being analyzed
    private final Map<String, AtomicBoolean> processingLocks = new ConcurrentHashMap<>();

    private static final int FULL_DAY_THRESHOLD = 25;

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    public StockAIConsumer(AnalysisStreamService streamService,
                           @Value("${ollama.base-url}") String baseUrl,
                           @Value("${ollama.model}") String modelName) {
        this.streamService = streamService;
        this.aiModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    @KafkaListener(topics = "stock-topic", groupId = "ai-analyst-group")
    public void consumeAndAnalyze(String message) {
        try {
            // 1. Parse incoming JSON to identify the ticker
            JsonNode node = objectMapper.readTree(message);
            String ticker = node.get("ticker").asText();

            // 2. Get or create the context list for this specific ticker
            List<String> history = tickerContexts.computeIfAbsent(ticker, k -> Collections.synchronizedList(new ArrayList<>()));
            history.add(message);

            // 3. Get or create a lock for this ticker
            AtomicBoolean lock = processingLocks.computeIfAbsent(ticker, k -> new AtomicBoolean(false));

            if (lock.compareAndSet(false, true)) {
                try {
                    int count = history.size();
                    if (count % 5 == 0 || count >= FULL_DAY_THRESHOLD) {
                        boolean isFinal = (count >= FULL_DAY_THRESHOLD);
                        generateTrendAnalysis(ticker, history, isFinal);

                        if (isFinal) {
                            tickerContexts.remove(ticker);
                            processingLocks.remove(ticker);
                        }
                    }
                } finally {
                    lock.set(false);
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing Kafka message: " + e.getMessage());
        }
    }

    private void generateTrendAnalysis(String ticker, List<String> history, boolean isFinal) {
        if (!isOllamaRunning()) {
            streamService.broadcast("⚠️ ERROR: AI Engine unavailable.");
            return;
        }

        String contextData;
        synchronized (history) {
            contextData = String.join("\n", history);
        }

        String systemInstruction = String.format("""
            You are a Professional Analyst for %s.
            Based on the 15-minute data provided, give a:
            - Phase-by-Phase Breakdown
            - Key Technical Levels
            - Final Verdict
            """, ticker);

        String task = isFinal ? "Master Summary of the trading day." : "Trend summary of the last hour.";
        String finalPrompt = String.format("%s\n\nData:\n%s\n\nTask: %s", systemInstruction, contextData, task);

        try {
            String response = aiModel.generate(finalPrompt);
            streamService.broadcast("### Analysis for " + ticker + "\n" + response);
        } catch (Exception e) {
            streamService.broadcast("❌ AI Inference failed for " + ticker);
        }
    }

    private boolean isOllamaRunning() {
        try {
            URL url = new URL(ollamaBaseUrl + "/api/tags");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(2000);
            return (connection.getResponseCode() == 200);
        } catch (Exception e) { return false; }
    }
}