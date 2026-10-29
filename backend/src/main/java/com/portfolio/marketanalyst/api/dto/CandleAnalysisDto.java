package com.portfolio.marketanalyst.api.dto;

import com.portfolio.marketanalyst.domain.AnalysisStatus;
import com.portfolio.marketanalyst.domain.CandleAnalysisEntity;

import java.math.BigDecimal;
import java.time.Instant;

public record CandleAnalysisDto(
        Long id,
        String symbol,
        String interval,
        Instant candleTimestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume,
        AnalysisStatus status,
        boolean inFlight,
        String aiSummary,
        String sentiment,
        String modelUsed,
        String errorMessage,
        Instant updatedAt
) {
    public static CandleAnalysisDto fromEntity(CandleAnalysisEntity entity, boolean inFlight) {
        return new CandleAnalysisDto(
                entity.getId(),
                entity.getSymbol(),
                entity.getInterval(),
                entity.getCandleTimestamp(),
                entity.getOpenPrice(),
                entity.getHighPrice(),
                entity.getLowPrice(),
                entity.getClosePrice(),
                entity.getVolume(),
                entity.getStatus(),
                inFlight,
                entity.getAiSummary(),
                entity.getSentiment(),
                entity.getModelUsed(),
                entity.getErrorMessage(),
                entity.getUpdatedAt()
        );
    }
}
