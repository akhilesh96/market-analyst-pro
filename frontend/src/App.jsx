import React, { useState, useEffect, useRef } from 'react';
import { fetchStockAnalysis } from './api.js';

const QUICK_TICKERS = ['TCS', 'INFY', 'RELIANCE', 'HDFCBANK'];

function formatCandleWindow(isoString, interval = '15m') {
  const startDate = new Date(isoString);
  const minutesToAdd = parseInt(interval.replace(/\D/g, ''), 10) || 15;
  let endDate = new Date(startDate.getTime() + minutesToAdd * 60 * 1000);

  const marketCloseUtc = new Date(startDate);
  marketCloseUtc.setUTCHours(10, 0, 0, 0);
  if (startDate < marketCloseUtc && endDate > marketCloseUtc) {
    endDate = marketCloseUtc;
  }

  const formatTime = (date) => {
    const parts = new Intl.DateTimeFormat('en-IN', {
      timeZone: 'Asia/Kolkata',
      hour: 'numeric',
      minute: '2-digit',
      hour12: true,
    }).formatToParts(date);
    const hour = parts.find((p) => p.type === 'hour')?.value || '';
    const minute = parts.find((p) => p.type === 'minute')?.value || '';
    return `${hour}:${minute}`;
  };

  const dateIST = startDate.toLocaleDateString('en-IN', {
    timeZone: 'Asia/Kolkata',
    day: '2-digit',
    month: 'short',
  });

  return {
    timeRange: `${formatTime(startDate)} - ${formatTime(endDate)}`,
    dateIST,
  };
}

