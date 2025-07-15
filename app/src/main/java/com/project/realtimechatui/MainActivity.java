package com.project.realtimechatui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.project.realtimechatui.adapters.UserListAdapter;
import com.project.realtimechatui.adapters.UserSuggestionAdapter;
import com.project.realtimechatui.api.ApiClient;
import com.project.realtimechatui.api.ApiService;
import com.project.realtimechatui.api.models.BaseDTO;
import com.project.realtimechatui.api.models.ChatRoom;
import com.project.realtimechatui.api.models.Participant;
import com.project.realtimechatui.api.models.User;
import com.project.realtimechatui.enums.EnumRoomType;
import com.project.realtimechatui.utils.AuthDebugHelper;
import com.project.realtimechatui.utils.SharedPrefManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        UserListAdapter.OnUserClickListener,
        UserSuggestionAdapter.OnUserClickListener {

    private static final String TAG = "MainActivity";
    private EditText etSearch;
    private ImageView ivClearSearch, ivNewChat;
    private RecyclerView rvChatList;
    private LinearLayout llEmptyState;
    private ProgressBar pbLoading;

    private UserListAdapter userListAdapter;
    private UserSuggestionAdapter userSuggestionAdapter;
    private ApiService apiService;
    private SharedPrefManager sharedPrefManager;

    private boolean isSearchMode = false;

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

        // Check if user is logged in
        sharedPrefManager = SharedPrefManager.getInstance();
        if (!sharedPrefManager.isLoggedIn()) {
            redirectToLogin();
            return;
        }

        // Debug auth status and test token
        AuthDebugHelper.logAuthStatus();
        AuthDebugHelper.testTokenValidity();

        initViews();
        setupRecyclerView();
        setupApiService();
        setupSearchFunctionality();
//        loadParticipants();
        loadChatRooms();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh chat rooms when returning to this activity
        if (!isSearchMode) {
            loadChatRooms();
        }
    }

    private void redirectToLogin() {
        Toast.makeText(this, "Please login first", Toast.LENGTH_LONG).show();
        finish();
    }

    private void initViews() {
        etSearch = findViewById(R.id.etSearch);
        ivClearSearch = findViewById(R.id.ivClearSearch);
        ivNewChat = findViewById(R.id.ivNewChat);
        rvChatList = findViewById(R.id.rvChatList);
        llEmptyState = findViewById(R.id.llEmptyState);
        pbLoading = findViewById(R.id.pbLoading);
    }

    private void setupRecyclerView() {
        userListAdapter = new UserListAdapter(this, this);
        userSuggestionAdapter = new UserSuggestionAdapter(this, this);

        rvChatList.setLayoutManager(new LinearLayoutManager(this));
        rvChatList.setAdapter(userListAdapter); // Default adapter
    }

    private void setupApiService() {
        apiService = ApiClient.getApiService();
    }

    private void setupSearchFunctionality() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                ivClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);

                if (!query.isEmpty()) {
                    // Switch to search mode IMMEDIATELY when user starts typing
                    if (!isSearchMode) {
                        isSearchMode = true;
                        rvChatList.setAdapter(userSuggestionAdapter);
                        // Hide empty state when in search mode
                        llEmptyState.setVisibility(View.GONE);
                        rvChatList.setVisibility(View.VISIBLE);

                        // Load all users for search if not already loaded
                        if (userSuggestionAdapter.getItemCount() == 0) {
                            loadAllUsersForSearch();
                        }
                    }

                    // Filter existing users
                    userSuggestionAdapter.filter(query);

                    // If searching for specific username with @, also search via API
                    if (query.startsWith("@") && query.length() > 1) {
                        String username = query.substring(1);
                        searchUserByUsername(username);
                    }
                } else {
                    // Switch back to participant list mode
                    if (isSearchMode) {
                        isSearchMode = false;
                        rvChatList.setAdapter(userListAdapter);
                        updateUIState();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        ivClearSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etSearch.setText("");
                etSearch.clearFocus();
                // Switch back to participant list
                if (isSearchMode) {
                    isSearchMode = false;
                    rvChatList.setAdapter(userListAdapter);
                    updateUIState();
                }
            }
        });

        ivNewChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Switch to search mode to find users
                if (!isSearchMode) {
                    isSearchMode = true;
                    rvChatList.setAdapter(userSuggestionAdapter);
                    llEmptyState.setVisibility(View.GONE);
                    rvChatList.setVisibility(View.VISIBLE);

                    // Load all users if not already loaded
                    if (userSuggestionAdapter.getItemCount() == 0) {
                        loadAllUsersForSearch();
                    }
                }
                etSearch.requestFocus();
            }
        });
    }

