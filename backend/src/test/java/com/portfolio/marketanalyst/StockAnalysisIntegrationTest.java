package com.portfolio.marketanalyst;

import com.portfolio.marketanalyst.ai.AiSummaryProvider;
import com.portfolio.marketanalyst.api.dto.StockAnalysisResponse;
import com.portfolio.marketanalyst.domain.AiGenerationResult;
import com.portfolio.marketanalyst.domain.AnalysisStatus;
import com.portfolio.marketanalyst.domain.CandleData;
import com.portfolio.marketanalyst.repository.CandleAnalysisRepository;
import com.portfolio.marketanalyst.service.StockAnalysisOrchestrator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.provider.mode=mock",
        "app.ai.mock-latency-ms=250",
        "app.ai.max-retries=2",
        "app.ai.retry-backoff-ms=50"
})
@Testcontainers
class StockAnalysisIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("market_analyst_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired
    private StockAnalysisOrchestrator orchestrator;

    @Autowired
    private CandleAnalysisRepository repository;

    @Autowired
    private CountingAiProvider countingAiProvider;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        countingAiProvider.reset();
    }

    @Test
    @DisplayName("1. Asynchronous Non-Blocking + Caching: First request returns PENDING immediately, then caches COMPLETED in PostgreSQL")
    void shouldReturnPendingImmediatelyAndCacheCompletedSummaries() {
        // First call for TCS.NS returns immediately without blocking on AI
        StockAnalysisResponse initial = orchestrator.analyzeSymbol("TCS.NS", false);
        assertThat(initial.symbol()).isEqualTo("TCS.NS");
        assertThat(initial.candles()).hasSize(4);
        assertThat(initial.overallStatus()).isEqualTo("PROCESSING");

        // Wait for async virtual threads to complete AI generation and persist to PostgreSQL
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    StockAnalysisResponse polled = orchestrator.analyzeSymbol("TCS.NS", false);
                    assertThat(polled.overallStatus()).isEqualTo("COMPLETED");
                    assertThat(polled.cachedCount()).isEqualTo(4);
                    assertThat(polled.candles())
                            .extracting(c -> c.status())
                            .containsOnly(AnalysisStatus.COMPLETED);
                });

        // Verify exactly 4 AI calls were made for the 4 candles
        assertThat(countingAiProvider.getInvocationCount()).isEqualTo(4);

        // Subsequent search by another user must serve 100% from PostgreSQL cache (0 new AI calls)
        StockAnalysisResponse cachedResponse = orchestrator.analyzeSymbol("TCS.NS", false);
        assertThat(cachedResponse.overallStatus()).isEqualTo("COMPLETED");
        assertThat(cachedResponse.cachedCount()).isEqualTo(4);
        assertThat(countingAiProvider.getInvocationCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("2. In-Flight Deduplication: 10 concurrent users requesting TCS.NS share exactly 1 AI job per candle (4 total)")
    void shouldDeduplicateConcurrentRequestsForSameSymbol() throws Exception {
        ExecutorService clientPool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<StockAnalysisResponse>> tasks = IntStream.range(0, 10)
                    .<Callable<StockAnalysisResponse>>mapToObj(i -> () -> orchestrator.analyzeSymbol("TCS.NS", false))
                    .toList();

            List<Future<StockAnalysisResponse>> futures = clientPool.invokeAll(tasks);
            for (Future<StockAnalysisResponse> f : futures) {
                assertThat(f.get().candles()).hasSize(4);
            }

            Awaitility.await()
                    .atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> {
                        StockAnalysisResponse finalState = orchestrator.analyzeSymbol("TCS.NS", false);
                        assertThat(finalState.overallStatus()).isEqualTo("COMPLETED");
                    });

            // Despite 10 concurrent users * 4 candles = 40 candle lookups, only 4 AI calls were executed
            assertThat(countingAiProvider.getInvocationCount()).isEqualTo(4);
        } finally {
            clientPool.shutdownNow();
        }
    }

    @Test
    @DisplayName("3. Fault Tolerance & Retry: Transient AI failures automatically retry and recover")
    void shouldRetryOnTransientFailureAndSucceed() {
        countingAiProvider.setFailuresBeforeSuccess(1); // Fail first attempt on each candle, succeed on retry

        orchestrator.analyzeSymbol("INFY.NS", false);

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    StockAnalysisResponse response = orchestrator.analyzeSymbol("INFY.NS", false);
                    assertThat(response.overallStatus()).isEqualTo("COMPLETED");
                    assertThat(response.candles())
                            .extracting(c -> c.status())
                            .containsOnly(AnalysisStatus.COMPLETED);
                });
    }

    @TestConfiguration
    static class TestAiConfig {
        @Bean
        @Primary
        public CountingAiProvider countingAiProvider() {
            return new CountingAiProvider();
        }
    }

    static class CountingAiProvider implements AiSummaryProvider {
        private final AtomicInteger invocations = new AtomicInteger(0);
        private volatile int failuresBeforeSuccess = 0;

        @Override
        public AiGenerationResult generateSummary(CandleData candle) {
            int count = invocations.incrementAndGet();
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (failuresBeforeSuccess > 0 && count <= failuresBeforeSuccess) {
                throw new RuntimeException("Simulated transient Ollama timeout");
            }
            return new AiGenerationResult(
                    "Technical summary for " + candle.symbol() + " at " + candle.timestamp(),
                    "BULLISH",
                    "test-model"
            );
        }

        @Override
        public String modelIdentifier() {
            return "test-model";
        }

        void reset() {
            invocations.set(0);
            failuresBeforeSuccess = 0;
        }

        void setFailuresBeforeSuccess(int failures) {
            this.failuresBeforeSuccess = failures;
        }

        int getInvocationCount() {
            return invocations.get();
        }
    }
}
