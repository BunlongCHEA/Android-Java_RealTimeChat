package com.project.realtimechatui.websocket;

import android.util.Log;
import com.google.gson.Gson;
import com.project.realtimechatui.api.models.ChatMessage;
import com.project.realtimechatui.utils.Constants;
import com.project.realtimechatui.utils.SharedPrefManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.CompletableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.LifecycleEvent;
import ua.naiksoftware.stomp.dto.StompHeader;

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

    public interface ChatMessageListener {
        void onMessageReceived(ChatMessage message);
        void onTypingIndicator(String username, boolean isTyping);
        void onUserStatusChanged(Long userId, boolean isOnline);
        void onMessageEdited(Long messageId, String newContent);
        void onMessageDeleted(Long messageId);
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
    }

    public static synchronized WebSocketChatManager getInstance() {
        if (instance == null) {
            instance = new WebSocketChatManager();
        }
        return instance;
    }

    public void connect() {
        if (isConnected || !sharedPrefManager.isLoggedIn()) {
            Log.w(TAG, "Already connected or user not logged in");
            return;
        }

        try {
            String wsUrl = Constants.WS_BASE_URL; // "ws://10.0.2.2:8080/ws"
            Log.d(TAG, "Connecting to WebSocket: " + wsUrl);

            // Create STOMP client
            stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, wsUrl);

            // Add authorization header
            String token = sharedPrefManager.getAccessToken();
            if (token != null) {
                stompClient.withClientHeartbeat(10000).withServerHeartbeat(10000);

                // Add auth header for STOMP connection
                List<StompHeader> headers = new ArrayList<>();
                headers.add(new StompHeader("Authorization", "Bearer " + token));

                // Connect with headers
                Disposable disposeConnection = stompClient.lifecycle()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::handleLifecycleEvent);

                compositeDisposable.add(disposeConnection);

                // Connect
                stompClient.connect();

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
                Log.e(TAG, "WebSocket error: " + lifecycleEvent.getException());
                isConnected = false;
                if (connectionListener != null) {
                    connectionListener.onError(lifecycleEvent.getException().getMessage());
                }
                break;
        }
    }

    public void joinChatRoom(Long chatRoomId) {
        if (!isConnected || stompClient == null) {
            Log.w(TAG, "Cannot join chat room - not connected");
            return;
        }

        try {
            // Unsubscribe from previous room if any
            if (currentChatRoomTopic != null) {
                // Previous subscription will be automatically disposed
            }

            currentChatRoomTopic = "/topic/chat/" + chatRoomId;
            Log.d(TAG, "Subscribing to: " + currentChatRoomTopic);

            // Subscribe to chat room messages
            Disposable disposable = stompClient.topic(currentChatRoomTopic)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            topicMessage -> {
                                Log.d(TAG, "Received message: " + topicMessage.getPayload());
                                handleIncomingMessage(topicMessage.getPayload());
                            },
                            throwable -> {
                                Log.e(TAG, "Error in chat room subscription", throwable);
                            }
                    );

            compositeDisposable.add(disposable);

            // Subscribe to status updates
            String statusTopic = "/topic/chat/" + chatRoomId + "/status";
            Disposable statusDisposable = stompClient.topic(statusTopic)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            topicMessage -> handleStatusUpdate(topicMessage.getPayload()),
                            throwable -> Log.e(TAG, "Error in status subscription", throwable)
                    );

            compositeDisposable.add(statusDisposable);

            // Subscribe to edit notifications
            String editTopic = "/topic/chat/" + chatRoomId + "/edit";
            Disposable editDisposable = stompClient.topic(editTopic)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            topicMessage -> handleMessageEdit(topicMessage.getPayload()),
                            throwable -> Log.e(TAG, "Error in edit subscription", throwable)
                    );

            compositeDisposable.add(editDisposable);

            // Subscribe to delete notifications
            String deleteTopic = "/topic/chat/" + chatRoomId + "/delete";
            Disposable deleteDisposable = stompClient.topic(deleteTopic)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            topicMessage -> handleMessageDelete(topicMessage.getPayload()),
                            throwable -> Log.e(TAG, "Error in delete subscription", throwable)
                    );

            compositeDisposable.add(deleteDisposable);

        } catch (Exception e) {
            Log.e(TAG, "Error joining chat room", e);
        }
    }

    public void sendMessage(Long chatRoomId, String content, Long senderId) {
        if (!isConnected || stompClient == null) {
            Log.w(TAG, "Cannot send message - not connected");
            return;
        }

        try {
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("content", content);
            messageData.put("type", Constants.MESSAGE_TYPE_TEXT);
            messageData.put("timestamp", System.currentTimeMillis());

            String destination = "/app/chat.sendMessage/" + chatRoomId;
            String jsonMessage = gson.toJson(messageData);

            Log.d(TAG, "Sending message to: " + destination);
            Log.d(TAG, "Message content: " + jsonMessage);

            Disposable disposable = stompClient.send(destination, jsonMessage)
                    .compose(applySchedulers())
                    .subscribe(
                            () -> Log.d(TAG, "Message sent successfully"),
                            throwable -> Log.e(TAG, "Error sending message", throwable)
                    );

            compositeDisposable.add(disposable);

        } catch (Exception e) {
            Log.e(TAG, "Error sending message", e);
        }
    }

    public void sendTypingIndicator(Long chatRoomId, Long userId, boolean isTyping) {
        if (!isConnected || stompClient == null) {
            return;
        }

        try {
            Map<String, Object> typingData = new HashMap<>();
            typingData.put("type", "typing");
            typingData.put("isTyping", isTyping);
            typingData.put("userId", userId);
            typingData.put("username", sharedPrefManager.getUsername());

            String destination = "/app/chat.typing/" + chatRoomId;
            String jsonMessage = gson.toJson(typingData);

            Disposable disposable = stompClient.send(destination, jsonMessage)
                    .compose(applySchedulers())
                    .subscribe(
                            () -> {}, // Success - no action needed
                            throwable -> Log.e(TAG, "Error sending typing indicator", throwable)
                    );

            compositeDisposable.add(disposable);

        } catch (Exception e) {
            Log.e(TAG, "Error sending typing indicator", e);
        }
    }

    private void handleIncomingMessage(String messageJson) {
        try {
            Log.d(TAG, "Parsing incoming message: " + messageJson);

            // Try to parse as ChatMessage first
            ChatMessage chatMessage = gson.fromJson(messageJson, ChatMessage.class);
            if (chatMessage != null && chatMessage.getContent() != null) {
                if (messageListener != null) {
                    messageListener.onMessageReceived(chatMessage);
                }
                return;
            }

            // If not a chat message, try to parse as generic message
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(messageJson, Map.class);
            String type = (String) data.get("type");

            if ("typing".equals(type) && messageListener != null) {
                String username = (String) data.get("username");
                Boolean isTyping = (Boolean) data.get("isTyping");
                messageListener.onTypingIndicator(username, isTyping != null ? isTyping : false);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing incoming message", e);
        }
    }

    private void handleStatusUpdate(String statusJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(statusJson, Map.class);

            Double userIdDouble = (Double) data.get("userId");
            Boolean isOnline = (Boolean) data.get("online");

            if (userIdDouble != null && messageListener != null) {
                Long userId = userIdDouble.longValue();
                messageListener.onUserStatusChanged(userId, isOnline != null ? isOnline : false);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing status update", e);
        }
    }

    private void handleMessageEdit(String editJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(editJson, Map.class);

            Double messageIdDouble = (Double) data.get("messageId");
            String newContent = (String) data.get("content");

            if (messageIdDouble != null && messageListener != null) {
                Long messageId = messageIdDouble.longValue();
                messageListener.onMessageEdited(messageId, newContent);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing message edit", e);
        }
    }

    private void handleMessageDelete(String deleteJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(deleteJson, Map.class);

            Double messageIdDouble = (Double) data.get("messageId");

            if (messageIdDouble != null && messageListener != null) {
                Long messageId = messageIdDouble.longValue();
                messageListener.onMessageDeleted(messageId);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing message delete", e);
        }
    }

    private CompletableTransformer applySchedulers() {
        return upstream -> upstream
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    public void leaveChatRoom(Long chatRoomId) {
        currentChatRoomTopic = null;
        // Subscriptions will be disposed when disconnect is called
    }

    public void disconnect() {
        try {
            isConnected = false;

            if (compositeDisposable != null) {
                compositeDisposable.clear();
            }

            if (stompClient != null) {
                stompClient.disconnect();
                stompClient = null;
            }

            Log.d(TAG, "WebSocket disconnected");

        } catch (Exception e) {
            Log.e(TAG, "Error disconnecting WebSocket", e);
        }
    }

    public void reconnect() {
        disconnect();
        connect();
    }

    public boolean isConnected() {
        return isConnected && stompClient != null && stompClient.isConnected();
    }

    public void setMessageListener(ChatMessageListener listener) {
        this.messageListener = listener;
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }
}