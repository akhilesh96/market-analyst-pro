package com.portfolio.marketanalyst.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "candle_analyses",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_symbol_interval_timestamp",
        columnNames = {"symbol", "interval", "candle_timestamp"}
    )
)
public class CandleAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 16)
    private String interval;

    @Column(name = "candle_timestamp", nullable = false)
    private Instant candleTimestamp;

    @Column(name = "open_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal closePrice;

    @Column(nullable = false)
    private Long volume;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AnalysisStatus status;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(length = 16)
    private String sentiment;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "model_used", length = 64)
    private String modelUsed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CandleAnalysisEntity() {
        // Required by JPA
    }

    public static CandleAnalysisEntity createPending(CandleData candle) {
        CandleAnalysisEntity entity = new CandleAnalysisEntity();
        entity.symbol = candle.symbol().toUpperCase();
        entity.interval = candle.interval().toLowerCase();
        entity.candleTimestamp = candle.timestamp();
        entity.openPrice = candle.open();
        entity.highPrice = candle.high();
        entity.lowPrice = candle.low();
        entity.closePrice = candle.close();
        entity.volume = candle.volume();
        entity.status = AnalysisStatus.PENDING;
        Instant now = Instant.now();
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void markCompleted(AiGenerationResult result) {
        this.status = AnalysisStatus.COMPLETED;
        this.aiSummary = result.summary();
        this.sentiment = result.sentiment();
        this.modelUsed = result.modelUsed();
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = AnalysisStatus.FAILED;
        this.errorMessage = error;
        this.updatedAt = Instant.now();
    }

    public void resetToPending() {
        this.status = AnalysisStatus.PENDING;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public String getInterval() { return interval; }
    public Instant getCandleTimestamp() { return candleTimestamp; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public BigDecimal getHighPrice() { return highPrice; }
    public BigDecimal getLowPrice() { return lowPrice; }
    public BigDecimal getClosePrice() { return closePrice; }
    public Long getVolume() { return volume; }
    public AnalysisStatus getStatus() { return status; }
    public String getAiSummary() { return aiSummary; }
    public String getSentiment() { return sentiment; }
    public String getErrorMessage() { return errorMessage; }
    public String getModelUsed() { return modelUsed; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