export default function App() {
  // --- 1. Stock Analysis State ---
  const [symbolInput, setSymbolInput] = useState('TCS');
  const [analysis, setAnalysis] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const pollTimerRef = useRef(null);
  const activeRequestIdRef = useRef(0);

  // --- 2. Live Chat State (Hooks inside component) ---
  const [chatOpen, setChatOpen] = useState(false);
  const [messages, setMessages] = useState([]);
  const [chatInput, setChatInput] = useState('');
  const [username] = useState(() => 'Trader_' + Math.floor(1000 + Math.random() * 9000));
  const wsRef = useRef(null);
  const chatBottomRef = useRef(null);

  // --- WebSocket Connection ---
  useEffect(() => {
    const ws = new WebSocket('ws://localhost:3000/ws/chat');
    wsRef.current = ws;

    ws.onopen = () => console.log('Connected to Rust Chat Server');
    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        setMessages((prev) => [...prev, msg]);
      } catch (e) {
        console.error('Invalid message format', e);
      }
    };
    ws.onclose = () => console.log('Disconnected from Rust Chat');

    return () => ws.close();
  }, []);

  useEffect(() => {
    chatBottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, chatOpen]);

  const sendChatMessage = (e) => {
    e.preventDefault();
    if (!chatInput.trim() || !wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) return;

    const payload = {
      username,
      text: chatInput.trim(),
      timestamp: new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }),
    };

    wsRef.current.send(JSON.stringify(payload));
    setChatInput('');
  };

  // --- Stock Analysis Polling & Fetch ---
  const stopPolling = () => {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  };

  const triggerSearch = async (targetSymbol, retryFailed = false) => {
    stopPolling();
    const requestId = ++activeRequestIdRef.current;
    const cleanSymbol = targetSymbol.trim().toUpperCase().replace(/\.NS$/i, '');

    setSymbolInput(cleanSymbol);
    setLoading(true);
    setError(null);
    setAnalysis((prev) => (prev?.symbol === cleanSymbol ? prev : null));

    try {
      const data = await fetchStockAnalysis(cleanSymbol, retryFailed);
      if (requestId !== activeRequestIdRef.current) return;
      setAnalysis(data);

      const hasPending = data.candles?.some((c) => c.status === 'PENDING');
      if (hasPending) {
        pollTimerRef.current = setInterval(async () => {
          try {
            const updated = await fetchStockAnalysis(cleanSymbol, false);
            if (requestId !== activeRequestIdRef.current) return;
            setAnalysis(updated);
            if (!updated.candles?.some((c) => c.status === 'PENDING')) {
              stopPolling();
            }
          } catch {
            stopPolling();
          }
        }, 1200);
      }
    } catch (err) {
      if (requestId === activeRequestIdRef.current) {
        setAnalysis(null);
        setError(err.message);
      }
    } finally {
      if (requestId === activeRequestIdRef.current) {
        setLoading(false);
      }
    }
  };

  useEffect(() => {
    triggerSearch('TCS');
    return () => stopPolling();
  }, []);

  const handleSubmit = (e) => {
    e.preventDefault();
    if (symbolInput.trim()) {
      triggerSearch(symbolInput);
    }
  };

  return (
    <div className="container">
      <header className="header">
        <h1>Market Analyst Pro</h1>
        <form onSubmit={handleSubmit} className="search-bar">
          <input
            type="text"
            value={symbolInput}
            onChange={(e) => setSymbolInput(e.target.value)}
            placeholder="Search ticker (e.g. TCS, INFY, SBIN)..."
          />
          <button type="submit" disabled={loading}>
            {loading ? '...' : 'Analyze'}
          </button>
        </form>
        <div className="quick-chips">
          {QUICK_TICKERS.map((ticker) => (
            <button
              key={ticker}
              type="button"
              className={analysis?.symbol === ticker ? 'chip active' : 'chip'}
              onClick={() => triggerSearch(ticker)}
            >
              {ticker}
            </button>
          ))}
        </div>
      </header>

      {error && <div className="error-banner">{error}</div>}

      {analysis && (
        <main>
          <div className="stock-bar">
            <div className="stock-title">
              <h2>{analysis.symbol}</h2>
              <span className="interval-tag">{analysis.interval}</span>
            </div>
            <span className="cache-status">
              {analysis.overallStatus === 'COMPLETED'
                ? `${analysis.cachedCount}/${analysis.candles.length} Cached`
                : 'Analyzing...'}
            </span>
          </div>

          <div className="timeline">
            {analysis.candles.map((candle, idx) => {
              const change = ((candle.close - candle.open) / candle.open) * 100;
              const isUp = change >= 0;
              const { timeRange, dateIST } = formatCandleWindow(
                candle.candleTimestamp,
                candle.interval
              );

              return (
                <div key={candle.candleTimestamp} className="timeline-row">
                  <div className="timeline-track">
                    <span className={`dot dot-${candle.status.toLowerCase()}`} />
                    {idx < analysis.candles.length - 1 && <span className="line" />}
                  </div>

                  <div className="timeline-content">
                    <div className="row-top">
                      <div className="time-meta">
                        <span className="candle-label">Candle #{idx + 1}</span>
                        <strong className="time-window">{timeRange}</strong>
                        <span className="date-label">{dateIST}</span>
                      </div>

                      <div className="price-meta">
                        <span className={isUp ? 'price up' : 'price down'}>
                          ₹{Number(candle.close).toFixed(2)} ({isUp ? '+' : ''}
                          {change.toFixed(2)}%)
                        </span>
                        {candle.sentiment && (
                          <span className={`badge badge-${candle.sentiment.toLowerCase()}`}>
                            {candle.sentiment}
                          </span>
                        )}
                      </div>
                    </div>

                    {candle.status === 'COMPLETED' && (
                      <p className="summary-text">{candle.aiSummary}</p>
                    )}

                    {candle.status === 'PENDING' && (
                      <div className="pending-row">
                        <span className="spinner" />
                        <span>Generating AI summary...</span>
                      </div>
                    )}

                    {candle.status === 'FAILED' && (
                      <div className="failed-row">
                        <span>Failed to generate summary.</span>
                        <button
                          type="button"
                          onClick={() => triggerSearch(analysis.symbol, true)}
                        >
                          Retry
                        </button>
                      </div>
                    )}

                    <div className="ohlcv-muted">
                      <span>O: ₹{Number(candle.open).toFixed(2)}</span>
                      <span>H: ₹{Number(candle.high).toFixed(2)}</span>
                      <span>L: ₹{Number(candle.low).toFixed(2)}</span>
                      <span>Vol: {Number(candle.volume).toLocaleString()}</span>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </main>
      )}

      {/* Floating Rust Chat Widget */}
      <div className="chat-fab-container">
        {!chatOpen ? (
          <button className="chat-fab" onClick={() => setChatOpen(true)}>
            💬 Live Room
          </button>
        ) : (
          <div className="chat-box">
            <div className="chat-header">
              <span>⚡ Rust Live Chat (Port 3000)</span>
              <button className="chat-close" onClick={() => setChatOpen(false)}>✕</button>
            </div>
            <div className="chat-messages">
              {messages.length === 0 ? (
                <div className="chat-empty">No messages yet. Say hello!</div>
              ) : (
                messages.map((m, idx) => (
                  <div key={idx} className={m.username === username ? 'chat-msg self' : 'chat-msg'}>
                    <div className="msg-meta">
                      <strong>{m.username}</strong> <span>{m.timestamp}</span>
                    </div>
                    <div className="msg-bubble">{m.text}</div>
                  </div>
                ))
              )}
              <div ref={chatBottomRef} />
            </div>
            <form onSubmit={sendChatMessage} className="chat-footer">
              <input
                type="text"
                placeholder="Share a trade idea..."
                value={chatInput}
                onChange={(e) => setChatInput(e.target.value)}
              />
              <button type="submit">Send</button>
            </form>
          </div>
        )}
      </div>
    </div>
  );
}