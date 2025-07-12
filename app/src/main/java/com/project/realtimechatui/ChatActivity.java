package com.project.realtimechatui;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.project.realtimechatui.adapters.ChatMessageAdapter;
import com.project.realtimechatui.api.ApiClient;
import com.project.realtimechatui.api.ApiService;
import com.project.realtimechatui.api.models.BaseDTO;
import com.project.realtimechatui.api.models.ChatMessage;
import com.project.realtimechatui.api.models.ChatRoom;
import com.project.realtimechatui.api.models.Participant;
import com.project.realtimechatui.utils.Constants;
import com.project.realtimechatui.utils.SharedPrefManager;
import com.project.realtimechatui.websocket.WebSocketChatManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity implements
        WebSocketChatManager.ChatMessageListener,
        WebSocketChatManager.ConnectionListener {

    private static final String TAG = "ChatActivity";

    private TextView tvUserName, tvUserStatus, tvConnectionStatus;
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
        ivBack = findViewById(R.id.ivBack);
        ivUserProfile = findViewById(R.id.ivUserProfile);
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        cvSend = findViewById(R.id.cvSend);

        ivBack.setOnClickListener(v -> finish());
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
        webSocketManager.connect();
    }

    private void setupApiService() {
        apiService = ApiClient.getApiService();
    }

    private void setupMessageInput() {
        cvSend.setOnClickListener(v -> sendMessage());

        // Handle Enter key to send message
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (chatRoomId != null) {
                    boolean isTyping = s.length() > 0;
                    webSocketManager.sendTypingIndicator(chatRoomId, sharedPrefManager.getId(), isTyping);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void scrollToBottom() {
        if (messageAdapter.getItemCount() > 0) {
            rvMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
        }
    }

    // Try to find existing chat room first before creating new one
    private void findExistingChatRoom() {
        Log.d(TAG, "Looking for existing chat rooms...");

        Call<BaseDTO<List<ChatRoom>>> call = apiService.getChatRooms();
        call.enqueue(new Callback<BaseDTO<List<ChatRoom>>>() {
            @Override
            public void onResponse(Call<BaseDTO<List<ChatRoom>>> call, Response<BaseDTO<List<ChatRoom>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<List<ChatRoom>> result = response.body();
                    if (result.isSuccess() && result.getData() != null) {

                        // Look for existing personal chat with this user
                        ChatRoom existingRoom = findPersonalChatRoom(result.getData());

                        if (existingRoom != null) {
                            Log.d(TAG, "Found existing chat room: " + existingRoom.getId());
                            chatRoomId = existingRoom.getId();
                            joinWebSocketRoom();
                            loadMessages();
                        } else {
                            Log.d(TAG, "No existing chat room found, creating new one...");
                            createOrGetChatRoom();
                        }
                    } else {
                        Log.e(TAG, "Failed to get chat rooms: " + result.getMessage());
                        createOrGetChatRoom();
                    }
                } else {
                    Log.e(TAG, "Failed to get chat rooms. Code: " + response.code());
                    if (response.code() == 401) {
                        handleUnauthorized();
                    } else {
                        createOrGetChatRoom();
                    }
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<List<ChatRoom>>> call, Throwable t) {
                Log.e(TAG, "Network error getting chat rooms: " + t.getMessage());
                createOrGetChatRoom();
            }
        });
    }

    private ChatRoom findPersonalChatRoom(List<ChatRoom> chatRooms) {
        Long currentUserId = sharedPrefManager.getId();

        for (ChatRoom room : chatRooms) {
            if (Constants.ROOM_TYPE_PERSONAL.equals(room.getType()) && room.getParticipants() != null) {
                // Check if this personal chat contains both current user and target user
                boolean hasCurrentUser = false;
                boolean hasTargetUser = false;

                for (Participant participant : room.getParticipants()) {
                    if (participant.getUserId().equals(currentUserId)) {
                        hasCurrentUser = true;
                    }
                    if (participant.getUserId().equals(targetUserId)) {
                        hasTargetUser = true;
                    }
                }

                if (hasCurrentUser && hasTargetUser) {
                    return room;
                }
            }
        }
        return null;
    }

    private void createOrGetChatRoom() {
        Log.d(TAG, "Creating new personal chat room...");

        // Create a chat room with the selected user
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setName("Chat with " + targetUsername);
        chatRoom.setType(Constants.ROOM_TYPE_PERSONAL);

        // Create participants set with the target user
        Set<Participant> participants = new HashSet<>();
        Participant targetParticipant = new Participant(targetUserId, "MEMBER");
        participants.add(targetParticipant);
        chatRoom.setParticipants(participants);

        Long currentUserId = sharedPrefManager.getId();
        Log.d(TAG, "Current User ID: " + currentUserId);
        Log.d(TAG, "Target User ID: " + targetUserId);
        Log.d(TAG, "Creating room with name: " + chatRoom.getName());
        Log.d(TAG, "Room type: " + chatRoom.getType());

        Call<BaseDTO<ChatRoom>> call = apiService.createChatRoom(chatRoom, currentUserId);
        call.enqueue(new Callback<BaseDTO<ChatRoom>>() {
            @Override
            public void onResponse(Call<BaseDTO<ChatRoom>> call, Response<BaseDTO<ChatRoom>> response) {
                Log.d(TAG, "Create chat room response code: " + response.code());

                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<ChatRoom> result = response.body();
                    if (result.isSuccess() && result.getData() != null) {
                        chatRoomId = result.getData().getId();
                        Log.d(TAG, "Chat room created successfully with ID: " + chatRoomId);
                        joinWebSocketRoom();
                        loadMessages();
                        showSuccess("Chat started!");
                    } else {
                        Log.e(TAG, "Failed to create chat room: " + result.getMessage());
                        showError("Failed to create chat room: " + result.getMessage());
                    }
                } else {
                    Log.e(TAG, "Failed to create chat room. Response code: " + response.code());

                    if (response.code() == 401) {
                        handleUnauthorized();
                    } else if (response.code() == 403) {
                        showError("Permission denied. Please check your authentication.");
                        Log.e(TAG, "403 Forbidden - Token might be invalid or expired");
                    } else if (response.code() == 400) {
                        showError("Bad request. Please check the data being sent.");
                        Log.e(TAG, "400 Bad Request - Check request parameters");
                    } else {
                        showError("Failed to create chat room. Error: " + response.code());
                    }
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<ChatRoom>> call, Throwable t) {
                Log.e(TAG, "Network error creating chat room: " + t.getMessage());
                showError("Failed to create chat room: " + t.getMessage());
            }
        });
    }

    private void joinWebSocketRoom() {
        if (chatRoomId != null && webSocketManager.isConnected()) {
            webSocketManager.joinChatRoom(chatRoomId);
        }
    }

    private void loadMessages() {
        if (chatRoomId != null) {
            Log.d(TAG, "Loading messages for room: " + chatRoomId);

            Call<BaseDTO<List<ChatMessage>>> call = apiService.getChatMessages(chatRoomId);
            call.enqueue(new Callback<BaseDTO<List<ChatMessage>>>() {
                @Override
                public void onResponse(Call<BaseDTO<List<ChatMessage>>> call, Response<BaseDTO<List<ChatMessage>>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        BaseDTO<List<ChatMessage>> result = response.body();
                        if (result.isSuccess() && result.getData() != null) {
                            Log.d(TAG, "Loaded " + result.getData().size() + " messages");
                            messageAdapter.setMessages(result.getData());
                            scrollToBottom();
                        } else {
                            Log.e(TAG, "Failed to load messages: " + result.getMessage());
                        }
                    } else if (response.code() == 401) {
                        handleUnauthorized();
                    } else {
                        Log.e(TAG, "Failed to load messages. Code: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<BaseDTO<List<ChatMessage>>> call, Throwable t) {
                    Log.e(TAG, "Failed to load messages: " + t.getMessage());
                    showError("Failed to load messages: " + t.getMessage());
                }
            });
        }
    }

    private void sendMessage() {
        String content = etMessage.getText().toString().trim();
        if (!TextUtils.isEmpty(content) && chatRoomId != null) {
            Log.d(TAG, "Sending message: " + content);

            // Send via WebSocket for real-time delivery
            webSocketManager.sendMessage(chatRoomId, content, sharedPrefManager.getId());

            // Also send via API for persistence
//            Call<BaseDTO<ChatMessage>> call = apiService.sendMessage(chatRoomId, sharedPrefManager.getId(), content);
//            call.enqueue(new Callback<BaseDTO<ChatMessage>>() {
//                @Override
//                public void onResponse(Call<BaseDTO<ChatMessage>> call, Response<BaseDTO<ChatMessage>> response) {
//                    if (response.isSuccessful() && response.body() != null) {
//                        BaseDTO<ChatMessage> result = response.body();
//                        if (result.isSuccess()) {
//                            Log.d(TAG, "Message sent successfully");
//                            // Add message to local list if not already added via WebSocket
//                            if (result.getData() != null) {
//                                messageAdapter.addMessage(result.getData());
//                            }
//                        } else {
//                            Log.e(TAG, "Failed to send message: " + result.getMessage());
//                            showError("Failed to send message: " + result.getMessage());
//                        }
//                    } else if (response.code() == 401) {
//                        handleUnauthorized();
//                    } else {
//                        Log.e(TAG, "Failed to send message. Code: " + response.code());
//                        showError("Failed to send message. Error: " + response.code());
//                    }
//                }
//
//                @Override
//                public void onFailure(Call<BaseDTO<ChatMessage>> call, Throwable t) {
//                    Log.e(TAG, "Failed to send message: " + t.getMessage());
//                    showError("Failed to send message: " + t.getMessage());
//                }
//            });

            etMessage.setText("");
        } else if (chatRoomId == null) {
            showError("Chat room not ready. Please wait...");
        }
    }

    @Override
    public void onMessageReceived(ChatMessage message) {
        runOnUiThread(() -> {
            if (message.getChatRoomId().equals(chatRoomId)) {
                Log.d(TAG, "Received message via WebSocket: " + message.getContent());
                messageAdapter.addMessage(message);
            }
        });
    }

    @Override
    public void onMessageEdited(Long messageId, String newContent) {
        runOnUiThread(() -> {
            // Update the message in your adapter
            if (messageAdapter != null) {
                ChatMessage editedMessage = new ChatMessage();
                editedMessage.setId(messageId);
                editedMessage.setContent(newContent);
                editedMessage.setEdited(true);
                messageAdapter.updateMessage(editedMessage);
            }
        });
    }

    @Override
    public void onMessageDeleted(Long messageId) {
        runOnUiThread(() -> {
            // Remove the message from your adapter
            if (messageAdapter != null) {
                messageAdapter.removeMessage(messageId);
            }
        });
    }

    @Override
    public void onTypingIndicator(String username, boolean isTyping) {
        if (!username.equals(sharedPrefManager.getUsername())) {
            runOnUiThread(() -> {
                if (isTyping) {
                    tvUserStatus.setText("Typing...");
                } else {
                    tvUserStatus.setText("Online");
                }
            });
        }
    }

    @Override
    public void onUserStatusChanged(Long userId, boolean isOnline) {
        if (userId.equals(targetUserId)) {
            runOnUiThread(() -> {
                tvUserStatus.setText(isOnline ? "Online" : "Offline");
            });
        }
    }

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            tvConnectionStatus.setVisibility(View.GONE);
            joinWebSocketRoom();
            Log.d(TAG, "WebSocket connected");
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            tvConnectionStatus.setText("Disconnected");
            tvConnectionStatus.setVisibility(View.VISIBLE);
            Log.d(TAG, "WebSocket disconnected");
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            tvConnectionStatus.setText("Connection Error");
            tvConnectionStatus.setVisibility(View.VISIBLE);
            Log.e(TAG, "WebSocket error: " + error);
        });
    }

    private void handleUnauthorized() {
        Log.e(TAG, "Unauthorized - clearing auth data");
        sharedPrefManager.clearAuthData();
        finish();
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showSuccess(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatRoomId != null && webSocketManager != null) {
            webSocketManager.leaveChatRoom(chatRoomId);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reconnect WebSocket if needed
        if (webSocketManager != null && !webSocketManager.isConnected()) {
            webSocketManager.connect();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Send typing stopped indicator when leaving
        if (chatRoomId != null && webSocketManager != null) {
            webSocketManager.sendTypingIndicator(chatRoomId, sharedPrefManager.getId(), false);
        }
    }
}