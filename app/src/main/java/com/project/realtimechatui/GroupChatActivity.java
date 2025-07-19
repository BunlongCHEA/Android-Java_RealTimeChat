package com.project.realtimechatui;

import static com.project.realtimechatui.utils.Constants.TYPING_TIMEOUT;

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
import com.project.realtimechatui.adapters.UserListAdapter;
import com.project.realtimechatui.api.ApiClient;
import com.project.realtimechatui.api.ApiService;
import com.project.realtimechatui.api.models.BaseDTO;
import com.project.realtimechatui.api.models.ChatMessage;
import com.project.realtimechatui.api.models.ChatRoom;
import com.project.realtimechatui.api.models.Participant;
import com.project.realtimechatui.enums.EnumRoomType;
import com.project.realtimechatui.utils.Constants;
import com.project.realtimechatui.utils.SharedPrefManager;
import com.project.realtimechatui.websocket.WebSocketChatManager;

import java.text.SimpleDateFormat;
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

public class GroupChatActivity extends AppCompatActivity implements
        WebSocketChatManager.ChatMessageListener,
        WebSocketChatManager.ConnectionListener {

    private static final String TAG = "GroupChatActivity";

    // UI Components
    private TextView tvGroupName, tvMemberCount, tvConnectionStatus, tvTypingIndicator;
    private ImageView ivBack, ivGroupProfile, ivGroupInfo;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private CardView cvSend;

    // Group Data
    private ChatRoom chatRoom;
    private Long chatRoomId;
    private boolean isNewGroup;

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
    private String lastSentContent = "";
    private long lastSentTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_group_chat);
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

        if (isNewGroup) {
            // For new groups, join immediately
            joinChatRoom();
        } else {
            // For existing groups, load messages
            loadChatMessages();
            joinChatRoom();
        }
    }

    private void initViews() {
        tvGroupName = findViewById(R.id.tvGroupName);
        tvMemberCount = findViewById(R.id.tvMemberCount);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvTypingIndicator = findViewById(R.id.tvTypingIndicator);
        ivBack = findViewById(R.id.ivBack);
        ivGroupProfile = findViewById(R.id.ivGroupProfile);
        ivGroupInfo = findViewById(R.id.ivGroupInfo);
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        cvSend = findViewById(R.id.cvSend);

        ivBack.setOnClickListener(v -> finish());

        // Group info click listener
        ivGroupInfo.setOnClickListener(v -> {
            // TODO: Open group info activity
            Toast.makeText(this, "Group info coming soon", Toast.LENGTH_SHORT).show();
        });

        // Initially hide typing indicator
        tvTypingIndicator.setVisibility(View.GONE);

        // Update connection status
        updateConnectionStatus(Constants.CONNECTION_STATE_CONNECTING);
    }

    private void getIntentData() {
        chatRoom = (ChatRoom) getIntent().getSerializableExtra("chat_room");
        isNewGroup = getIntent().getBooleanExtra("is_new_group", false);

        if (chatRoom != null) {
            chatRoomId = chatRoom.getId();

            // Set group name
            String groupName = chatRoom.getName();
            if (groupName == null || groupName.isEmpty()) {
                groupName = "Group Chat";
            }
            tvGroupName.setText(groupName);

            // Set member count
            int memberCount = chatRoom.getParticipants() != null ? chatRoom.getParticipants().size() : 0;
            tvMemberCount.setText(memberCount + " members");

            Log.d(TAG, "Group Chat Room ID: " + chatRoomId);
            Log.d(TAG, "Group Name: " + groupName);
            Log.d(TAG, "Is New Group: " + isNewGroup);
        } else {
            Log.e(TAG, "No chat room data received");
            showError("No group data found");
            finish();
        }
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

                        // Sort messages by timestamp (oldest first, latest at bottom)
                        Collections.sort(messages, new Comparator<ChatMessage>() {
                            @Override
                            public int compare(ChatMessage m1, ChatMessage m2) {
                                try {
                                    long time1 = parseTimestamp(m1.getTimestamp());
                                    long time2 = parseTimestamp(m2.getTimestamp());
                                    return Long.compare(time1, time2);
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

        // Prevent duplicate sends
        if (isSendingMessage) {
            return;
        }

        // Prevent sending same message within 2 seconds
        long currentTime = System.currentTimeMillis();
        if (messageText.equals(lastSentContent) && (currentTime - lastSentTime) < 2000) {
            Log.d(TAG, "Preventing duplicate message send");
            return;
        }

        // Set sending state
        isSendingMessage = true;
        lastSentContent = messageText;
        lastSentTime = currentTime;

        // Stop typing indicator
        stopTyping();

        // Send message via WebSocket
        webSocketManager.sendMessage(chatRoomId, messageText);

        // Clear the input
        etMessage.setText("");

        // Reset sending state after a delay
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
            } else if (typingUsers.size() == 2) {
                Iterator<String> iterator = typingUsers.iterator();
                String user1 = iterator.next();
                String user2 = iterator.next();
                tvTypingIndicator.setText(user1 + " and " + user2 + " are typing...");
            } else {
                tvTypingIndicator.setText(typingUsers.size() + " people are typing...");
            }
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // WebSocketChatManager.ChatMessageListener implementation
    @Override
    public void onMessageReceived(ChatMessage message) {
        // Prevent duplicate messages
        if (message.getId() != null && receivedMessageIds.contains(message.getId())) {
            Log.d(TAG, "Duplicate message received, ignoring: " + message.getId());
            return;
        }

        // Add to received set
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
            // Update member online status if needed
            Log.d(TAG, "User " + userId + " is now " + (isOnline ? "online" : "offline"));
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
            Log.d(TAG, "User joined group: " + username);
            // Could show a system message or update member count
        });
    }

    @Override
    public void onUserLeft(String username) {
        runOnUiThread(() -> {
            Log.d(TAG, "User left group: " + username);
            // Could show a system message or update member count
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
