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
    public CandleAnalysisEntity getOrCreatePendingRecord(CandleData candle, boolean retryFailed) {
        return repository.findBySymbolAndIntervalAndCandleTimestamp(
                candle.symbol().toUpperCase(),
                candle.interval().toLowerCase(),
                candle.timestamp()
        ).map(existing -> {
            if (existing.getStatus() == AnalysisStatus.FAILED && retryFailed) {
                existing.resetToPending();
                return repository.saveAndFlush(existing);
            }
            return existing;
        }).orElseGet(() -> {
            try {
                return repository.saveAndFlush(CandleAnalysisEntity.createPending(candle));
            } catch (DataIntegrityViolationException ex) {
                // Another concurrent thread inserted the same (symbol, interval, timestamp) first
                return repository.findBySymbolAndIntervalAndCandleTimestamp(
                        candle.symbol().toUpperCase(),
                        candle.interval().toLowerCase(),
                        candle.timestamp()
                ).orElseThrow(() -> ex);
            }
        });
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
