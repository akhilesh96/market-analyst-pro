package com.portfolio.marketanalyst.marketdata;

import com.portfolio.marketanalyst.domain.CandleData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Milestone 1 Provider: Generates deterministic, realistic 15-minute completed candles
 * for Indian & global tickers (e.g., TCS.NS, INFY.NS, RELIANCE.NS) without external network calls.
 */
@Component
@ConditionalOnProperty(name = "app.provider.mode", havingValue = "mock", matchIfMissing = true)
public class FakeMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(FakeMarketDataProvider.class);

    // Fixed anchor aligned to 15-minute boundary so repeat searches hit the same 4 candles
    private static final Instant ANCHOR_TIME = Instant.parse("2026-10-26T09:00:00Z");

    @Override
    public List<CandleData> fetchLatestCompletedCandles(String symbol, String interval, int limit) {
        String cleanSymbol = symbol.trim().toUpperCase();
        log.info("[MOCK MARKET DATA] Fetching {} completed {} candles for {}", limit, interval, cleanSymbol);

        double basePrice = resolveBasePrice(cleanSymbol);
        List<CandleData> candles = new ArrayList<>(limit);

        // Price deltas for the 4 sequential 15-minute candles to demonstrate bullish, pullback, breakout, and consolidation
        double[] pctMoves = {-0.0042, 0.0068, 0.0085, -0.0018};
        long[] baseVolumes = {142_500L, 198_300L, 264_900L, 131_200L};

        double currentOpen = basePrice;
        for (int i = 0; i < limit; i++) {
            Instant candleTime = ANCHOR_TIME.minus((long) (limit - 1 - i) * 15, ChronoUnit.MINUTES);
            double pct = pctMoves[i % pctMoves.length];
            double close = currentOpen * (1.0 + pct);
            double high = Math.max(currentOpen, close) * 1.0028;
            double low = Math.min(currentOpen, close) * 0.9974;
            long volume = baseVolumes[i % baseVolumes.length] + (Math.abs(cleanSymbol.hashCode()) % 15_000);

            candles.add(new CandleData(
                    cleanSymbol,
                    interval,
                    candleTime,
                    bd(currentOpen),
                    bd(high),
                    bd(low),
                    bd(close),
                    volume
            ));
            currentOpen = close;
        }
        return candles;
    }

    @Override
    public String providerName() {
        return "mock-yfinance-simulator";
    }

    private double resolveBasePrice(String symbol) {
        return switch (symbol) {
            case "TCS.NS", "TCS" -> 4125.50;
            case "INFY.NS", "INFY" -> 1865.00;
            case "RELIANCE.NS", "RELIANCE" -> 2940.75;
            case "HDFCBANK.NS", "HDFCBANK" -> 1732.25;
            default -> 1500.00 + (Math.abs(symbol.hashCode()) % 2000);
        };
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}
