from datetime import datetime, timezone
import pandas as pd
import pytest
from main import filter_completed_candles


def build_sample_dataframe() -> pd.DataFrame:
    timestamps = pd.to_datetime(
        [
            "2026-10-26T08:15:00Z",
            "2026-10-26T08:30:00Z",
            "2026-10-26T08:45:00Z",
            "2026-10-26T09:00:00Z",
            "2026-10-26T09:15:00Z",  # Still forming if now is 09:22Z
        ]
    )
    data = {
        "Open": [4100.0, 4110.0, 4105.0, 4120.0, 4125.0],
        "High": [4115.0, 4120.0, 4125.0, 4135.0, 4130.0],
        "Low": [4095.0, 4102.0, 4100.0, 4118.0, 4122.0],
        "Close": [4110.0, 4105.0, 4120.0, 4125.0, 4128.0],
        "Volume": [120000, 135000, 150000, 180000, 45000],
    }
    return pd.DataFrame(data, index=timestamps)


def test_filter_completed_candles_excludes_in_progress_bar():
    df = build_sample_dataframe()
    now_utc = datetime(2026, 10, 26, 9, 22, 0, tzinfo=timezone.utc)

    candles = filter_completed_candles(df, interval="15m", limit=4, now_utc=now_utc)

    assert len(candles) == 4
    # The 09:15Z candle is only 7 minutes old at 09:22Z, so it must be excluded
    timestamps = [c.timestamp for c in candles]
    assert timestamps == [
        "2026-10-26T08:15:00Z",
        "2026-10-26T08:30:00Z",
        "2026-10-26T08:45:00Z",
        "2026-10-26T09:00:00Z",
    ]
    assert candles[-1].close == 4125.0
    assert candles[-1].volume == 180000


def test_filter_completed_candles_includes_bar_once_15_minutes_elapsed():
    df = build_sample_dataframe()
    now_utc = datetime(2026, 10, 26, 9, 30, 5, tzinfo=timezone.utc)

    candles = filter_completed_candles(df, interval="15m", limit=4, now_utc=now_utc)

    assert len(candles) == 4
    assert candles[-1].timestamp == "2026-10-26T09:15:00Z"
    assert candles[0].timestamp == "2026-10-26T08:30:00Z"


def test_filter_completed_candles_handles_empty_dataframe():
    empty_df = pd.DataFrame()
    candles = filter_completed_candles(empty_df, interval="15m", limit=4)
    assert candles == []
