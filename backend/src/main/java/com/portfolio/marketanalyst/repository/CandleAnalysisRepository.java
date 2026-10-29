package com.portfolio.marketanalyst.repository;

import com.portfolio.marketanalyst.domain.CandleAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CandleAnalysisRepository extends JpaRepository<CandleAnalysisEntity, Long> {

    Optional<CandleAnalysisEntity> findBySymbolAndIntervalAndCandleTimestamp(
            String symbol,
            String interval,
            Instant candleTimestamp
    );

    List<CandleAnalysisEntity> findBySymbolAndIntervalAndCandleTimestampIn(
            String symbol,
            String interval,
            Collection<Instant> timestamps
    );
}
