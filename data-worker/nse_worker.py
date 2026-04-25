import json
import yfinance as yf
from kafka import KafkaConsumer, KafkaProducer
from kafka.errors import NoBrokersAvailable
import os
import time

# Config from Environment Variables
bootstrap = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')
COMMAND_TOPIC = 'stock-command'
DATA_TOPIC = 'stock-topic'

def get_kafka_client():
    """Retries connection until Kafka is ready (Essential for Docker)"""
    while True:
        try:
            cons = KafkaConsumer(
                COMMAND_TOPIC,
                bootstrap_servers=bootstrap,
                value_deserializer=lambda m: json.loads(m.decode('utf-8')),
                group_id='python-worker-group' # Added group_id for persistence
            )
            prod = KafkaProducer(
                bootstrap_servers=bootstrap,
                value_serializer=lambda m: json.dumps(m).encode('utf-8')
            )
            return cons, prod
        except NoBrokersAvailable:
            print(f"Waiting for Kafka at {bootstrap}...")
            time.sleep(5)

consumer, producer = get_kafka_client()
print(f"✅ Python Worker connected to {bootstrap}. Listening on '{COMMAND_TOPIC}'...")

try:
    for message in consumer:
        ticker = message.value.get("ticker")
        if not ticker:
            continue

        print(f"📊 Processing analysis request for: {ticker}...")

        # Fetch data with error handling
        try:
            stock = yf.Ticker(f"{ticker}.NS")
            df = stock.history(period='1d', interval='15m')

            if df.empty:
                print(f"⚠️ No data found for {ticker}")
                continue

            for index, row in df.iterrows():
                msg = {
                    "ticker": ticker,
                    "close": round(float(row['Close']), 2),
                    "timestamp": str(index)
                }
                producer.send(DATA_TOPIC, value=msg)

            producer.flush()
            print(f"🚀 Sent {len(df)} candles for {ticker} to '{DATA_TOPIC}'.")

        except Exception as e:
            print(f"❌ Error fetching {ticker}: {str(e)}")

except KeyboardInterrupt:
    print("Stopping worker...")
finally:
    consumer.close()