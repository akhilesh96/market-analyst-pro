use axum::{
    extract::ws::{Message, WebSocket, WebSocketUpgrade},
    response::IntoResponse,
    routing::get,
    Extension, Router,
};
use futures_util::{SinkExt, StreamExt};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use tokio::sync::broadcast;
use tower_http::cors::CorsLayer;

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct ChatMessage {
    pub username: String,
    pub text: String,
    pub timestamp: String,
}

#[tokio::main]
async fn main() {
    // 1. Create a Tokio broadcast channel with a buffer of 100 messages.
    // Multiple producers can send, and every subscriber receives every message.
    let (tx, _rx) = broadcast::channel::<String>(100);

    // 2. Wrap the sender in an Arc (Atomic Reference Counting) so it can be safely
    // cloned and shared across independent asynchronous worker threads.
    let shared_tx = Arc::new(tx);

    let app = Router::new()
        .route("/ws/chat", get(ws_handler))
        .layer(CorsLayer::permissive())
        .layer(Extension(shared_tx));

    let addr = "0.0.0.0:3000";
    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();
    println!("⚡ Rust WebSocket Chat Server running on ws://localhost:3000/ws/chat");

    axum::serve(listener, app).await.unwrap();
}

async fn ws_handler(
    ws: WebSocketUpgrade,
    Extension(tx): Extension<Arc<broadcast::Sender<String>>>,
) -> impl IntoResponse {
    // Upgrade the HTTP GET request to a persistent duplex WebSocket connection
    ws.on_upgrade(move |socket| handle_socket(socket, tx))
}

async fn handle_socket(socket: WebSocket, tx: Arc<broadcast::Sender<String>>) {
    // Split the WebSocket into a read stream (incoming from client)
    // and a write sink (outgoing to client)
    let (mut sender, mut receiver) = socket.split();

    // Subscribe this client to the shared Tokio broadcast channel
    let mut rx = tx.subscribe();

    // Background Task A: Receive broadcast messages and push them out to this WebSocket client
    let mut send_task = tokio::spawn(async move {
        while let Ok(msg) = rx.recv().await {
            if sender.send(Message::Text(msg)).await.is_err() {
                break; // Client disconnected
            }
        }
    });

    // Background Task B: Read incoming messages from this client and broadcast to everyone
    let tx_clone = tx.clone();
    let mut recv_task = tokio::spawn(async move {
        while let Some(Ok(Message::Text(text))) = receiver.next().await {
            // Validate JSON format before broadcasting
            if serde_json::from_str::<ChatMessage>(&text).is_ok() {
                let _ = tx_clone.send(text);
            }
        }
    });

    // tokio::select! waits for whichever task completes first (e.g. client disconnects),
    // and aborts the other task immediately, freeing all memory.
    tokio::select! {
        _ = (&mut send_task) => recv_task.abort(),
        _ = (&mut recv_task) => send_task.abort(),
    };
}