//    private void loadParticipants() {
//        showLoading(true);
//
//        Long currentUserId = sharedPrefManager.getId();
//        if (currentUserId == null || currentUserId == -1L) {
//            showError("User ID not found");
//            return;
//        }
//
//        Call<BaseDTO<List<Participant>>> call = apiService.getChatPartners(currentUserId);
//        call.enqueue(new Callback<BaseDTO<List<Participant>>>() {
//            @Override
//            public void onResponse(Call<BaseDTO<List<Participant>>> call, Response<BaseDTO<List<Participant>>> response) {
//                showLoading(false);
//
//                if (response.isSuccessful() && response.body() != null) {
//                    BaseDTO<List<Participant>> result = response.body();
//                    if (result.isSuccess() && result.getData() != null) {
//                        List<Participant> participants = result.getData();
//
//                        Log.d(TAG, "Loaded " + participants.size() + " chat partners");
////                        for (Participant partner : participants) {
////                            if (partner.getUser() != null) {
////                                Log.d(TAG, "Chat partner: " + partner.getUser().getUsername());
////                            }
////                        }
//
//                        userListAdapter.setParticipants(participants);
//                        updateUIState();
//
//                        Log.d(TAG, "Loaded " + participants.size() + " chat participants");
//                    } else {
//                        showError("Failed to load participants: " + result.getMessage());
//                    }
//                } else {
//                    if (response.code() == 401) {
//                        handleUnauthorized();
//                    } else {
//                        showError("Failed to load participants");
//                    }
//                }
//            }
//
//            @Override
//            public void onFailure(Call<BaseDTO<List<Participant>>> call, Throwable t) {
//                showLoading(false);
//                showError("Network error: " + t.getMessage());
//            }
//        });
//    }

    // Updated method to load chat rooms instead of participants
    private void loadChatRooms() {
        showLoading(true);

        Long currentUserId = sharedPrefManager.getId();
        if (currentUserId == null || currentUserId == -1L) {
            showError("User ID not found");
            return;
        }

        Call<BaseDTO<List<ChatRoom>>> call = apiService.getChatRoomsByUserId(currentUserId);
        call.enqueue(new Callback<BaseDTO<List<ChatRoom>>>() {
            @Override
            public void onResponse(Call<BaseDTO<List<ChatRoom>>> call, Response<BaseDTO<List<ChatRoom>>> response) {
                showLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<List<ChatRoom>> result = response.body();
                    if (result.isSuccess() && result.getData() != null) {
                        List<ChatRoom> chatRooms = result.getData();

                        Log.d(TAG, "Loaded " + chatRooms.size() + " chat rooms");
                        for (ChatRoom room : chatRooms) {
                            Log.d(TAG, "Chat room: " + room.getName() +
                                    " (" + room.getType() + "), Last message: " + room.getLastMessageContent());
                        }

                        userListAdapter.setChatRooms(chatRooms);
                        updateUIState();
                    } else {
                        showError("Failed to load chat rooms: " + result.getMessage());
                    }
                } else {
                    if (response.code() == 401) {
                        handleUnauthorized();
                    } else {
                        showError("Failed to load chat rooms. Response code: " + response.code());
                    }
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<List<ChatRoom>>> call, Throwable t) {
                showLoading(false);
                showError("Network error: " + t.getMessage());
                Log.e(TAG, "Failed to load chat rooms", t);
            }
        });
    }

    // Load all users for search functionality
    // This is used when user wants to start a new chat
    private void loadAllUsersForSearch() {
        Call<BaseDTO<List<User>>> call = apiService.getAllUsers();
        call.enqueue(new Callback<BaseDTO<List<User>>>() {
            @Override
            public void onResponse(Call<BaseDTO<List<User>>> call, Response<BaseDTO<List<User>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<List<User>> result = response.body();
                    if (result.isSuccess() && result.getData() != null) {
                        List<User> users = result.getData();
                        Long currentUserId = sharedPrefManager.getId();
                        if (currentUserId != null && currentUserId != -1L) {
                            users.removeIf(user -> user.getId().equals(currentUserId));
                        }
                        userSuggestionAdapter.setUsers(users);
                    }
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<List<User>>> call, Throwable t) {
                // Handle error silently for search
                Log.e(TAG, "Failed to load users for search", t);
            }
        });
    }

    private void searchUserByUsername(String username) {
        Call<BaseDTO<User>> call = apiService.getUserByUsername(username);
        call.enqueue(new Callback<BaseDTO<User>>() {
            @Override
            public void onResponse(Call<BaseDTO<User>> call, Response<BaseDTO<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<User> result = response.body();
                    if (result.isSuccess() && result.getData() != null) {
                        User foundUser = result.getData();
                        Long currentUserId = sharedPrefManager.getId();
                        if (currentUserId == null || !foundUser.getId().equals(currentUserId)) {
                            // Run on UI thread to ensure proper updates
                            runOnUiThread(() -> {
                                // Show toast notification
                                Toast.makeText(MainActivity.this, "User found: @" + foundUser.getUsername(), Toast.LENGTH_SHORT).show();

                                // Add the found user to search results
                                userSuggestionAdapter.addUser(foundUser);

                                // Ensure we're in search mode and UI is visible
                                if (isSearchMode) {
                                    llEmptyState.setVisibility(View.GONE);
                                    rvChatList.setVisibility(View.VISIBLE);
                                }

                                Log.d(TAG, "Added user to search results: " + foundUser.getUsername());
                            });
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<User>> call, Throwable t) {
                Log.e(TAG, "Search by username failed", t);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Search failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showLoading(boolean show) {
        pbLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        rvChatList.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void updateUIState() {
        if (!isSearchMode) {
            boolean hasChatRooms = userListAdapter.getItemCount() > 0;
            rvChatList.setVisibility(hasChatRooms ? View.VISIBLE : View.GONE);
            llEmptyState.setVisibility(hasChatRooms ? View.GONE : View.VISIBLE);
        } else {
            // In search mode, always show the RecyclerView and hide empty state
            rvChatList.setVisibility(View.VISIBLE);
            llEmptyState.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        updateUIState();
    }

    private void handleUnauthorized() {
        sharedPrefManager.clearAuthData();
        redirectToLogin();
    }

    // UserListAdapter click listener (for participants)
    @Override
    public void onUserClick(ChatRoom chatRoom, Participant otherParticipant) {
        if (chatRoom == null) {
            return;
        }

        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("chat_room_id", chatRoom.getId());

        if (chatRoom.getType() == EnumRoomType.PERSONAL && otherParticipant != null) {
            // Personal chat - pass other user's info
            intent.putExtra("user_id", otherParticipant.getId());
            intent.putExtra("username", otherParticipant.getUsername());
            intent.putExtra("full_name", otherParticipant.getFullName());
            intent.putExtra("profile_picture", otherParticipant.getAvatarUrl());
        } else {
            // Group chat or channel - pass room info
            intent.putExtra("room_name", chatRoom.getName());
            intent.putExtra("room_type", chatRoom.getType().name());
        }

        startActivity(intent);
    }

    // UserSuggestionAdapter click listener (for search results)
    @Override
    public void onUserClick(User user) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("user_id", user.getId());
        intent.putExtra("username", user.getUsername());
        intent.putExtra("full_name", user.getFullName());
        intent.putExtra("profile_picture", user.getAvatarUrl());
        startActivity(intent);

        // Clear search and return to participant list
        etSearch.setText("");
        etSearch.clearFocus();
        isSearchMode = false;
        rvChatList.setAdapter(userListAdapter);
        updateUIState();
    }
}