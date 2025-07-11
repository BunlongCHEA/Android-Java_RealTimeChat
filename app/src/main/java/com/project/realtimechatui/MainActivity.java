package com.project.realtimechatui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.project.realtimechatui.adapters.ChatAdapter;
import com.project.realtimechatui.api.ApiClient;
import com.project.realtimechatui.api.ApiService;
import com.project.realtimechatui.api.models.BaseDTO;
import com.project.realtimechatui.api.models.ChatRoom;
import com.project.realtimechatui.utils.Constants;
import com.project.realtimechatui.utils.SharedPrefManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import retrofit2.Call;
import retrofit2.Callback;

public class MainActivity extends AppCompatActivity implements ChatAdapter.OnChatClickListener {

    private static final String TAG = "MainActivity";
    private EditText etSearch;
    private ImageView ivClearSearch;
    private ImageView ivNewChat;
    private RecyclerView rvChatList;
    private LinearLayout llEmptyState;
    private CardView cvStartChat;
    private ProgressBar pbLoading;

    private ChatAdapter chatAdapter;
    private ApiService apiService;
    private SharedPrefManager sharedPrefManager;
    private WebSocket webSocket;
    private OkHttpClient client = new OkHttpClient();

    private List<ChatRoom> allChatRooms = new ArrayList<>();
    private List<ChatRoom> filteredChatRooms = new ArrayList<>();
    private String currentUsername;
    private Long currentUserId;
    private Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        initData();
        setupRecyclerView();
        setupSearchFunctionality();
        setupClickListeners();
        connectWebSocket();
        loadChatRooms();
    }

    private void initViews() {
        etSearch = findViewById(R.id.etSearch);
        ivClearSearch = findViewById(R.id.ivClearSearch);
        ivNewChat = findViewById(R.id.ivNewChat);
        rvChatList = findViewById(R.id.rvChatList);
        llEmptyState = findViewById(R.id.llEmptyState);
        cvStartChat = findViewById(R.id.cvStartChat);
        pbLoading = findViewById(R.id.pbLoading);
    }

    private void initData() {
        // Initialize OkHttpClient with timeout settings
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        // Initialize API service
        apiService = ApiClient.getApiService();

        // Initialize SharedPrefManager
        sharedPrefManager = SharedPrefManager.getInstance(this);

        // Check if user is logged in
        if (!sharedPrefManager.isLoggedIn()) {
            // Redirect to login if not logged in
            redirectToLogin("User not logged in");
            return;
        }

        // Get current user info from SharedPrefManager
        currentUsername = sharedPrefManager.getUsername();
        currentUserId = sharedPrefManager.getId();

        if (currentUsername == null || currentUserId == null) {
            redirectToLogin("User data not found. Please login again.");
            return;
        }
    }

    private void redirectToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        sharedPrefManager.clearAuthData();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(this, currentUsername);
        chatAdapter.setOnChatClickListener(this);

        rvChatList.setLayoutManager(new LinearLayoutManager(this));
        rvChatList.setAdapter(chatAdapter);
    }

    private void setupSearchFunctionality() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String searchText = s.toString().trim();

                if (!searchText.isEmpty()) {
                    ivClearSearch.setVisibility(View.VISIBLE);
                    filterChatRooms(searchText);
                } else {
                    ivClearSearch.setVisibility(View.GONE);
                    showAllChatRooms();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        ivClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            ivClearSearch.setVisibility(View.GONE);
            showAllChatRooms();
        });
    }

    private void setupClickListeners() {
        // New Chat button - opens dialog to create new group chat
        ivNewChat.setOnClickListener(v -> showNewChatDialog());

        // Start Chat button - same functionality as new chat
        cvStartChat.setOnClickListener(v -> showNewChatDialog());
    }

    private void showNewChatDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_chat, null);

        EditText etChatName = dialogView.findViewById(R.id.etChatName);
        EditText etParticipants = dialogView.findViewById(R.id.etParticipants);

        builder.setView(dialogView)
                .setTitle("Create New Group Chat")
                .setPositiveButton("Create", (dialog, which) -> {
                    String chatName = etChatName.getText().toString().trim();
                    String participantsText = etParticipants.getText().toString().trim();

                    if (chatName.isEmpty()) {
                        Toast.makeText(this, "Please enter a chat name", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    createNewGroupChat(chatName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createNewGroupChat(String chatName) {
        showLoading(true);

        // Create chat room (participants will be handled separately via backend logic)
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setName(chatName);
        chatRoom.setType("GROUP");

        // Make API call to create chat room - matching your backend signature
        Call<BaseDTO<ChatRoom>> call = apiService.createChatRoom(chatRoom, currentUserId);

        call.enqueue(new Callback<BaseDTO<ChatRoom>>() {
            @Override
            public void onResponse(Call<BaseDTO<ChatRoom>> call, retrofit2.Response<BaseDTO<ChatRoom>> response) {
                showLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<ChatRoom> result = response.body();

                    Log.d(TAG, "Create chat room response: " + result.toString());

                    if (result.isSuccess()) {
                        Toast.makeText(MainActivity.this, "Group chat created successfully!", Toast.LENGTH_SHORT).show();
                        // Refresh chat list
                        loadChatRooms();
                    } else {
                        String errorMsg = result.getMessage() != null ? result.getMessage() : "Unknown error occurred";
                        Toast.makeText(MainActivity.this, "Failed to create chat: " + errorMsg, Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Create chat failed - Status: " + result.getStatusCode() + ", Message: " + result.getMessage());
                    }
                } else {
                    String errorMsg = "HTTP " + response.code() + ": " + response.message();
                    Toast.makeText(MainActivity.this, "Failed to create group chat: " + errorMsg, Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Create chat HTTP error: " + errorMsg);
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<ChatRoom>> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "Create chat room failed", t);
                Toast.makeText(MainActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void connectWebSocket() {
        if (webSocket != null) {
            webSocket.close(1000, "Reconnecting");
        }

        String token = sharedPrefManager.getAccessToken();
        if (token == null || token.isEmpty()) {
            Log.e(TAG, "No access token available for WebSocket connection");
            return;
        }

        Request request = new Request.Builder()
                .url(Constants.WEBSOCKET_URL + "?token=" + token)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "WebSocket connected successfully");
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Connected to real-time chat", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "WebSocket message received: " + text);
                runOnUiThread(() -> handleWebSocketMessage(text));
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closing: " + code + " / " + reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + code + " / " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket connection failed", t);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Real-time connection failed", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void handleWebSocketMessage(String message) {
        try {
            // Parse the WebSocket message
            WebSocketMessage wsMessage = gson.fromJson(message, WebSocketMessage.class);

            switch (wsMessage.getType()) {
                case "new_chat_room":
                case "chat_room_updated":
                case "new_message":
                case "user_joined":
                case "user_left":
                    // Refresh chat list for any room-related updates
                    loadChatRooms();
                    break;
                default:
                    Log.d(TAG, "Unknown WebSocket message type: " + wsMessage.getType());
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing WebSocket message", e);
        }
    }

    private void loadChatRooms() {
        showLoading(true);

        // Call API without authorization header as your backend doesn't seem to require it for this endpoint
        Call<BaseDTO<List<ChatRoom>>> call = apiService.getChatRooms();
        call.enqueue(new Callback<BaseDTO<List<ChatRoom>>>() {
            @Override
            public void onResponse(Call<BaseDTO<List<ChatRoom>>> call, retrofit2.Response<BaseDTO<List<ChatRoom>>> response) {
                showLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<List<ChatRoom>> result = response.body();
                    if (result.getStatusCode() == 200 && result.getData() != null) {
                        allChatRooms = result.getData();
                        filteredChatRooms = new ArrayList<>(allChatRooms);
                        chatAdapter.setChatRooms(filteredChatRooms);
                        updateEmptyState();

                        Log.d(TAG, "Loaded " + allChatRooms.size() + " chat rooms");
                    } else {
                        String errorMsg = result.getMessage() != null ? result.getMessage() : "Unknown error occurred";
                        Toast.makeText(MainActivity.this, "Failed to load chats: " + errorMsg, Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Load chats failed - Status: " + result.getStatusCode() + ", Message: " + result.getMessage());
                        loadMockChatRooms();
                    }
                } else {
                    String errorMsg = "HTTP " + response.code() + ": " + response.message();
                    Log.e(TAG, "Load chat rooms HTTP error: " + errorMsg);

                    if (response.code() == 401) {
                        redirectToLogin("Session expired. Please login again.");
                    } else {
                        Toast.makeText(MainActivity.this, "Failed to load chat rooms: " + errorMsg, Toast.LENGTH_SHORT).show();
                        loadMockChatRooms();
                    }
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<List<ChatRoom>>> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "Load chat rooms failed", t);
                Toast.makeText(MainActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                loadMockChatRooms();
            }
        });
    }


    private void loadMockChatRooms() {
        // Simulate network delay
        new android.os.Handler().postDelayed(() -> {
            showLoading(false);
            allChatRooms = new ArrayList<>();
            filteredChatRooms = new ArrayList<>(allChatRooms);
            chatAdapter.setChatRooms(filteredChatRooms);
            updateEmptyState();
            Toast.makeText(this, "No chat rooms found. Start a new conversation!", Toast.LENGTH_SHORT).show();
        }, 1000);
    }

    private void filterChatRooms(String searchText) {
        filteredChatRooms.clear();

        // Remove @ if user typed it
        String cleanSearchText = searchText.startsWith("@") ? searchText.substring(1) : searchText;

        for (ChatRoom chatRoom : allChatRooms) {
            boolean matches = false;

            // Search in chat room name
            if (chatRoom.getName() != null &&
                    chatRoom.getName().toLowerCase().contains(cleanSearchText.toLowerCase())) {
                matches = true;
            }

            // Search in participant usernames for personal chats
            if (!matches && chatRoom.getParticipants() != null) {
                for (com.project.realtimechatui.api.models.Participant participant : chatRoom.getParticipants()) {
                    if (!currentUsername.equals(participant.getUsername()) &&
                            participant.getUsername() != null &&
                            participant.getUsername().toLowerCase().contains(cleanSearchText.toLowerCase())) {
                        matches = true;
                        break;
                    }
                }
            }

            if (matches) {
                filteredChatRooms.add(chatRoom);
            }
        }

        chatAdapter.setChatRooms(filteredChatRooms);
        updateEmptyState();
    }

    private void showAllChatRooms() {
        filteredChatRooms = new ArrayList<>(allChatRooms);
        chatAdapter.setChatRooms(filteredChatRooms);
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (filteredChatRooms.isEmpty()) {
            rvChatList.setVisibility(View.GONE);
            llEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvChatList.setVisibility(View.VISIBLE);
            llEmptyState.setVisibility(View.GONE);
        }
    }

    private void showLoading(boolean show) {
        pbLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            rvChatList.setVisibility(View.GONE);
            llEmptyState.setVisibility(View.GONE);
        }
    }

    @Override
    public void onChatClick(ChatRoom chatRoom) {
        // Navigate to chat detail activity (to be implemented)
        Toast.makeText(this, "Opening chat: " + chatRoom.getName(), Toast.LENGTH_SHORT).show();

        // Uncomment when you implement ChatDetailActivity:
        /*
        Intent intent = new Intent(this, ChatDetailActivity.class);
        intent.putExtra("chat_room_id", chatRoom.getId());
        intent.putExtra("chat_room_name", chatRoom.getName());
        intent.putExtra("chat_room_type", chatRoom.getType());
        startActivity(intent);
        */
    }


    // Overrid class
    @Override
    protected void onResume() {
        super.onResume();
        if (apiService != null && sharedPrefManager != null && sharedPrefManager.isLoggedIn()) {
            loadChatRooms();
        }
        if (webSocket == null) {
            connectWebSocket();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocket != null) {
            webSocket.close(1000, "Activity destroyed");
            webSocket = null;
        }
    }

    // Helper class for WebSocket messages
    private static class WebSocketMessage {
        private String type;
        private Object data;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
    }

}