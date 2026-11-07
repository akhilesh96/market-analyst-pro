package com.portfolio.marketanalyst.repository;

import com.portfolio.marketanalyst.domain.CandleAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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

    @Modifying
    @Query(value = """
        INSERT INTO candle_analyses 
            (symbol, interval, candle_timestamp, open_price, high_price, low_price, close_price, volume, status, created_at, updated_at)
        VALUES 
            (:symbol, :interval, :candleTimestamp, :openPrice, :highPrice, :lowPrice, :closePrice, :volume, 'PENDING', NOW(), NOW())
        ON CONFLICT (symbol, interval, candle_timestamp) DO NOTHING
    """, nativeQuery = true)
    int insertPendingIfAbsent(
            @Param("symbol") String symbol,
            @Param("interval") String interval,
            @Param("candleTimestamp") Instant candleTimestamp,
            @Param("openPrice") BigDecimal openPrice,
            @Param("highPrice") BigDecimal highPrice,
            @Param("lowPrice") BigDecimal lowPrice,
            @Param("closePrice") BigDecimal closePrice,
            @Param("volume") Long volume
    );
}
