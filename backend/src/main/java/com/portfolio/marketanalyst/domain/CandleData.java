package com.portfolio.marketanalyst.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain model representing a single completed OHLCV market candle.
 */
public record CandleData(
        String symbol,
        String interval,
        Instant timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {
    public CandleKey toKey() {
        return new CandleKey(symbol, interval, timestamp);
    }
}
