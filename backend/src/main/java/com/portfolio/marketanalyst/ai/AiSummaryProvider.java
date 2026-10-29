package com.portfolio.marketanalyst.ai;

import com.portfolio.marketanalyst.domain.AiGenerationResult;
import com.portfolio.marketanalyst.domain.CandleData;

public interface AiSummaryProvider {

    /**
     * Generates a concise technical commentary for a single 15-minute OHLCV candle.
     */
    AiGenerationResult generateSummary(CandleData candle);

    String modelIdentifier();
}
