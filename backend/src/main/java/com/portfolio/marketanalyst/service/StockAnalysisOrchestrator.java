package com.portfolio.marketanalyst.service;

import com.portfolio.marketanalyst.ai.AiSummaryProvider;
import com.portfolio.marketanalyst.api.dto.CandleAnalysisDto;
import com.portfolio.marketanalyst.api.dto.StockAnalysisResponse;
import com.portfolio.marketanalyst.config.AppProperties;
import com.portfolio.marketanalyst.domain.*;
import com.portfolio.marketanalyst.marketdata.MarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class StockAnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(StockAnalysisOrchestrator.class);
    private static final Duration OHLCV_CACHE_TTL = Duration.ofSeconds(30);
    private static final int MAX_CONCURRENT_AI_CALLS = 2;

    private final MarketDataProvider marketDataProvider;
    private final AiSummaryProvider aiSummaryProvider;
    private final CandlePersistenceHelper persistenceHelper;
    private final ExecutorService aiAnalysisExecutor;
    private final AppProperties properties;

    /**
     * Singleflight registry: coalesces concurrent requests for the same (symbol, interval, timestamp)
     * into a single async job.
     */
    private final ConcurrentMap<CandleKey, CompletableFuture<Void>> inFlightJobs = new ConcurrentHashMap<>();

    /**
     * L1 short-lived OHLCV cache (30s TTL): prevents 1.2s UI polling or 100 req/s traffic spikes
     * from hammering the Python yfinance service and triggering Yahoo Finance HTTP 429 rate limits.
     */
    private final ConcurrentMap<String, CachedCandles> ohlcvCache = new ConcurrentHashMap<>();

    /**
     * Backpressure bulkhead: caps concurrent Ollama LLM inference calls so local CPU/GPU is never
     * overwhelmed by burst searches across multiple uncached tickers.
     */
    private final Semaphore aiConcurrencyLimiter = new Semaphore(MAX_CONCURRENT_AI_CALLS);

    private final AtomicInteger totalAiJobsTriggered = new AtomicInteger(0);

    private record CachedCandles(List<CandleData> candles, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public StockAnalysisOrchestrator(
            MarketDataProvider marketDataProvider,
            AiSummaryProvider aiSummaryProvider,
            CandlePersistenceHelper persistenceHelper,
            ExecutorService aiAnalysisExecutor,
            AppProperties properties
    ) {
        this.marketDataProvider = marketDataProvider;
        this.aiSummaryProvider = aiSummaryProvider;
        this.persistenceHelper = persistenceHelper;
        this.aiAnalysisExecutor = aiAnalysisExecutor;
        this.properties = properties;
    }

    public StockAnalysisResponse analyzeSymbol(String rawSymbol, boolean retryFailed) {
        String symbol = normalizeSymbol(rawSymbol);
        String interval = properties.marketData().interval();
        int candleCount = properties.marketData().candleCount();

        // Step 1: Fetch latest completed candles (served from 30s L1 cache during polling bursts)
        List<CandleData> candles = fetchCandlesWithShortTtlCache(symbol, interval, candleCount);

        List<CandleAnalysisDto> candleDtos = new ArrayList<>(candles.size());
        int cachedHits = 0;
        int newlyQueuedOrInFlight = 0;

        // Step 2: Check PostgreSQL L2 cache & trigger deduplicated async AI jobs for missing candles
        for (CandleData candle : candles) {
            CandleAnalysisEntity entity = persistenceHelper.getOrCreatePendingRecord(
                    candle,
                    retryFailed
            );

            if (entity.getStatus() == AnalysisStatus.COMPLETED) {
                cachedHits++;
            } else if (entity.getStatus() == AnalysisStatus.PENDING) {
                newlyQueuedOrInFlight++;
                triggerDeduplicatedAsyncJob(candle);
            }

            candleDtos.add(CandleAnalysisDto.fromEntity(entity, inFlightJobs.containsKey(candle.toKey())));
        }

        // Step 3: Sort descending by candleTimestamp so the latest candle appears on top
        candleDtos.sort(Comparator.comparing(CandleAnalysisDto::candleTimestamp).reversed());

        boolean allCompleted = candleDtos.stream()
                .allMatch(c -> c.status() == AnalysisStatus.COMPLETED);

        return new StockAnalysisResponse(
                symbol,
                interval,
                properties.provider().mode(),
                marketDataProvider.providerName(),
                aiSummaryProvider.modelIdentifier(),
                allCompleted ? "COMPLETED" : "PROCESSING",
                cachedHits,
                newlyQueuedOrInFlight,
                inFlightJobs.size(),
                Instant.now(),
                candleDtos
        );
    }

    private List<CandleData> fetchCandlesWithShortTtlCache(String symbol, String interval, int limit) {
        String cacheKey = symbol + ":" + interval + ":" + limit;
        CachedCandles cached = ohlcvCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.candles();
        }

        List<CandleData> fresh = marketDataProvider.fetchLatestCompletedCandles(symbol, interval, limit);
        ohlcvCache.put(cacheKey, new CachedCandles(fresh, Instant.now().plus(OHLCV_CACHE_TTL)));
        return fresh;
    }

    private void triggerDeduplicatedAsyncJob(CandleData candle) {
        CandleKey key = candle.toKey();
        inFlightJobs.computeIfAbsent(key, k -> {
            totalAiJobsTriggered.incrementAndGet();
            log.info("[DEDUP-ORCHESTRATOR] Scheduling new async AI job for {}", key);

            CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> executeWithRetryAndTimeout(candle),
                    aiAnalysisExecutor
            );

            future.whenComplete((res, ex) -> {
                inFlightJobs.remove(key);
                log.debug("[DEDUP-ORCHESTRATOR] Removed completed in-flight key {}", key);
            });
            return future;
        });
    }
    private static final int MAX_WAITING_AI_JOBS = 60; // Load-shedding cap

    private void executeWithRetryAndTimeout(CandleData candle) {
        // 1. Load Shedding: Fail fast if the AI queue already has > 60 jobs waiting
        if (aiConcurrencyLimiter.getQueueLength() > MAX_WAITING_AI_JOBS) {
            persistenceHelper.markFailed(candle, "Server busy: AI analysis queue is full. Please retry shortly.");
            return;
        }

        int maxAttempts = Math.max(1, properties.ai().maxRetries() + 1);
        long backoffMs = Math.max(50L, properties.ai().retryBackoffMs());
        int timeoutSeconds = Math.max(1, properties.ai().timeoutSeconds());
        Throwable lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean acquired = false;
            try {
                // 2. Wait in the Virtual Thread line to acquire 1 of the 2 Ollama permits
                aiConcurrencyLimiter.acquire();
                acquired = true;

                // 3. Start the timeout clock ONLY after the permit is acquired!
                AiGenerationResult result = CompletableFuture
                        .supplyAsync(() -> aiSummaryProvider.generateSummary(candle), aiAnalysisExecutor)
                        .get(timeoutSeconds, TimeUnit.SECONDS);

                persistenceHelper.markCompleted(candle, result);
                return;
            } catch (TimeoutException te) {
                lastError = new RuntimeException("AI generation timed out after " + timeoutSeconds + "s", te);
            } catch (ExecutionException ee) {
                lastError = ee.getCause() != null ? ee.getCause() : ee;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                lastError = ie;
                break;
            } finally {
                if (acquired) {
                    aiConcurrencyLimiter.release();
                }
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(backoffMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        String errorMsg = lastError != null ? lastError.getMessage() : "Unknown AI generation failure";
        persistenceHelper.markFailed(candle, errorMsg);
    }

    public int getTotalAiJobsTriggered() {
        return totalAiJobsTriggered.get();
    }

    public int getActiveInFlightCount() {
        return inFlightJobs.size();
    }

    private String normalizeSymbol(String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new IllegalArgumentException("Stock ticker symbol must not be empty");
        }
        String cleaned = rawSymbol.trim().toUpperCase();
        if (cleaned.endsWith(".NS")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        if (!cleaned.matches("^[A-Z0-9._-]{1,20}$")) {
            throw new IllegalArgumentException("Invalid stock ticker symbol format: " + rawSymbol);
        }
        return cleaned;
    }
}