from datetime import datetime, timezone
from typing import List, Optional
import pandas as pd
import yfinance as yf
from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel

app = FastAPI(
    title="Market Analyst Pro — Market Data Service",
    description="Fetches the latest N completed 15-minute OHLCV candles via yfinance",
    version="1.0.0",
)

INTERVAL_MINUTES = {
    "1m": 1,
    "5m": 5,
    "15m": 15,
    "30m": 30,
    "60m": 60,
    "1h": 60,
}


class CandleItem(BaseModel):
    timestamp: str
    open: float
    high: float
    low: float
    close: float
    volume: int


class CandleResponse(BaseModel):
    symbol: str
    interval: str
    count: int
    candles: List[CandleItem]


def filter_completed_candles(
    df: pd.DataFrame,
    interval: str,
    limit: int,
    now_utc: Optional[datetime] = None,
) -> List[CandleItem]:
    """
    Filters a raw yfinance OHLCV DataFrame to strictly completed candles
    (dropping any currently-forming bar) and returns the latest `limit` candles
    in chronological order.
    """
    if df is None or df.empty:
        return []

    if now_utc is None:
        now_utc = datetime.now(timezone.utc)

    interval_mins = INTERVAL_MINUTES.get(interval, 15)
    completed_rows: List[CandleItem] = []

    for idx, row in df.iterrows():
        ts = idx.to_pydatetime()
        if ts.tzinfo is None:
            ts = ts.replace(tzinfo=timezone.utc)
        else:
            ts = ts.astimezone(timezone.utc)

        # A candle starting at `ts` is completed only if `ts + interval_mins <= now_utc`
        elapsed_Minutes = (now_utc - ts).total_seconds() / 60.0
        if elapsed_Minutes < interval_mins:
            continue

        open_val = float(row["Open"].iloc[0] if isinstance(row["Open"], pd.Series) else row["Open"])
        high_val = float(row["High"].iloc[0] if isinstance(row["High"], pd.Series) else row["High"])
        low_val = float(row["Low"].iloc[0] if isinstance(row["Low"], pd.Series) else row["Low"])
        close_val = float(row["Close"].iloc[0] if isinstance(row["Close"], pd.Series) else row["Close"])
        vol_val = int(row["Volume"].iloc[0] if isinstance(row["Volume"], pd.Series) else row["Volume"])

        completed_rows.append(
            CandleItem(
                timestamp=ts.strftime("%Y-%m-%dT%H:%M:%SZ"),
                open=round(open_val, 4),
                high=round(high_val, 4),
                low=round(low_val, 4),
                close=round(close_val, 4),
                volume=vol_val,
            )
        )

    return completed_rows[-limit:]

def download_candles_from_yfinance(symbol: str, interval: str) -> pd.DataFrame:
    # Automatically append .NS for Yahoo Finance NSE lookup if no suffix was passed
    yf_symbol = symbol if "." in symbol else f"{symbol}.NS"
    ticker = yf.Ticker(yf_symbol)
    return ticker.history(period="5d", interval=interval, auto_adjust=False)


@app.get("/api/v1/candles/{symbol}", response_model=CandleResponse)
def get_latest_completed_candles(
    symbol: str,
    interval: str = Query(default="15m", pattern="^(1m|5m|15m|30m|60m|1h)$"),
    limit: int = Query(default=4, ge=1, le=20),
):
    clean_symbol = symbol.strip().upper().removesuffix(".NS")
    try:
        df = download_candles_from_yfinance(clean_symbol, interval)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"yfinance fetch error for {clean_symbol}: {exc}") from exc

    candles = filter_completed_candles(df, interval=interval, limit=limit)
    if not candles:
        raise HTTPException(
            status_code=404,
            detail=f"No completed {interval} candles found for symbol {clean_symbol}",
        )

    return CandleResponse(
        symbol=clean_symbol,
        interval=interval,
        count=len(candles),
        candles=candles,
    )


@app.get("/health")
def health():
    return {"status": "UP", "service": "market-data-service"}


