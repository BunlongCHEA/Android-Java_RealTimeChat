package com.project.realtimechatui.websocket;

import android.util.Log;
import com.google.gson.Gson;
import com.project.realtimechatui.api.models.Message;
import com.project.realtimechatui.utils.Constants;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class WebSocketService {
    private static final String TAG = "WebSocketService";
    private static WebSocketService instance;
    private WebSocketClient webSocketClient;
    private WebSocketListener listener;
    private Gson gson;
    private boolean isConnected = false;

    private WebSocketService() {
        gson = new Gson();
    }

    public static synchronized WebSocketService getInstance() {
        if (instance == null) {
            instance = new WebSocketService();
        }
        return instance;
    }

    public interface WebSocketListener {
        void onConnected();
        void onDisconnected();
        void onMessageReceived(Message message);
        void onError(String error);
    }

    public void setListener(WebSocketListener listener) {
        this.listener = listener;
    }

    public void connect(String accessToken) {
        try {
            if (webSocketClient != null && webSocketClient.isOpen()) {
                Log.d(TAG, "WebSocket already connected");
                return;
            }

            URI serverUri = URI.create(Constants.WEBSOCKET_URL);
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            
            webSocketClient = new WebSocketClient(serverUri, headers) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.d(TAG, "WebSocket Connected");
                    isConnected = true;
                    if (listener != null) {
                        listener.onConnected();
                    }
                }

                @Override
                public void onMessage(String messageText) {
                    Log.d(TAG, "Message received: " + messageText);
                    try {
                        Message message = gson.fromJson(messageText, Message.class);
                        if (listener != null) {
                            listener.onMessageReceived(message);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing message: " + e.getMessage());
                        if (listener != null) {
                            listener.onError("Error parsing message: " + e.getMessage());
                        }
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "WebSocket Disconnected: " + reason);
                    isConnected = false;
                    if (listener != null) {
                        listener.onDisconnected();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket Error: " + ex.getMessage());
                    isConnected = false;
                    if (listener != null) {
                        listener.onError("Connection error: " + ex.getMessage());
                    }
                }
            };

            webSocketClient.connect();
            
        } catch (Exception e) {
            Log.e(TAG, "Error connecting to WebSocket: " + e.getMessage());
            if (listener != null) {
                listener.onError("Connection failed: " + e.getMessage());
            }
        }
    }

    public void sendMessage(Message message) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                String messageJson = gson.toJson(message);
                webSocketClient.send(messageJson);
                Log.d(TAG, "Message sent: " + messageJson);
            } catch (Exception e) {
                Log.e(TAG, "Error sending message: " + e.getMessage());
                if (listener != null) {
                    listener.onError("Error sending message: " + e.getMessage());
                }
            }
        } else {
            Log.w(TAG, "WebSocket not connected. Message not sent.");
            if (listener != null) {
                listener.onError("Not connected to server");
            }
        }
    }

    public void disconnect() {
        if (webSocketClient != null) {
            webSocketClient.close();
            webSocketClient = null;
            isConnected = false;
            Log.d(TAG, "WebSocket disconnected manually");
        }
    }

    public boolean isConnected() {
        return isConnected && webSocketClient != null && webSocketClient.isOpen();
    }
}