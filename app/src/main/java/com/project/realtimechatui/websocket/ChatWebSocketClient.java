package com.project.realtimechatui.websocket;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.project.realtimechatui.api.models.WebSocketMessage;
import com.project.realtimechatui.utils.Constants;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.TimeUnit;

public class ChatWebSocketClient extends WebSocketClient {
    private static final String TAG = "ChatWebSocketClient";
    private final Gson gson;
    private final WebSocketListener listener;
    private boolean isManualClose = false;

    public interface WebSocketListener {
        void onConnected();
        void onDisconnected();
        void onMessageReceived(WebSocketMessage message);
        void onError(String error);
    }

    public ChatWebSocketClient(WebSocketListener listener) {
        super(URI.create(Constants.WEBSOCKET_URL));
        this.listener = listener;
        this.gson = new GsonBuilder().create();
        
        // Set connection timeout
        setConnectionLostTimeout(30);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        Log.d(TAG, "WebSocket Connected");
        isManualClose = false;
        if (listener != null) {
            listener.onConnected();
        }
    }

    @Override
    public void onMessage(String message) {
        Log.d(TAG, "Message received: " + message);
        try {
            WebSocketMessage wsMessage = gson.fromJson(message, WebSocketMessage.class);
            if (listener != null) {
                listener.onMessageReceived(wsMessage);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing WebSocket message", e);
            if (listener != null) {
                listener.onError("Error parsing message: " + e.getMessage());
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Log.d(TAG, "WebSocket Closed: " + reason + " (Code: " + code + ", Remote: " + remote + ")");
        if (listener != null) {
            listener.onDisconnected();
        }
        
        // Auto-reconnect if not manually closed
        if (!isManualClose && !remote) {
            scheduleReconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        Log.e(TAG, "WebSocket Error", ex);
        if (listener != null) {
            listener.onError("Connection error: " + ex.getMessage());
        }
    }

    public void sendMessage(WebSocketMessage message) {
        if (isOpen()) {
            try {
                String jsonMessage = gson.toJson(message);
                send(jsonMessage);
                Log.d(TAG, "Message sent: " + jsonMessage);
            } catch (Exception e) {
                Log.e(TAG, "Error sending message", e);
                if (listener != null) {
                    listener.onError("Error sending message: " + e.getMessage());
                }
            }
        } else {
            Log.w(TAG, "WebSocket not connected, cannot send message");
            if (listener != null) {
                listener.onError("Not connected to server");
            }
        }
    }

    public void disconnect() {
        isManualClose = true;
        if (isOpen()) {
            close();
        }
    }

    public boolean isConnected() {
        return isOpen();
    }

    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(5)); // Wait 5 seconds before reconnecting
                if (!isManualClose) {
                    Log.d(TAG, "Attempting to reconnect...");
                    reconnect();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}