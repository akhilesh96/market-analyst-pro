package com.portfolio.marketanalyst.api;

import com.portfolio.marketanalyst.api.dto.StockAnalysisResponse;
import com.portfolio.marketanalyst.service.StockAnalysisOrchestrator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analysis")
public class StockAnalysisController {

    private final StockAnalysisOrchestrator orchestrator;

    public StockAnalysisController(StockAnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Primary endpoint:
     * Example: GET /api/v1/analysis/TCS.NS?retryFailed=false
     * - Returns 200 OK if all 4 candle summaries are already cached in PostgreSQL.
     * - Returns 202 ACCEPTED immediately with cached + PENDING candles while missing AI jobs run asynchronously.
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<StockAnalysisResponse> analyzeStock(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "false") boolean retryFailed
    ) {
        StockAnalysisResponse response = orchestrator.analyzeSymbol(symbol, retryFailed);
        HttpStatus status = "COMPLETED".equals(response.overallStatus())
                ? HttpStatus.OK
                : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "activeInFlightJobs", orchestrator.getActiveInFlightCount(),
                "totalAiJobsTriggered", orchestrator.getTotalAiJobsTriggered(),
                "timestamp", Instant.now().toString()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "BAD_REQUEST",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUpstreamError(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "UPSTREAM_FAILURE",
                "message", ex.getMessage() != null ? ex.getMessage() : "Unexpected error",
                "timestamp", Instant.now().toString()
        ));
    }
}
