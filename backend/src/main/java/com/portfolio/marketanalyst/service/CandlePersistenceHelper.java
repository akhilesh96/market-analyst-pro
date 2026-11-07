package com.portfolio.marketanalyst.service;

import com.portfolio.marketanalyst.domain.AiGenerationResult;
import com.portfolio.marketanalyst.domain.AnalysisStatus;
import com.portfolio.marketanalyst.domain.CandleAnalysisEntity;
import com.portfolio.marketanalyst.domain.CandleData;
import com.portfolio.marketanalyst.repository.CandleAnalysisRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles atomic per-candle state transitions in isolated transactions so that
 * concurrent threads immediately see PENDING, COMPLETED, or FAILED rows in PostgreSQL.
 */
@Service
public class CandlePersistenceHelper {

    private final CandleAnalysisRepository repository;

    public CandlePersistenceHelper(CandleAnalysisRepository repository) {
        this.repository = repository;
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CandleAnalysisEntity getOrCreatePendingRecord(CandleData candle, boolean retryFailed, String activeMode) {
        String symbol = candle.symbol().toUpperCase();
        String interval = candle.interval().toLowerCase();
        Instant candleTimestamp = candle.timestamp();

        // 1. Insert if missing (database ignores duplicates safely)
        repository.insertPendingIfAbsent(
                symbol,
                interval,
                candleTimestamp,
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume()
        );

        // 2. Fetch the row (it is guaranteed to exist now)
        CandleAnalysisEntity entity = repository.findBySymbolAndIntervalAndCandleTimestamp(symbol, interval, candleTimestamp)
                .orElseThrow(() -> new IllegalStateException("Failed to load candle for " + symbol));

        // 3. Retry if previously failed or if switching from mock mode
        boolean isStaleMock = "real".equalsIgnoreCase(activeMode)
                && entity.getModelUsed() != null
                && entity.getModelUsed().startsWith("mock-");

        if (isStaleMock || (entity.getStatus() == AnalysisStatus.FAILED && retryFailed)) {
            entity.refreshCandleAndResetPending(candle);
            return repository.saveAndFlush(entity);
        }

        return entity;
    }



    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(CandleData candle, AiGenerationResult result) {
        repository.findBySymbolAndIntervalAndCandleTimestamp(
                candle.symbol().toUpperCase(),
                candle.interval().toLowerCase(),
                candle.timestamp()
        ).ifPresent(entity -> {
            entity.markCompleted(result);
            repository.saveAndFlush(entity);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(CandleData candle, String errorMessage) {
        repository.findBySymbolAndIntervalAndCandleTimestamp(
                candle.symbol().toUpperCase(),
                candle.interval().toLowerCase(),
                candle.timestamp()
        ).ifPresent(entity -> {
            entity.markFailed(errorMessage);
            repository.saveAndFlush(entity);
        });
    }
}
