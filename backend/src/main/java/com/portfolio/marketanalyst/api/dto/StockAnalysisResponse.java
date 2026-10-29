package com.portfolio.marketanalyst.api.dto;

import java.time.Instant;
import java.util.List;

public record StockAnalysisResponse(
        String symbol,
        String interval,
        String mode,
        String marketDataProvider,
        String aiModel,
        String overallStatus,
        int cachedCount,
        int pendingCount,
        int activeInFlightJobs,
        Instant requestedAt,
        List<CandleAnalysisDto> candles
) {}
