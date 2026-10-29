package com.portfolio.marketanalyst.ai;

import com.portfolio.marketanalyst.config.AppProperties;
import com.portfolio.marketanalyst.domain.AiGenerationResult;
import com.portfolio.marketanalyst.domain.CandleData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Milestone 1 AI Provider: Simulates realistic LLM latency so asynchronous non-blocking
 * responses, PostgreSQL caching, and concurrent request deduplication can be observed and tested.
 */
@Component
@ConditionalOnProperty(name = "app.provider.mode", havingValue = "mock", matchIfMissing = true)
public class FakeAiSummaryProvider implements AiSummaryProvider {

    private static final Logger log = LoggerFactory.getLogger(FakeAiSummaryProvider.class);
    private final AppProperties properties;

    public FakeAiSummaryProvider(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public AiGenerationResult generateSummary(CandleData candle) {
        long latency = properties.ai().mockLatencyMs();
        if (latency > 0) {
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted during mock AI summary generation", e);
            }
        }

        BigDecimal delta = candle.close().subtract(candle.open());
        BigDecimal pctChange = delta
                .divide(candle.open(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal range = candle.high().subtract(candle.low()).setScale(2, RoundingMode.HALF_UP);

        String sentiment;
        String actionPhrase;
        if (pctChange.compareTo(BigDecimal.valueOf(0.20)) > 0) {
            sentiment = "BULLISH";
            actionPhrase = "institutional buying pressure absorbed intraday dips near support";
        } else if (pctChange.compareTo(BigDecimal.valueOf(-0.20)) < 0) {
            sentiment = "BEARISH";
            actionPhrase = "supply overhead capped upper wick expansion and triggered mild profit-taking";
        } else {
            sentiment = "NEUTRAL";
            actionPhrase = "balanced two-way order flow kept price tightly range-bound";
        }

        String summary = String.format(
                "%s closed at %s (%s%%) across a %s-point intraday spread (H: %s, L: %s) on %,d volume; %s.",
                candle.symbol(),
                candle.close().setScale(2, RoundingMode.HALF_UP),
                (pctChange.signum() >= 0 ? "+" : "") + pctChange,
                range,
                candle.high().setScale(2, RoundingMode.HALF_UP),
                candle.low().setScale(2, RoundingMode.HALF_UP),
                candle.volume(),
                actionPhrase
        );

        log.info("[MOCK AI] Generated {} summary for {} @ {}", sentiment, candle.symbol(), candle.timestamp());
        return new AiGenerationResult(summary, sentiment, modelIdentifier());
    }

    @Override
    public String modelIdentifier() {
        return "mock-ollama-qwen2.5:3b";
    }
}
