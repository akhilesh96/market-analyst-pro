package com.portfolio.marketanalyst.ai;

import com.portfolio.marketanalyst.config.AppProperties;
import com.portfolio.marketanalyst.domain.AiGenerationResult;
import com.portfolio.marketanalyst.domain.CandleData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Milestone 2 AI Provider: Integrates directly with local Ollama REST API (/api/generate)
 * using Llama 3 or Qwen 2.5 to analyze individual 15-minute OHLCV candles.
 */
@Component
@ConditionalOnProperty(name = "app.provider.mode", havingValue = "real")
public class OllamaAiSummaryProvider implements AiSummaryProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaAiSummaryProvider.class);
    private final RestClient restClient;
    private final AppProperties properties;

    public OllamaAiSummaryProvider(RestClient.Builder builder, AppProperties properties) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.ai().ollamaBaseUrl()).build();
    }
    @Override
    public AiGenerationResult generateSummary(CandleData candle) {
        BigDecimal delta = candle.close().subtract(candle.open());
        BigDecimal pctChange = delta
                .divide(candle.open(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        String sentiment = pctChange.compareTo(BigDecimal.valueOf(0.15)) > 0
                ? "BULLISH"
                : (pctChange.compareTo(BigDecimal.valueOf(-0.15)) < 0 ? "BEARISH" : "NEUTRAL");

        String prompt = String.format(
                "You are a quantitative equity analyst covering the National Stock Exchange of India (NSE). " +
                        "Write a concise 2-sentence institutional commentary for NSE ticker %s on this completed %s candle at %s: " +
                        "Open=%s, High=%s, Low=%s, Close=%s (%s%%), Volume=%d. " +
                        "Refer to the stock strictly as %s (never append .NS and never guess company names) and focus on price action, wick rejection, and volume participation without disclaimers.",
                candle.symbol(),
                candle.interval(),
                candle.timestamp(),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                pctChange,
                candle.volume(),
                candle.symbol()
        );

        Map<String, Object> requestBody = Map.of(
                "model", properties.ai().ollamaModel(),
                "prompt", prompt,
                "stream", false
        );

        log.info("[OLLAMA AI] Calling model {} for {} @ {}", properties.ai().ollamaModel(), candle.symbol(), candle.timestamp());

        OllamaResponse response = restClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(OllamaResponse.class);

        if (response == null || response.response() == null || response.response().isBlank()) {
            throw new IllegalStateException("Empty response received from Ollama model " + properties.ai().ollamaModel());
        }

        return new AiGenerationResult(
                response.response().trim(),
                sentiment,
                properties.ai().ollamaModel()
        );
    }

    @Override
    public String modelIdentifier() {
        return properties.ai().ollamaModel();
    }

    public record OllamaResponse(String model, String response, boolean done) {}
}
