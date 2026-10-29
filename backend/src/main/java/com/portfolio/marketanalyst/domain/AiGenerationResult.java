package com.portfolio.marketanalyst.domain;

public record AiGenerationResult(
        String summary,
        String sentiment,
        String modelUsed
) {}
