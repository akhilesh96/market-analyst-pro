package com.portfolio.marketanalyst.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable composite key used for in-flight job deduplication across concurrent requests.
 */
public record CandleKey(String symbol, String interval, Instant candleTimestamp) {

    public CandleKey {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        Objects.requireNonNull(candleTimestamp, "candleTimestamp must not be null");
        symbol = symbol.trim().toUpperCase();
        interval = interval.trim().toLowerCase();
    }
}
