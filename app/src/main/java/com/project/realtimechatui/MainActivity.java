package com.project.realtimechatui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.project.realtimechatui.adapters.ChatAdapter;
import com.project.realtimechatui.api.models.Message;
import com.project.realtimechatui.databinding.ActivityMainBinding;
import com.project.realtimechatui.utils.SharedPrefManager;
import com.project.realtimechatui.websocket.WebSocketService;

public class MainActivity extends AppCompatActivity implements WebSocketService.WebSocketListener {

    private ActivityMainBinding binding;
    private SharedPrefManager sharedPrefManager;
    private WebSocketService webSocketService;
    private ChatAdapter chatAdapter;
    private Long currentUserId;
    private Long currentChatRoomId = 1L; // Default chat room for now

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

        // Initialize SharedPrefManager
        sharedPrefManager = SharedPrefManager.getInstance(this);
        
        // Check if user is logged in
        if (!sharedPrefManager.isLoggedIn()) {
            navigateToLogin();
            return;
        }

        currentUserId = sharedPrefManager.getUserId();
        setupUI();
        setupWebSocket();
    }

    private void setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar);
        
        // Setup RecyclerView
        chatAdapter = new ChatAdapter(currentUserId);
        binding.rvMessages.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMessages.setAdapter(chatAdapter);
        
        // Setup click listeners
        binding.ivSendMessage.setOnClickListener(v -> sendMessage());
        binding.ivNewChat.setOnClickListener(v -> openNewChat());
        binding.btnStartNewChat.setOnClickListener(v -> openNewChat());
        
        // Setup message input
        binding.etMessage.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
        
        // Show start chat view initially
        showStartChatView(true);
    }

    private void setupWebSocket() {
        webSocketService = WebSocketService.getInstance();
        webSocketService.setListener(this);
        
        String accessToken = sharedPrefManager.getAccessToken();
        if (accessToken != null) {
            showConnectionStatus("Connecting to server...", true);
            webSocketService.connect(accessToken);
        } else {
            showError("No access token found. Please login again.");
            navigateToLogin();
        }
    }

    private void sendMessage() {
        String messageText = binding.etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(messageText)) {
            return;
        }

        if (!webSocketService.isConnected()) {
            showError("Not connected to server. Please check your connection.");
            return;
        }

        // Create message object
        Message message = new Message(
            messageText,
            currentUserId,
            sharedPrefManager.getUsername(),
            currentChatRoomId
        );

        // Send message via WebSocket
        webSocketService.sendMessage(message);
        
        // Clear input
        binding.etMessage.setText("");
        
        // Hide start chat view if visible
        if (binding.cvStartChat.getVisibility() == View.VISIBLE) {
            showStartChatView(false);
        }
    }

    private void openNewChat() {
        Intent intent = new Intent(this, NewChatActivity.class);
        startActivity(intent);
    }

    private void showStartChatView(boolean show) {
        binding.cvStartChat.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.layoutMessageInput.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showConnectionStatus(String message, boolean show) {
        binding.tvConnectionStatus.setText(message);
        binding.tvConnectionStatus.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // WebSocket Listener Methods
    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            showConnectionStatus("Connected", false);
            Toast.makeText(this, "Connected to chat server", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            showConnectionStatus("Disconnected from server", true);
        });
    }

    @Override
    public void onMessageReceived(Message message) {
        runOnUiThread(() -> {
            chatAdapter.addMessage(message);
            binding.rvMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
            
            // Hide start chat view if visible
            if (binding.cvStartChat.getVisibility() == View.VISIBLE) {
                showStartChatView(false);
            }
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            showError(error);
            showConnectionStatus("Connection error", true);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocketService != null) {
            webSocketService.disconnect();
        }
        binding = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reconnect if needed
        if (webSocketService != null && !webSocketService.isConnected()) {
            String accessToken = sharedPrefManager.getAccessToken();
            if (accessToken != null) {
                webSocketService.connect(accessToken);
            }
        }
    }
}