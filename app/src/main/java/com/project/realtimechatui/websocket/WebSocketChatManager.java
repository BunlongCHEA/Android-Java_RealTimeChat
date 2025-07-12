package com.project.realtimechatui.websocket;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.project.realtimechatui.api.models.ChatMessage;
import com.project.realtimechatui.utils.Constants;
import com.project.realtimechatui.utils.SharedPrefManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.CompletableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.LifecycleEvent;
import ua.naiksoftware.stomp.dto.StompHeader;
import ua.naiksoftware.stomp.dto.StompMessage;

public class WebSocketChatManager {
    private static final String TAG = "WebSocketChatManager";
    private static WebSocketChatManager instance;

    private StompClient stompClient;
    private ChatMessageListener messageListener;
    private ConnectionListener connectionListener;
    private Gson gson;
    private boolean isConnected = false;
    private SharedPrefManager sharedPrefManager;
    private CompositeDisposable compositeDisposable;
    private String currentChatRoomTopic;
    private Long currentChatRoomId;
    private Handler typingHandler;
    private Runnable typingStopRunnable;

    private boolean isSendingMessage = false;

    public interface ChatMessageListener {
        void onMessageReceived(ChatMessage message);
        void onTypingIndicator(String username, boolean isTyping);
        void onUserStatusChanged(Long userId, boolean isOnline);
        void onMessageEdited(Long messageId, String newContent);
        void onMessageDeleted(Long messageId);
        void onUserJoined(String username);
        void onUserLeft(String username);
        void onError(String error);
    }

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }

    private WebSocketChatManager() {
        gson = new Gson();
        sharedPrefManager = SharedPrefManager.getInstance();
        compositeDisposable = new CompositeDisposable();
        typingHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized WebSocketChatManager getInstance() {
        if (instance == null) {
            instance = new WebSocketChatManager();
        }
        return instance;
    }

    public void setMessageListener(ChatMessageListener listener) {
        this.messageListener = listener;
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public boolean isConnected() {
        return isConnected && stompClient != null && stompClient.isConnected();
    }

    public void connect() {
        if (isConnected() || !sharedPrefManager.isLoggedIn()) {
            Log.w(TAG, "Already connected or user not logged in");
            return;
        }

        try {
            String wsUrl = Constants.WS_BASE_URL;
            Log.d(TAG, "Connecting to WebSocket: " + wsUrl);

            // Create STOMP client with SockJS fallback
            stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, wsUrl);

            // Configure heartbeat
            stompClient.withClientHeartbeat(Constants.HEARTBEAT_INTERVAL)
                    .withServerHeartbeat(Constants.HEARTBEAT_INTERVAL);

            // Get auth token
            String token = sharedPrefManager.getAccessToken();
            if (token != null) {
                // Add auth header for STOMP connection
                List<StompHeader> headers = new ArrayList<>();
                headers.add(new StompHeader("Authorization", "Bearer " + token));

                // Listen to lifecycle events
                Disposable lifeCycleDisposable = stompClient.lifecycle()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::handleLifecycleEvent, throwable -> {
                            Log.e(TAG, "Lifecycle error", throwable);
                            if (connectionListener != null) {
                                connectionListener.onError("Connection lifecycle error: " + throwable.getMessage());
                            }
                        });

                compositeDisposable.add(lifeCycleDisposable);

                // Connect with headers
                stompClient.connect(headers);

            } else {
                Log.e(TAG, "No auth token available");
                if (connectionListener != null) {
                    connectionListener.onError("No authentication token");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error connecting to WebSocket", e);
            if (connectionListener != null) {
                connectionListener.onError(e.getMessage());
            }
        }
    }

    private void handleLifecycleEvent(LifecycleEvent lifecycleEvent) {
        switch (lifecycleEvent.getType()) {
            case OPENED:
                Log.d(TAG, "WebSocket connection opened");
                isConnected = true;
                if (connectionListener != null) {
                    connectionListener.onConnected();
                }
                break;

            case CLOSED:
                Log.d(TAG, "WebSocket connection closed");
                isConnected = false;
                if (connectionListener != null) {
                    connectionListener.onDisconnected();
                }
                break;

            case ERROR:
                Log.e(TAG, "WebSocket connection error", lifecycleEvent.getException());
                isConnected = false;
                if (connectionListener != null) {
                    connectionListener.onError("Connection error: " +
                            (lifecycleEvent.getException() != null ?
                                    lifecycleEvent.getException().getMessage() : "Unknown error"));
                }
                break;

            case FAILED_SERVER_HEARTBEAT:
                Log.w(TAG, "Server heartbeat failed");
                // Attempt to reconnect
                reconnect();
                break;
        }
    }

    public void leaveChatRoom() {
        if (currentChatRoomId != null && isConnected()) {
            try {
                // Send leave room message
                Map<String, Object> leavePayload = new HashMap<>();
                leavePayload.put("action", "leave");
                stompClient.send(Constants.WS_LEAVE_ROOM + currentChatRoomId, gson.toJson(leavePayload))
                        .compose(applySchedulers())
                        .subscribe(() -> {
                            Log.d(TAG, "Leave room message sent successfully");
                        }, throwable -> {
                            Log.e(TAG, "Error sending leave room message", throwable);
                        });

                Log.d(TAG, "Left chat room: " + currentChatRoomId);
            } catch (Exception e) {
                Log.e(TAG, "Error leaving chat room", e);
            }
        }

        // Reset current room
        currentChatRoomId = null;
        currentChatRoomTopic = null;
    }

    public void sendMessage(String content) {
        if (!isConnected() || currentChatRoomId == null) {
            Log.w(TAG, "Cannot send message: not connected or no chat room");
            if (messageListener != null) {
                messageListener.onError("Not connected or no chat room selected");
            }
            return;
        }

        try {
            Map<String, Object> messagePayload = new HashMap<>();
            messagePayload.put("content", content);
            messagePayload.put("type", Constants.MESSAGE_TYPE_TEXT);

            stompClient.send(Constants.WS_SEND_MESSAGE + currentChatRoomId, gson.toJson(messagePayload))
                    .compose(applySchedulers())
                    .subscribe(() -> {
                        Log.d(TAG, "Message sent successfully");
                    }, throwable -> {
                        Log.e(TAG, "Error sending message", throwable);
                        if (messageListener != null) {
                            messageListener.onError("Failed to send message: " + throwable.getMessage());
                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error preparing message", e);
            if (messageListener != null) {
                messageListener.onError("Failed to send message: " + e.getMessage());
            }
        }
    }

    public void sendTypingIndicator(boolean isTyping) {
        if (!isConnected() || currentChatRoomId == null) {
            return;
        }

        try {
            Map<String, Object> typingPayload = new HashMap<>();
            typingPayload.put("isTyping", isTyping);

            stompClient.send(Constants.WS_TYPING_INDICATOR + currentChatRoomId, gson.toJson(typingPayload))
                    .compose(applySchedulers())
                    .subscribe(() -> {
                        Log.d(TAG, "Typing indicator sent: " + isTyping);
                    }, throwable -> {
                        Log.e(TAG, "Error sending typing indicator", throwable);
                    });

            // Auto-stop typing indicator after delay
            if (isTyping) {
                if (typingStopRunnable != null) {
                    typingHandler.removeCallbacks(typingStopRunnable);
                }
                typingStopRunnable = () -> sendTypingIndicator(false);
                typingHandler.postDelayed(typingStopRunnable, Constants.TYPING_INDICATOR_DELAY);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error sending typing indicator", e);
        }
    }

    private void handleIncomingMessage(StompMessage stompMessage) {
        try {
            String payload = stompMessage.getPayload();
            Log.d(TAG, "Received message: " + payload);

            ChatMessage chatMessage = gson.fromJson(payload, ChatMessage.class);
            if (messageListener != null) {
                messageListener.onMessageReceived(chatMessage);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling incoming message", e);
        }
    }

    private void handleTypingIndicator(StompMessage stompMessage) {
        try {
            JsonObject jsonObject = gson.fromJson(stompMessage.getPayload(), JsonObject.class);
            String username = jsonObject.get("username").getAsString();
            boolean isTyping = jsonObject.get("isTyping").getAsBoolean();

            if (messageListener != null) {
                messageListener.onTypingIndicator(username, isTyping);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing typing indicator", e);
        }
    }

    private void handleRoomEvent(StompMessage stompMessage) {
        try {
            String payload = stompMessage.getPayload();
            JsonObject jsonObject = JsonParser.parseString(payload).getAsJsonObject();

            String type = jsonObject.get("type").getAsString();
            String username = jsonObject.has("username") ? jsonObject.get("username").getAsString() : null;

            if (messageListener != null) {
                switch (type) {
                    case Constants.WS_TYPE_USER_JOINED:
                        if (username != null && !username.equals(sharedPrefManager.getUsername())) {
                            messageListener.onUserJoined(username);
                        }
                        break;

                    case Constants.WS_TYPE_USER_LEFT:
                        if (username != null && !username.equals(sharedPrefManager.getUsername())) {
                            messageListener.onUserLeft(username);
                        }
                        break;

                    case Constants.WS_TYPE_MESSAGE_UPDATED:
                        Long messageId = jsonObject.get("messageId").getAsLong();
                        String newContent = jsonObject.get("newContent").getAsString();
                        messageListener.onMessageEdited(messageId, newContent);
                        break;

                    case Constants.WS_TYPE_MESSAGE_DELETED:
                        Long deletedMessageId = jsonObject.get("messageId").getAsLong();
                        messageListener.onMessageDeleted(deletedMessageId);
                        break;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling room event", e);
        }
    }

    private void handleErrorMessage(StompMessage stompMessage) {
        try {
            String errorMessage = stompMessage.getPayload();
            Log.e(TAG, "Received error message: " + errorMessage);

            if (messageListener != null) {
                messageListener.onError(errorMessage);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling error message", e);
        }
    }

    public void disconnect() {
        try {
            leaveChatRoom();

            if (stompClient != null) {
                stompClient.disconnect();
            }

            compositeDisposable.clear();
            isConnected = false;

            // Clear typing handler
            if (typingStopRunnable != null) {
                typingHandler.removeCallbacks(typingStopRunnable);
                typingStopRunnable = null;
            }

            Log.d(TAG, "WebSocket disconnected successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error disconnecting from WebSocket", e);
        }
    }

    public void reconnect() {
        Log.d(TAG, "Attempting to reconnect...");
        disconnect();

        // Wait a bit before reconnecting
        typingHandler.postDelayed(() -> {
            if (!isConnected() && sharedPrefManager.isLoggedIn()) {
                connect();

                // Rejoin chat room if we were in one
                if (currentChatRoomId != null) {
                    typingHandler.postDelayed(() -> joinChatRoom(currentChatRoomId), 1000);
                }
            }
        }, 2000);
    }

    private CompletableTransformer applySchedulers() {
        return upstream -> upstream
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    public Long getCurrentChatRoomId() {
        return currentChatRoomId;
    }

    // Add these methods to WebSocketChatManager.java

    public void sendTypingIndicator(Long chatRoomId, boolean isTyping) {
        if (!isConnected() || currentChatRoomId == null || !currentChatRoomId.equals(chatRoomId)) {
            return;
        }

        try {
            Map<String, Object> typingData = new HashMap<>();
            typingData.put("isTyping", isTyping);

            String destination = "/app/chat.typing/" + chatRoomId;
            String jsonData = gson.toJson(typingData);

            Disposable disposable = stompClient.send(destination, jsonData)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> {
                        Log.d(TAG, "Typing indicator sent: " + isTyping);
                    }, throwable -> {
                        Log.e(TAG, "Error sending typing indicator", throwable);
                    });

            compositeDisposable.add(disposable);
        } catch (Exception e) {
            Log.e(TAG, "Error sending typing indicator", e);
        }
    }

    public void subscribeToChatRoom(Long chatRoomId) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot subscribe - not connected");
            return;
        }

        // To prevent duplicate subscriptions
        if (currentChatRoomId != null && currentChatRoomId.equals(chatRoomId)) {
            Log.d(TAG, "Already subscribed to chat room: " + chatRoomId);
            return;
        }

        try {
            currentChatRoomId = chatRoomId;
            String chatTopic = "/topic/chat/" + chatRoomId;
            String typingTopic = "/topic/chat/" + chatRoomId + "/typing";
            String statusTopic = "/topic/chat/" + chatRoomId + "/status";
            String eventsTopic = "/topic/chat/" + chatRoomId + "/events";

            // Subscribe to chat messages
            Disposable chatDisposable = stompClient.topic(chatTopic)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::handleChatMessage, throwable -> {
                        Log.e(TAG, "Error in chat topic subscription", throwable);
                    });

            // Subscribe to typing indicators
            Disposable typingDisposable = stompClient.topic(typingTopic)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::handleTypingIndicator, throwable -> {
                        Log.e(TAG, "Error in typing topic subscription", throwable);
                    });

            // Subscribe to status updates
            Disposable statusDisposable = stompClient.topic(statusTopic)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::handleStatusUpdate, throwable -> {
                        Log.e(TAG, "Error in status topic subscription", throwable);
                    });

            // Subscribe to events
            Disposable eventsDisposable = stompClient.topic(eventsTopic)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::handleEventMessage, throwable -> {
                        Log.e(TAG, "Error in events topic subscription", throwable);
                    });

            compositeDisposable.addAll(chatDisposable, typingDisposable, statusDisposable, eventsDisposable);
            currentChatRoomTopic = chatTopic;

            Log.d(TAG, "Subscribed to chat room: " + chatRoomId);
        } catch (Exception e) {
            Log.e(TAG, "Error subscribing to chat room", e);
        }
    }

    public void joinChatRoom(Long chatRoomId) {
        if (!isConnected()) {
            Log.w(TAG, "Not connected to WebSocket");
            return;
        }

        this.currentChatRoomId = chatRoomId;
        this.currentChatRoomTopic = Constants.WS_CHAT_TOPIC + chatRoomId;

        try {
            // Subscribe to chat room messages
            Disposable messageDisposable = stompClient.topic(currentChatRoomTopic)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::handleIncomingMessage, throwable -> {
                        Log.e(TAG, "Error subscribing to chat room", throwable);
                        if (messageListener != null) {
                            messageListener.onError("Failed to subscribe to chat room: " + throwable.getMessage());
                        }
                    });

            compositeDisposable.add(messageDisposable);

            // Subscribe to typing indicators
            Disposable typingDisposable = stompClient.topic(currentChatRoomTopic + Constants.WS_TYPING_TOPIC)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::handleTypingIndicator, throwable -> {
                        Log.e(TAG, "Error subscribing to typing indicators", throwable);
                    });

            compositeDisposable.add(typingDisposable);

            // Subscribe to room events
            Disposable eventsDisposable = stompClient.topic(currentChatRoomTopic + Constants.WS_EVENTS_TOPIC)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::handleRoomEvent, throwable -> {
                        Log.e(TAG, "Error subscribing to room events", throwable);
                    });

            compositeDisposable.add(eventsDisposable);

            // Subscribe to personal error queue
            String username = sharedPrefManager.getUsername();
            if (username != null) {
                Disposable errorDisposable = stompClient.topic("/user/" + username + Constants.WS_ERROR_QUEUE)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::handleErrorMessage, throwable -> {
                            Log.e(TAG, "Error subscribing to error queue", throwable);
                        });

                compositeDisposable.add(errorDisposable);
            }

            // Send join room message
            Map<String, Object> joinPayload = new HashMap<>();
            joinPayload.put("action", "join");
            stompClient.send(Constants.WS_JOIN_ROOM + chatRoomId, gson.toJson(joinPayload))
                    .compose(applySchedulers())
                    .subscribe(() -> {
                        Log.d(TAG, "Join room message sent successfully");
                    }, throwable -> {
                        Log.e(TAG, "Error sending join room message", throwable);
                    });

            Log.d(TAG, "Successfully joined chat room: " + chatRoomId);

        } catch (Exception e) {
            Log.e(TAG, "Error joining chat room", e);
            if (messageListener != null) {
                messageListener.onError("Failed to join chat room: " + e.getMessage());
            }
        }
    }

    public void leaveChatRoom(Long chatRoomId) {
        if (!isConnected()) {
            return;
        }

        try {
            String destination = "/app/chat.leave/" + chatRoomId;

            Disposable disposable = stompClient.send(destination, "{}")
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> {
                        Log.d(TAG, "Left chat room: " + chatRoomId);
                    }, throwable -> {
                        Log.e(TAG, "Error leaving chat room", throwable);
                    });

            compositeDisposable.add(disposable);
        } catch (Exception e) {
            Log.e(TAG, "Error leaving chat room", e);
        }
    }

    public void unsubscribeFromChatRoom(Long chatRoomId) {
        currentChatRoomId = null;
        currentChatRoomTopic = null;
        // Disposables will be automatically disposed when activity is destroyed
    }

    public void sendMessage(Long chatRoomId, String content) {
        if (!isConnected() || currentChatRoomId == null || !currentChatRoomId.equals(chatRoomId)) {
            return;
        }

        // Prevent duplicate sends - ADD THIS
        if (isSendingMessage || content == null || content.trim().isEmpty()) {
            Log.w(TAG, "Cannot send message: already sending or empty content");
            return;
        }

        try {
            isSendingMessage = true;

            Map<String, Object> messageData = new HashMap<>();
            messageData.put("content", content.trim()); // Trim content
            messageData.put("timestamp", System.currentTimeMillis()); // ADD timestamp

            String destination = "/app/chat.sendMessage/" + chatRoomId;
            String jsonData = gson.toJson(messageData);

            Disposable disposable = stompClient.send(destination, jsonData)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> {
                        Log.d(TAG, "Message sent successfully");
                        isSendingMessage = false;
                    }, throwable -> {
                        Log.e(TAG, "Error sending message", throwable);
                        isSendingMessage = false;
                        if (messageListener != null) {
                            messageListener.onError("Failed to send message");
                        }
                    });

            compositeDisposable.add(disposable);
        } catch (Exception e) {
            Log.e(TAG, "Error sending message", e);
            isSendingMessage = false;
            if (messageListener != null) {
                messageListener.onError("Failed to send message");
            }
        }
    }

    // Helper methods for handling different message types
    private void handleChatMessage(StompMessage stompMessage) {
        try {
            ChatMessage message = gson.fromJson(stompMessage.getPayload(), ChatMessage.class);
            if (messageListener != null) {
                messageListener.onMessageReceived(message);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing chat message", e);
        }
    }

    private void handleStatusUpdate(StompMessage stompMessage) {
        try {
            JsonObject jsonObject = gson.fromJson(stompMessage.getPayload(), JsonObject.class);
            Long userId = jsonObject.get("userId").getAsLong();
            boolean online = jsonObject.get("online").getAsBoolean();

            if (messageListener != null) {
                messageListener.onUserStatusChanged(userId, online);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing status update", e);
        }
    }

    private void handleEventMessage(StompMessage stompMessage) {
        try {
            JsonObject jsonObject = gson.fromJson(stompMessage.getPayload(), JsonObject.class);
            String type = jsonObject.get("type").getAsString();
            String username = jsonObject.get("username").getAsString();

            if (messageListener != null) {
                if ("user_joined".equals(type)) {
                    messageListener.onUserJoined(username);
                } else if ("user_left".equals(type)) {
                    messageListener.onUserLeft(username);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing event message", e);
        }
    }
}