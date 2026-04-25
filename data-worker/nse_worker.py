import json
import yfinance as yf
from kafka import KafkaConsumer, KafkaProducer

# Config
bootstrap = ['localhost:9092']
consumer = KafkaConsumer('stock-command', bootstrap_servers=bootstrap,
                           value_deserializer=lambda m: json.loads(m.decode('utf-8')))
producer = KafkaProducer(bootstrap_servers=bootstrap,
                           value_serializer=lambda m: json.dumps(m).encode('utf-8'))

print("Python Worker: Listening for stock names...")

for message in consumer:
    ticker = message.value.get("ticker")
    print(f"Working on {ticker}...")

    # Fetch full day (period='1d') at 15m intervals
    df = yf.Ticker(f"{ticker}.NS").history(period='1d', interval='15m')

    for index, row in df.iterrows():
        # Convert NumPy float64 to Python float for JSON compatibility
        msg = {
            "ticker": ticker,
            "close": round(float(row['Close']), 2),
            "timestamp": str(index)
        }
        producer.send('stock-topic', value=msg)

    producer.flush()
    print(f"Full day data for {ticker} sent to Kafka.")