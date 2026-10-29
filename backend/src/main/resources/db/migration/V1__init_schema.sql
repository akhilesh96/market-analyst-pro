CREATE TABLE IF NOT EXISTS candle_analyses (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    interval VARCHAR(16) NOT NULL,
    candle_timestamp TIMESTAMPTZ NOT NULL,
    open_price NUMERIC(14, 4) NOT NULL,
    high_price NUMERIC(14, 4) NOT NULL,
    low_price NUMERIC(14, 4) NOT NULL,
    close_price NUMERIC(14, 4) NOT NULL,
    volume BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    ai_summary TEXT,
    sentiment VARCHAR(16),
    error_message TEXT,
    model_used VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_symbol_interval_timestamp UNIQUE (symbol, interval, candle_timestamp)
);

CREATE INDEX IF NOT EXISTS idx_candle_lookup
    ON candle_analyses (symbol, interval, candle_timestamp DESC);
