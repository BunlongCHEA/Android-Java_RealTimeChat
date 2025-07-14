
package com.project.realtimechatui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.project.realtimechatui.adapters.ChatMessageAdapter;
import com.project.realtimechatui.api.ApiService;
import com.project.realtimechatui.api.ApiClient;
import com.project.realtimechatui.api.models.BaseDTO;
import com.project.realtimechatui.api.models.ChatMessage;
import com.project.realtimechatui.api.models.ChatRoom;
import com.project.realtimechatui.api.models.Participant;
import com.project.realtimechatui.enums.EnumRoomType;
import com.project.realtimechatui.utils.Constants;
import com.project.realtimechatui.utils.SharedPrefManager;
import com.project.realtimechatui.websocket.WebSocketChatManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity implements
        WebSocketChatManager.ChatMessageListener,
        WebSocketChatManager.ConnectionListener {

    private static final String TAG = "ChatActivity";
    private static final int TYPING_TIMEOUT = 3000; // 3 seconds

    // UI Components
    private TextView tvUserName, tvUserStatus, tvConnectionStatus, tvTypingIndicator;
    private ImageView ivBack, ivUserProfile;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private CardView cvSend;

    // User and Chat Data
    private Long targetUserId;
    private String targetUsername;
    private String targetFullName;
    private String targetProfilePicture;
    private Long chatRoomId;

    // Services and Managers
    private WebSocketChatManager webSocketManager;
    private ApiService apiService;
    private SharedPrefManager sharedPrefManager;
    private ChatMessageAdapter messageAdapter;
    private LinearLayoutManager layoutManager;
    private Handler typingHandler;
    private Set<String> typingUsers;
    private boolean isTyping = false;
    private Runnable stopTypingRunnable;

    // For duplicate prevention
    private boolean isSendingMessage = false;
    private Set<Long> receivedMessageIds = new HashSet<>();
    private String lastSentContent = "";  // ← Already declared here
    private long lastSentTime = 0;        // ← Already declared here

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        sharedPrefManager = SharedPrefManager.getInstance();
        typingHandler = new Handler(Looper.getMainLooper());
        typingUsers = new HashSet<>();

        initViews();
        getIntentData();
        setupRecyclerView();
        setupWebSocket();
        setupApiService();
        setupMessageInput();

        // Check if we need to find existing chat room first
        findExistingChatRoom();
    }

    private void initViews() {
        tvUserName = findViewById(R.id.tvUserName);
        tvUserStatus = findViewById(R.id.tvUserStatus);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvTypingIndicator = findViewById(R.id.tvTypingIndicator);
        ivBack = findViewById(R.id.ivBack);
        ivUserProfile = findViewById(R.id.ivUserProfile);
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        cvSend = findViewById(R.id.cvSend);

        ivBack.setOnClickListener(v -> finish());

        // Initially hide typing indicator
        tvTypingIndicator.setVisibility(View.GONE);

        // Update connection status
        updateConnectionStatus(Constants.CONNECTION_STATE_CONNECTING);
    }

    private void getIntentData() {
        targetUserId = getIntent().getLongExtra("user_id", -1);
        targetUsername = getIntent().getStringExtra("username");
        targetFullName = getIntent().getStringExtra("full_name");
        targetProfilePicture = getIntent().getStringExtra("profile_picture");

        Log.d(TAG, "Target User ID: " + targetUserId);
        Log.d(TAG, "Target Username: " + targetUsername);

        tvUserName.setText(!TextUtils.isEmpty(targetFullName) ? targetFullName : "@" + targetUsername);
        tvUserStatus.setText("Online");
    }

    private void setupRecyclerView() {
        messageAdapter = new ChatMessageAdapter(this);
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // Start from bottom

        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(messageAdapter);

        // Scroll to bottom when new message is added
        messageAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                scrollToBottom();
            }
        });
    }

    private void setupWebSocket() {
        webSocketManager = WebSocketChatManager.getInstance();
        webSocketManager.setMessageListener(this);
        webSocketManager.setConnectionListener(this);

        if (!webSocketManager.isConnected()) {
            webSocketManager.connect();
        }
    }

    private void setupApiService() {
        apiService = ApiClient.getApiService();
    }

    private void setupMessageInput() {
        // Send button click listener
        cvSend.setOnClickListener(v -> sendMessage());

        // Text change listener for typing indicator
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (chatRoomId != null && webSocketManager.isConnected()) {
                    if (s.toString().trim().length() > 0 && !isTyping) {
                        // Start typing
                        isTyping = true;
                        webSocketManager.sendTypingIndicator(chatRoomId, true);
                        scheduleStopTyping();
                    } else if (s.toString().trim().length() == 0 && isTyping) {
                        // Stop typing
                        stopTyping();
                    } else if (isTyping) {
                        // Reset the stop typing timer
                        scheduleStopTyping();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void scheduleStopTyping() {
        // Remove previous runnable
        if (stopTypingRunnable != null) {
            typingHandler.removeCallbacks(stopTypingRunnable);
        }

        // Schedule new runnable
        stopTypingRunnable = this::stopTyping;
        typingHandler.postDelayed(stopTypingRunnable, TYPING_TIMEOUT);
    }

    private void stopTyping() {
        if (isTyping && chatRoomId != null && webSocketManager.isConnected()) {
            isTyping = false;
            webSocketManager.sendTypingIndicator(chatRoomId, false);
        }
        if (stopTypingRunnable != null) {
            typingHandler.removeCallbacks(stopTypingRunnable);
            stopTypingRunnable = null;
        }
    }

    private void findExistingChatRoom() {
        // First, try to find if a personal chat room already exists between current user and target user
        Long currentUserId = sharedPrefManager.getId();

        if (currentUserId == null || targetUserId == null || targetUserId == -1) {
            showError("Invalid user data");
            return;
        }

        // Get all chat rooms and find personal chat with target user
        Call<BaseDTO<List<ChatRoom>>> call = apiService.getAllChatRooms();
        call.enqueue(new Callback<BaseDTO<List<ChatRoom>>>() {
            @Override
            public void onResponse(Call<BaseDTO<List<ChatRoom>>> call, Response<BaseDTO<List<ChatRoom>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<List<ChatRoom>> result = response.body();
                    if (result.isSuccess() && result.getData() != null) {
                        ChatRoom existingRoom = findPersonalChatRoom(result.getData(), currentUserId, targetUserId);
                        if (existingRoom != null) {
                            // Found existing chat room
                            chatRoomId = existingRoom.getId();
                            Log.d(TAG, "Found existing chat room: " + chatRoomId);
                            joinChatRoom();
                            loadChatMessages();
                        } else {
                            // No existing chat room, create new one
                            createPersonalChatRoom();
                        }
                    }
                } else {
                    createPersonalChatRoom(); // Fallback to creating new room
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<List<ChatRoom>>> call, Throwable t) {
                Log.e(TAG, "Failed to get chat rooms", t);
                createPersonalChatRoom(); // Fallback to creating new room
            }
        });
    }

    private ChatRoom findPersonalChatRoom(List<ChatRoom> chatRooms, Long currentUserId, Long targetUserId) {
        for (ChatRoom room : chatRooms) {
            if (room.getType() == EnumRoomType.PERSONAL && room.getParticipants() != null) {
                Set<Long> participantIds = new HashSet<>();
                for (Participant participant : room.getParticipants()) {
                    // Fixed: Add null checks for participant and user
                    if (participant != null && participant.getUserId() != null) {
                        participantIds.add(participant.getUserId());
                    } else {
                        Log.w(TAG, "Participant or User is null in chat room: " + room.getId());
                        // Skip this participant if data is incomplete
                        continue;
                    }
                }

                // Check if this room contains exactly our two users
                if (participantIds.size() == 2 &&
                        participantIds.contains(currentUserId) &&
                        participantIds.contains(targetUserId)) {
                    return room;
                }
            }
        }
        return null;
    }

    private void createPersonalChatRoom() {
        Long currentUserId = sharedPrefManager.getId();

        if (currentUserId == null) {
            showError("User not logged in");
            return;
        }

        // Create ChatRoom DTO for personal chat
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setType(EnumRoomType.PERSONAL);
        chatRoom.setName(targetUsername); // Backend will handle the name

        // Add target user as participant
        Set<Participant> participants = new HashSet<>();
        Participant targetParticipant = new Participant();
        targetParticipant.setUserId(targetUserId);  // ONLY set userId - remove all other fields
        participants.add(targetParticipant);
        chatRoom.setParticipants(participants);

        Log.d(TAG, "Creating personal chat room with user: " + targetUserId);
        Log.d(TAG, "Sending ChatRoomDTO: " + new Gson().toJson(chatRoom)); // Add this for debugging

        Call<BaseDTO<ChatRoom>> call = apiService.createChatRoom(chatRoom, currentUserId);
        call.enqueue(new Callback<BaseDTO<ChatRoom>>() {
            @Override
            public void onResponse(Call<BaseDTO<ChatRoom>> call, Response<BaseDTO<ChatRoom>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<ChatRoom> result = response.body();
                    if (result.isSuccess() && result.getData() != null) {
                        chatRoomId = result.getData().getId();
                        Log.d(TAG, "Created chat room: " + chatRoomId);
                        joinChatRoom();
                        loadChatMessages();
                    } else {
                        showError("Failed to create chat room: " + result.getMessage());
                    }
                } else {
                    if (response.code() == 400 && response.errorBody() != null) {
                        try {
                            String errorBody = response.errorBody().string();
                            if (errorBody.contains("personal chat already exists")) {
                                // Chat room already exists, try to find it again
                                findExistingChatRoom();
                                return;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing error response", e);
                        }
                    }
                    showError("Failed to create chat room");
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<ChatRoom>> call, Throwable t) {
                Log.e(TAG, "Failed to create chat room", t);
                showError("Network error: " + t.getMessage());
            }
        });
    }

    private void joinChatRoom() {
        if (chatRoomId != null && webSocketManager.isConnected()) {
            webSocketManager.subscribeToChatRoom(chatRoomId);
            webSocketManager.joinChatRoom(chatRoomId);
            updateConnectionStatus(Constants.CONNECTION_STATE_CONNECTED);
        }
    }

    private void loadChatMessages() {
        if (chatRoomId == null) return;

        Call<BaseDTO<List<ChatMessage>>> call = apiService.getMessagesByChatRoom(chatRoomId, 0, 50);
        call.enqueue(new Callback<BaseDTO<List<ChatMessage>>>() {
            @Override
            public void onResponse(Call<BaseDTO<List<ChatMessage>>> call, Response<BaseDTO<List<ChatMessage>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<List<ChatMessage>> result = response.body();
                    if (result.isSuccess() && result.getData() != null) {
                        List<ChatMessage> messages = result.getData();

                        // Sort messages by timestamp (oldest first, latest at bottom) - ADD THIS
                        Collections.sort(messages, new Comparator<ChatMessage>() {
                            @Override
                            public int compare(ChatMessage m1, ChatMessage m2) {
                                try {
                                    // Parse timestamps and compare
                                    long time1 = parseTimestamp(m1.getTimestamp());
                                    long time2 = parseTimestamp(m2.getTimestamp());
                                    return Long.compare(time1, time2); // Ascending order (oldest first)
                                } catch (Exception e) {
                                    return 0;
                                }
                            }
                        });

                        messageAdapter.setMessages(messages);
                        scrollToBottom();
                    }
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<List<ChatMessage>>> call, Throwable t) {
                Log.e(TAG, "Failed to load messages", t);
            }
        });
    }

    private long parseTimestamp(String timestamp) {
        try {
            if (TextUtils.isEmpty(timestamp)) {
                return 0;
            }

            // If timestamp is already in milliseconds (13 digits)
            if (timestamp.matches("\\d{13}")) {
                return Long.parseLong(timestamp);
            }

            // If timestamp is in seconds (10 digits), convert to milliseconds
            if (timestamp.matches("\\d{10}")) {
                return Long.parseLong(timestamp) * 1000;
            }

            // If timestamp is in ISO format, parse it
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = sdf.parse(timestamp);
            return date != null ? date.getTime() : System.currentTimeMillis();

        } catch (Exception e) {
            Log.e(TAG, "Error parsing timestamp: " + timestamp, e);
            return System.currentTimeMillis();
        }
    }

    private void sendMessage() {
        String messageText = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(messageText) || chatRoomId == null) {
            return;
        }

        if (!webSocketManager.isConnected()) {
            showError("Not connected to chat server");
            return;
        }

        // Prevent duplicate sends - ADD THIS BLOCK
        if (isSendingMessage) {
            return;
        }

        // Prevent sending same message within 2 seconds - ADD THIS BLOCK
        long currentTime = System.currentTimeMillis();
        if (messageText.equals(lastSentContent) && (currentTime - lastSentTime) < 2000) {
            Log.d(TAG, "Preventing duplicate message send");
            return;
        }

        // Set sending state - ADD THIS
        isSendingMessage = true;
        lastSentContent = messageText;
        lastSentTime = currentTime;

        // Stop typing indicator
        stopTyping();

        // Send message via WebSocket
        webSocketManager.sendMessage(chatRoomId, messageText);

        // Clear the input
        etMessage.setText("");

        // Reset sending state after a delay - ADD THIS
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isSendingMessage = false;
        }, 1000);
    }

    private void scrollToBottom() {
        if (messageAdapter.getItemCount() > 0) {
            rvMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
        }
    }

    private void updateConnectionStatus(String status) {
        tvConnectionStatus.setText(status);
        switch (status) {
            case Constants.CONNECTION_STATE_CONNECTED:
                tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                break;
            case Constants.CONNECTION_STATE_CONNECTING:
                tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                break;
            case Constants.CONNECTION_STATE_DISCONNECTED:
                tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                break;
        }
    }

    private void updateTypingIndicator() {
        if (typingUsers.isEmpty()) {
            tvTypingIndicator.setVisibility(View.GONE);
        } else {
            tvTypingIndicator.setVisibility(View.VISIBLE);
            if (typingUsers.size() == 1) {
                tvTypingIndicator.setText(typingUsers.iterator().next() + " is typing...");
            } else {
                tvTypingIndicator.setText("Multiple users are typing...");
            }
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // WebSocketChatManager.ChatMessageListener implementation
    @Override
    public void onMessageReceived(ChatMessage message) {
        // Prevent duplicate messages - ADD THIS BLOCK
        if (message.getId() != null && receivedMessageIds.contains(message.getId())) {
            Log.d(TAG, "Duplicate message received, ignoring: " + message.getId());
            return;
        }

        // Add to received set - ADD THIS BLOCK
        if (message.getId() != null) {
            receivedMessageIds.add(message.getId());

            // Keep only last 1000 message IDs to prevent memory issues
            if (receivedMessageIds.size() > 1000) {
                Iterator<Long> iterator = receivedMessageIds.iterator();
                for (int i = 0; i < 500 && iterator.hasNext(); i++) {
                    iterator.next();
                    iterator.remove();
                }
            }
        }

        runOnUiThread(() -> {
            messageAdapter.addMessage(message);
            scrollToBottom();
        });
    }

    @Override
    public void onTypingIndicator(String username, boolean isTyping) {
        runOnUiThread(() -> {
            if (isTyping) {
                typingUsers.add(username);
            } else {
                typingUsers.remove(username);
            }
            updateTypingIndicator();
        });
    }

    @Override
    public void onUserStatusChanged(Long userId, boolean isOnline) {
        runOnUiThread(() -> {
            if (userId.equals(targetUserId)) {
                tvUserStatus.setText(isOnline ? "Online" : "Offline");
            }
        });
    }

    @Override
    public void onMessageEdited(Long messageId, String newContent) {
        runOnUiThread(() -> messageAdapter.updateMessage(messageId, newContent));
    }

    @Override
    public void onMessageDeleted(Long messageId) {
        runOnUiThread(() -> messageAdapter.removeMessage(messageId));
    }

    @Override
    public void onUserJoined(String username) {
        runOnUiThread(() -> {
            // Handle user joined event if needed
            Log.d(TAG, "User joined: " + username);
        });
    }

    @Override
    public void onUserLeft(String username) {
        runOnUiThread(() -> {
            // Handle user left event if needed
            Log.d(TAG, "User left: " + username);
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> showError("Chat error: " + error));
    }

    // WebSocketChatManager.ConnectionListener implementation
    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            updateConnectionStatus(Constants.CONNECTION_STATE_CONNECTED);
            if (chatRoomId != null) {
                joinChatRoom();
            }
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            updateConnectionStatus(Constants.CONNECTION_STATE_DISCONNECTED);
            // Stop typing indicator
            stopTyping();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!webSocketManager.isConnected()) {
            webSocketManager.connect();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop typing indicator when leaving
        stopTyping();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up typing handler
        if (stopTypingRunnable != null) {
            typingHandler.removeCallbacks(stopTypingRunnable);
        }

        // Leave chat room
        if (chatRoomId != null && webSocketManager.isConnected()) {
            webSocketManager.leaveChatRoom(chatRoomId);
            webSocketManager.unsubscribeFromChatRoom(chatRoomId);
        }
    }
}
