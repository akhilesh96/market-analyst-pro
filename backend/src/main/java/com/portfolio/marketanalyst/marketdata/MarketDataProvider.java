package com.portfolio.marketanalyst.marketdata;

import com.portfolio.marketanalyst.domain.CandleData;
import java.util.List;

public interface MarketDataProvider {

    /**
     * Fetches the latest N completed 15-minute candles for the given stock ticker.
     */
    List<CandleData> fetchLatestCompletedCandles(String symbol, String interval, int limit);

    String providerName();
}
