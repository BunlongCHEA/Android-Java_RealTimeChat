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

import com.project.realtimechatui.adapters.ChatMessageAdapter;
import com.project.realtimechatui.api.ApiClient;
import com.project.realtimechatui.api.ApiService;
import com.project.realtimechatui.api.models.BaseResponse;
import com.project.realtimechatui.api.models.ChatMessage;
import com.project.realtimechatui.api.models.ChatRoom;
import com.project.realtimechatui.api.models.CreateChatRoomRequest;
import com.project.realtimechatui.utils.Constants;
import com.project.realtimechatui.utils.SharedPrefManager;
import com.project.realtimechatui.websocket.WebSocketChatManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity implements
        WebSocketChatManager.ChatMessageListener,
        WebSocketChatManager.ConnectionListener {

    private static final String TAG = "ChatActivity";

    private TextView tvUserName, tvUserStatus, tvConnectionStatus, tvTypingIndicator;
    private ImageView ivBack, ivUserProfile;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private CardView cvSend;

    private Long targetUserId;
    private String targetUsername;
    private String targetFullName;
    private String targetProfilePicture;
    private Long chatRoomId;

    private WebSocketChatManager webSocketManager;
    private ApiService apiService;
    private SharedPrefManager sharedPrefManager;
    private ChatMessageAdapter messageAdapter;
    private LinearLayoutManager layoutManager;
    private Handler typingHandler;
    private Set<String> typingUsers;
    private boolean isTyping = false;

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
        cvSend.setOnClickListener(v -> {
            String messageText = etMessage.getText().toString().trim();
            if (!messageText.isEmpty()) {
                sendMessage(messageText);
                etMessage.setText("");
            }
        });

        // Typing indicator
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isTyping && s.length() > 0) {
                    isTyping = true;
                    webSocketManager.sendTypingIndicator(true);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 0 && isTyping) {
                    isTyping = false;
                    webSocketManager.sendTypingIndicator(false);
                }
            }
        });
    }

    private void findExistingChatRoom() {
        if (targetUserId == null || targetUserId == -1) {
            Log.e(TAG, "Invalid target user ID");
            showError("Invalid user selected");
            return;
        }

        List<Long> participantIds = new ArrayList<>();
        participantIds.add(sharedPrefManager.getUserId());
        participantIds.add(targetUserId);

        Call<BaseResponse<ChatRoom>> call = apiService.findChatRoomByParticipants(participantIds);
        call.enqueue(new Callback<BaseResponse<ChatRoom>>() {
            @Override
            public void onResponse(Call<BaseResponse<ChatRoom>> call, Response<BaseResponse<ChatRoom>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseResponse<ChatRoom> baseResponse = response.body();
                    if (baseResponse.getStatus() == 200 && baseResponse.getData() != null) {
                        // Chat room exists
                        chatRoomId = baseResponse.getData().getId();
                        Log.d(TAG, "Found existing chat room: " + chatRoomId);
                        joinChatRoom();
                        loadChatHistory();
                    } else {
                        // Chat room doesn't exist, create new one
                        Log.d(TAG, "Chat room not found, creating new one");
                        createNewChatRoom();
                    }
                } else {
                    Log.e(TAG, "Error finding chat room: " + response.message());
                    createNewChatRoom();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse<ChatRoom>> call, Throwable t) {
                Log.e(TAG, "Network error finding chat room", t);
                createNewChatRoom();
            }
        });
    }

    private void createNewChatRoom() {
        CreateChatRoomRequest request = new CreateChatRoomRequest();
        request.setName("Chat with " + targetUsername);
        request.setType(Constants.ROOM_TYPE_PERSONAL);
        request.setDescription("Personal chat room");

        List<Long> participantIds = new ArrayList<>();
        participantIds.add(sharedPrefManager.getUserId());
        participantIds.add(targetUserId);
        request.setParticipantIds(participantIds);

        Call<BaseResponse<ChatRoom>> call = apiService.createChatRoom(request);
        call.enqueue(new Callback<BaseResponse<ChatRoom>>() {
            @Override
            public void onResponse(Call<BaseResponse<ChatRoom>> call, Response<BaseResponse<ChatRoom>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseResponse<ChatRoom> baseResponse = response.body();
                    if (baseResponse.getStatus() == 201 && baseResponse.getData() != null) {
                        chatRoomId = baseResponse.getData().getId();
                        Log.d(TAG, "Created new chat room: " + chatRoomId);
                        joinChatRoom();
                    } else {
                        Log.e(TAG, "Error creating chat room: " + baseResponse.getMessage());
                        showError("Failed to create chat room");
                    }
                } else {
                    Log.e(TAG, "Error creating chat room: " + response.message());
                    showError("Failed to create chat room");
                }
            }

            @Override
            public void onFailure(Call<BaseResponse<ChatRoom>> call, Throwable t) {
                Log.e(TAG, "Network error creating chat room", t);
                showError("Network error creating chat room");
            }
        });
    }

    private void joinChatRoom() {
        if (chatRoomId != null && webSocketManager.isConnected()) {
            webSocketManager.joinChatRoom(chatRoomId);
            updateConnectionStatus(Constants.CONNECTION_STATE_CONNECTED);
        } else {
            Log.w(TAG, "Cannot join chat room: chatRoomId=" + chatRoomId +
                    ", connected=" + webSocketManager.isConnected());
        }
    }

    private void loadChatHistory() {
        if (chatRoomId == null) return;

        Call<BaseResponse<Page<ChatMessage>>> call = apiService.getMessagesByChatRoomId(chatRoomId, 0, 50);
        call.enqueue(new Callback<BaseResponse<Page<ChatMessage>>>() {
            @Override
            public void onResponse(Call<BaseResponse<Page<ChatMessage>>> call,
                                   Response<BaseResponse<Page<ChatMessage>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseResponse<Page<ChatMessage>> baseResponse = response.body();
                    if (baseResponse.getStatus() == 200 && baseResponse.getData() != null) {
                        List<ChatMessage> messages = baseResponse.getData().getContent();
                        runOnUiThread(() -> {
                            messageAdapter.updateMessages(messages);
                            scrollToBottom();
                        });
                        Log.d(TAG, "Loaded " + messages.size() + " messages");
                    }
                } else {
                    Log.e(TAG, "Error loading chat history: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<BaseResponse<Page<ChatMessage>>> call, Throwable t) {
                Log.e(TAG, "Network error loading chat history", t);
            }
        });
    }

    private void sendMessage(String content) {
        if (webSocketManager.isConnected() && chatRoomId != null) {
            webSocketManager.sendMessage(content);
        } else {
            showError("Not connected to chat");
        }
    }

    private void updateConnectionStatus(String status) {
        runOnUiThread(() -> {
            switch (status) {
                case Constants.CONNECTION_STATE_CONNECTING:
                    tvConnectionStatus.setText("Connecting...");
                    tvConnectionStatus.setTextColor(getColor(android.R.color.holo_orange_light));
                    break;
                case Constants.CONNECTION_STATE_CONNECTED:
                    tvConnectionStatus.setText("Connected");
                    tvConnectionStatus.setTextColor(getColor(android.R.color.holo_green_light));
                    break;
                case Constants.CONNECTION_STATE_DISCONNECTED:
                    tvConnectionStatus.setText("Disconnected");
                    tvConnectionStatus.setTextColor(getColor(android.R.color.holo_red_light));
                    break;
                case Constants.CONNECTION_STATE_ERROR:
                    tvConnectionStatus.setText("Connection Error");
                    tvConnectionStatus.setTextColor(getColor(android.R.color.holo_red_light));
                    break;
            }
        });
    }

    private void updateTypingIndicator() {
        runOnUiThread(() -> {
            if (typingUsers.isEmpty()) {
                tvTypingIndicator.setVisibility(View.GONE);
            } else {
                String typingText;
                if (typingUsers.size() == 1) {
                    typingText = typingUsers.iterator().next() + " is typing...";
                } else if (typingUsers.size() == 2) {
                    typingText = String.join(" and ", typingUsers) + " are typing...";
                } else {
                    typingText = "Several people are typing...";
                }
                tvTypingIndicator.setText(typingText);
                tvTypingIndicator.setVisibility(View.VISIBLE);
            }
        });
    }

    private void scrollToBottom() {
        if (messageAdapter.getItemCount() > 0) {
            rvMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    // WebSocket Message Listener Methods
    @Override
    public void onMessageReceived(ChatMessage message) {
        runOnUiThread(() -> {
            messageAdapter.addMessage(message);
            scrollToBottom();
        });
    }

    @Override
    public void onTypingIndicator(String username, boolean isTyping) {
        if (isTyping) {
            typingUsers.add(username);
        } else {
            typingUsers.remove(username);
        }
        updateTypingIndicator();
    }

    @Override
    public void onUserStatusChanged(Long userId, boolean isOnline) {
        // Update user status if needed
        if (userId.equals(targetUserId)) {
            runOnUiThread(() -> tvUserStatus.setText(isOnline ? "Online" : "Offline"));
        }
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
        showError(username + " joined the chat");
    }

    @Override
    public void onUserLeft(String username) {
        showError(username + " left the chat");
    }

    // WebSocket Connection Listener Methods
    @Override
    public void onConnected() {
        Log.d(TAG, "WebSocket connected");
        updateConnectionStatus(Constants.CONNECTION_STATE_CONNECTED);

        // Join chat room if we have one
        if (chatRoomId != null) {
            joinChatRoom();
        }
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "WebSocket disconnected");
        updateConnectionStatus(Constants.CONNECTION_STATE_DISCONNECTED);
    }

    @Override
    public void onError(String error) {
        Log.e(TAG, "WebSocket error: " + error);
        updateConnectionStatus(Constants.CONNECTION_STATE_ERROR);
        showError("Connection error: " + error);
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
        // Stop typing indicator
        if (isTyping) {
            isTyping = false;
            webSocketManager.sendTypingIndicator(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Leave chat room and disconnect
        if (webSocketManager != null) {
            webSocketManager.leaveChatRoom();
        }

        // Clear typing handler
        if (typingHandler != null && typingStopRunnable != null) {
            typingHandler.removeCallbacks(typingStopRunnable);
        }

        // Note: Don't disconnect WebSocket here as it might be used by other activities
        // webSocketManager.disconnect();
    }
}