const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

export async function fetchStockAnalysis(symbol, retryFailed = false) {
  const encoded = encodeURIComponent(symbol.trim().toUpperCase());
  const response = await fetch(`${API_BASE}/api/v1/analysis/${encoded}?retryFailed=${retryFailed}`);
  if (!response.ok) {
    const errBody = await response.json().catch(() => ({}));
    throw new Error(errBody.message || `API request failed with status ${response.status}`);
  }
  return response.json();
}
