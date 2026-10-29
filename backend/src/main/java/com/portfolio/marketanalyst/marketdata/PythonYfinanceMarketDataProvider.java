package com.portfolio.marketanalyst.marketdata;

import com.portfolio.marketanalyst.config.AppProperties;
import com.portfolio.marketanalyst.domain.CandleData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Milestone 2 Provider: Calls the Python FastAPI + yfinance microservice to fetch
 * the latest 4 completed 15-minute candles.
 */
@Component
@ConditionalOnProperty(name = "app.provider.mode", havingValue = "real")
public class PythonYfinanceMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(PythonYfinanceMarketDataProvider.class);
    private final RestClient restClient;

    public PythonYfinanceMarketDataProvider(RestClient.Builder builder, AppProperties properties) {
        this.restClient = builder.baseUrl(properties.marketData().baseUrl()).build();
    }

    @Override
    public List<CandleData> fetchLatestCompletedCandles(String symbol, String interval, int limit) {
        String cleanSymbol = symbol.trim().toUpperCase().replaceAll("\\.NS$", "");
        // Always send .NS to the Python yfinance bridge so any Indian NSE stock works
        // even without rebuilding the Python Docker container
        String yfSymbol = cleanSymbol + ".NS";
        log.info("[PYTHON YFINANCE] Requesting {} completed {} candles for {} (yfinance ticker: {})",
                limit, interval, cleanSymbol, yfSymbol);

        PythonCandleResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/candles/{symbol}")
                        .queryParam("interval", interval)
                        .queryParam("limit", limit)
                        .build(yfSymbol))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || response.candles() == null || response.candles().isEmpty()) {
            throw new IllegalStateException("No completed candles returned for symbol: " + cleanSymbol);
        }

        return response.candles().stream()
                .map(c -> new CandleData(
                        cleanSymbol, // Store clean ticker (e.g. SBIN, TCS) without .NS
                        interval,
                        Instant.parse(c.timestamp()),
                        c.open(),
                        c.high(),
                        c.low(),
                        c.close(),
                        c.volume()
                ))
                .toList();
    }

    @Override
    public String providerName() {
        return "python-yfinance-service";
    }

    public record PythonCandleResponse(
            String symbol,
            String interval,
            List<PythonCandleItem> candles
    ) {}

    public record PythonCandleItem(
            String timestamp,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume
    ) {}
}
