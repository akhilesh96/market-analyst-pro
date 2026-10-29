package com.portfolio.marketanalyst.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        ProviderConfig provider,
        MarketDataConfig marketData,
        AiConfig ai
) {
    public record ProviderConfig(String mode) {}

    public record MarketDataConfig(
            String baseUrl,
            int candleCount,
            String interval,
            int timeoutSeconds
    ) {}

    public record AiConfig(
            String ollamaBaseUrl,
            String ollamaModel,
            int timeoutSeconds,
            int maxRetries,
            long retryBackoffMs,
            long mockLatencyMs
    ) {}
}
