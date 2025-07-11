package com.project.realtimechatui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.project.realtimechatui.api.models.Chat;
import com.project.realtimechatui.api.models.WebSocketMessage;
import com.project.realtimechatui.databinding.ActivityMainBinding;
import com.project.realtimechatui.utils.SharedPrefManager;
import com.project.realtimechatui.websocket.ChatWebSocketClient;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ChatWebSocketClient.WebSocketListener {
    
    private ActivityMainBinding binding;
    private SharedPrefManager sharedPrefManager;
    private ChatWebSocketClient webSocketClient;
    private Handler uiHandler;
    private List<Chat> chatList;
    private ChatAdapter chatAdapter;
    
    private static final int REQUEST_NEW_CHAT = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Initialize ViewBinding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize components
        sharedPrefManager = SharedPrefManager.getInstance(this);
        uiHandler = new Handler(Looper.getMainLooper());
        chatList = new ArrayList<>();
        
        // Check if user is logged in
        if (!sharedPrefManager.isLoggedIn()) {
            navigateToLogin();
            return;
        }

        setupUI();
        initializeChatList();
        connectWebSocket();
    }

    private void setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar);
        
        // Setup RecyclerView
        chatAdapter = new ChatAdapter(chatList, this::onChatItemClick);
        binding.rvChats.setLayoutManager(new LinearLayoutManager(this));
        binding.rvChats.setAdapter(chatAdapter);
        
        // Setup click listeners
        binding.ivNewChat.setOnClickListener(v -> openNewChatActivity());
        binding.cvStartChat.setOnClickListener(v -> openNewChatActivity());
        
        // Setup swipe refresh
        binding.swipeRefresh.setOnRefreshListener(this::refreshChats);
        
        // Update UI based on chat list
        updateEmptyState();
    }

    private void initializeChatList() {
        // TODO: Load chats from API or local database
        // For now, we'll show empty state
        updateEmptyState();
    }

    private void connectWebSocket() {
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }
        
        updateConnectionStatus(getString(R.string.connecting));
        webSocketClient = new ChatWebSocketClient(this);
        
        // Connect in background thread
        new Thread(() -> {
            try {
                webSocketClient.connect();
            } catch (Exception e) {
                uiHandler.post(() -> {
                    updateConnectionStatus(getString(R.string.disconnected));
                    showError("Failed to connect to server: " + e.getMessage());
                });
            }
        }).start();
    }

    private void openNewChatActivity() {
        Intent intent = new Intent(this, NewChatActivity.class);
        startActivityForResult(intent, REQUEST_NEW_CHAT);
    }

    private void onChatItemClick(Chat chat) {
        // TODO: Open chat conversation activity
        Toast.makeText(this, "Open chat: " + chat.getName(), Toast.LENGTH_SHORT).show();
    }

    private void refreshChats() {
        // TODO: Refresh chats from API
        binding.swipeRefresh.setRefreshing(false);
    }

    private void updateEmptyState() {
        boolean isEmpty = chatList.isEmpty();
        binding.llEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvChats.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void updateConnectionStatus(String status) {
        uiHandler.post(() -> {
            binding.tvConnectionStatus.setText(status);
            
            // Update status color
            if (status.equals(getString(R.string.connected))) {
                binding.tvConnectionStatus.setTextColor(getResources().getColor(R.color.colorAccent));
            } else if (status.equals(getString(R.string.connecting))) {
                binding.tvConnectionStatus.setTextColor(getResources().getColor(R.color.colorPrimary));
            } else {
                binding.tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            }
        });
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // WebSocket Listener Methods
    @Override
    public void onConnected() {
        updateConnectionStatus(getString(R.string.connected));
        
        // TODO: Send authentication/join message if needed
        // WebSocketMessage authMessage = new WebSocketMessage("AUTH", null, sharedPrefManager.getUserId(), null);
        // webSocketClient.sendMessage(authMessage);
    }

    @Override
    public void onDisconnected() {
        updateConnectionStatus(getString(R.string.disconnected));
    }

    @Override
    public void onMessageReceived(WebSocketMessage message) {
        uiHandler.post(() -> {
            // TODO: Handle different message types
            switch (message.getType()) {
                case "MESSAGE":
                    // Handle new message
                    // Update chat list or notify user
                    break;
                case "NEW_CHAT":
                    // Handle new chat created
                    refreshChats();
                    break;
                default:
                    // Handle other message types
                    break;
            }
        });
    }

    @Override
    public void onError(String error) {
        uiHandler.post(() -> showError(error));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_NEW_CHAT && resultCode == RESULT_OK) {
            // New chat was created, refresh the list
            refreshChats();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reconnect WebSocket if disconnected
        if (webSocketClient == null || !webSocketClient.isConnected()) {
            connectWebSocket();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }
        binding = null;
    }
}