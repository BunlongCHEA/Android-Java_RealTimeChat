package com.project.realtimechatui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    private final List<User> selectedUsersForGroup = new ArrayList<>();

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
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Start New Chat")
                        .setItems(new CharSequence[]{"Group Chat", "Channel"}, (dialog, which) -> {
                            if (which == 0) {
                                showGroupChatDialog();
                            } else if (which == 1) {
                                // Future: Channel
                                Toast.makeText(MainActivity.this, "Channel creation coming soon", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
            }
        });
    }

//    private void startPersonalChatMode() {
//        // Switch to search mode to find users for personal chat
//        if (!isSearchMode) {
//            isSearchMode = true;
//            rvChatList.setAdapter(userSuggestionAdapter);
//            llEmptyState.setVisibility(View.GONE);
//            rvChatList.setVisibility(View.VISIBLE);
//
//            // Load all users if not already loaded
//            if (userSuggestionAdapter.getItemCount() == 0) {
//                loadAllUsersForSearch();
//            }
//        }
//        etSearch.requestFocus();
//        Toast.makeText(this, "Search for a user to start a personal chat", Toast.LENGTH_SHORT).show();
//    }

    private void showGroupChatDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_group_chat, null);
        EditText etGroupName = dialogView.findViewById(R.id.etGroupName);
        EditText etSearchUsers = dialogView.findViewById(R.id.etSearchUsers);
        RecyclerView rvUserList = dialogView.findViewById(R.id.rvUserList);
        ImageButton btnBack = dialogView.findViewById(R.id.btnBack);
        ChipGroup chipGroup = dialogView.findViewById(R.id.chipGroupSelectedUsers);

        selectedUsersForGroup.clear();
        chipGroup.removeAllViews();

        UserSuggestionAdapter.OnUserClickListener listener = user -> {
            if (selectedUsersForGroup.contains(user)) {
                selectedUsersForGroup.remove(user);
                removeUserChip(chipGroup, user);
            } else {
                selectedUsersForGroup.add(user);
                addUserChip(chipGroup, user);
            }
        };

        UserSuggestionAdapter adapter = new UserSuggestionAdapter(this, listener);
        rvUserList.setLayoutManager(new LinearLayoutManager(this));
        rvUserList.setAdapter(adapter);

        // Create dialog
        AlertDialog groupDialog = new AlertDialog.Builder(this)
//                .setTitle("Create Group Chat")
                .setView(dialogView)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();

        // Handle back button click
        btnBack.setOnClickListener(v -> groupDialog.dismiss());

        groupDialog.setOnShowListener(d -> {
            Button btnCreate = groupDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnCreate.setOnClickListener(v -> {
                String groupName = etGroupName.getText().toString().trim();
                if (groupName.isEmpty()) {
                    etGroupName.setError("Group name required");
                    return;
                }
                if (selectedUsersForGroup.size() < 2) {
                    Toast.makeText(this, "Select at least 2 users", Toast.LENGTH_SHORT).show();
                    return;
                }

//                ChatRoom chatRoom = new ChatRoom();
////                chatRoom.setId(System.currentTimeMillis()); // temp ID
//                chatRoom.setName(groupName);
//                chatRoom.setType(EnumRoomType.GROUP);
//                Set<Participant> participants = new HashSet<>();
//
//                // Add current user as admin
//                Participant currentUserParticipant = new Participant();
//                currentUserParticipant.setUserId(sharedPrefManager.getId());
//                currentUserParticipant.setUsername(sharedPrefManager.getUsername());
//                currentUserParticipant.setFullName(sharedPrefManager.getFullName()); // assuming you have this
//                currentUserParticipant.setOnline(true);
//                currentUserParticipant.setRole("ADMIN");
//                currentUserParticipant.setMuted(false);
//                currentUserParticipant.setBlocked(false);
//                currentUserParticipant.setJoinDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
//                participants.add(currentUserParticipant);
//
//                // Add selected users as members
//                for (User user : selectedUsersForGroup) {
//                    Participant participant = new Participant();
//                    participant.setUserId(user.getId());
//                    participant.setUsername(user.getUsername());
//                    participant.setFullName(user.getFullName());
//                    participant.setAvatarUrl(user.getAvatarUrl());
//                    participant.setOnline(true);
//                    participant.setRole("MEMBER");
//                    participant.setMuted(false);
//                    participant.setBlocked(false);
//                    participant.setJoinDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
//                    participants.add(participant);
//                }
//
//                chatRoom.setParticipants(participants);
//
//                // Pass the created group chat room to GroupChatActivity
//                Intent intent = new Intent(MainActivity.this, GroupChatActivity.class);
//                intent.putExtra("chat_room", chatRoom);
//                intent.putExtra("is_new_group", true); // Flag to indicate this is a new group
//                startActivity(intent);

                createGroupChatRoom(groupName);
                groupDialog.dismiss();
            });
        });

        etSearchUsers.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (!TextUtils.isEmpty(query)) {
                    // Search users when user types
                    searchUsersForGroupDialog(query, adapter);
                } else {
                    // Show all users when search is empty
                    if (userSuggestionAdapter != null && userSuggestionAdapter.users != null) {
                        adapter.setUsers(userSuggestionAdapter.users);
                    } else {
                        loadAllUsersForGroupDialog(adapter);
                    }
                }
            }
        });

        // Load initial users
        if (userSuggestionAdapter != null && userSuggestionAdapter.users != null && !userSuggestionAdapter.users.isEmpty()) {
            adapter.setUsers(userSuggestionAdapter.users);
        } else {
            loadAllUsersForGroupDialog(adapter);
        }

        groupDialog.show();
    }

    private void createGroupChatRoom(String groupName) {
        Long currentUserId = sharedPrefManager.getId();
        if (currentUserId == null) {
            showError("User not logged in");
            return;
        }

//        // Create ChatRoom DTO for personal chat
//        ChatRoom chatRoom = new ChatRoom();
//        chatRoom.setType(EnumRoomType.PERSONAL);
//        chatRoom.setName(targetUsername); // Backend will handle the name
//
//        // Add target user as participant
//        Set<Participant> participants = new HashSet<>();
//        Participant targetParticipant = new Participant();
//        targetParticipant.setUserId(targetUserId);
//        participants.add(targetParticipant);
//        chatRoom.setParticipants(participants);

        // Create ChatRoom DTO for group chat
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setName(groupName);
        chatRoom.setType(EnumRoomType.GROUP);

        Set<Participant> participants = new HashSet<>();

        // Add current user as admin
        Participant currentUserParticipant = new Participant();
        currentUserParticipant.setUserId(currentUserId);
//        currentUserParticipant.setUsername(sharedPrefManager.getUsername());
//        currentUserParticipant.setFullName(sharedPrefManager.getFullName());
//        currentUserParticipant.setOnline(true);
        currentUserParticipant.setRole("ADMIN");
//        currentUserParticipant.setMuted(false);
//        currentUserParticipant.setBlocked(false);
//        currentUserParticipant.setJoinDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        participants.add(currentUserParticipant);

        // Add selected users as members
        for (User user : selectedUsersForGroup) {
            Participant participant = new Participant();
            participant.setUserId(user.getId());
//            participant.setUsername(user.getUsername());
//            participant.setFullName(user.getFullName());
//            participant.setAvatarUrl(user.getAvatarUrl());
//            participant.setOnline(true);
            participant.setRole("MEMBER");
//            participant.setMuted(false);
//            participant.setBlocked(false);
//            participant.setJoinDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            participants.add(participant);
        }

        chatRoom.setParticipants(participants);

        Log.d(TAG, "Creating group chat room: " + groupName);

        Call<BaseDTO<ChatRoom>> call = apiService.createChatRoom(chatRoom, currentUserId);
        call.enqueue(new Callback<BaseDTO<ChatRoom>>() {
            @Override
            public void onResponse(Call<BaseDTO<ChatRoom>> call, Response<BaseDTO<ChatRoom>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<ChatRoom> result = response.body();
                    if (result.isSuccess() && result.getData() != null) {
                        ChatRoom createdRoom = result.getData();
                        Log.d(TAG, "Created group chat room: " + createdRoom.getId());

                        // Navigate to GroupChatActivity
                        Intent intent = new Intent(MainActivity.this, GroupChatActivity.class);
                        intent.putExtra("chat_room", createdRoom);
                        intent.putExtra("is_new_group", true);
                        startActivity(intent);

                        // Refresh chat rooms list
                        loadChatRooms();
                    } else {
                        showError("Failed to create group chat: " + result.getMessage());
                    }
                } else {
                    showError("Failed to create group chat");
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<ChatRoom>> call, Throwable t) {
                Log.e(TAG, "Failed to create group chat room", t);
                showError("Network error: " + t.getMessage());
            }
        });
    }

    private void addUserChip(ChipGroup chipGroup, User user) {
        Chip chip = new Chip(this);
        chip.setText(user.getUsername());
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> {
            selectedUsersForGroup.remove(user);
            chipGroup.removeView(chip);
        });
        chipGroup.addView(chip);
    }

    private void removeUserChip(ChipGroup chipGroup, User user) {
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View child = chipGroup.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (chip.getText().toString().equals(user.getUsername())) {
                    chipGroup.removeViewAt(i);
                    break;
                }
            }
        }
    }

    // Modified method to load all users specifically for group dialog
    private void loadAllUsersForGroupDialog(UserSuggestionAdapter adapter) {
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

                        // Update the group dialog adapter
                        runOnUiThread(() -> {
                            adapter.setUsers(users);
                            // Also update the main userSuggestionAdapter for consistency
                            if (userSuggestionAdapter != null) {
                                userSuggestionAdapter.setUsers(users);
                            }
                        });
                    }
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<List<User>>> call, Throwable t) {
                Log.e(TAG, "Failed to load users for group dialog", t);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Failed to load users", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // Modified method to search users specifically for group dialog
    private void searchUsersForGroupDialog(String query, UserSuggestionAdapter adapter) {
        // If query looks like a username (starts with @ or is a simple string), search by username
        String searchQuery = query.startsWith("@") ? query.substring(1) : query;

        // First, filter existing users
        adapter.filter(query);

        // Then, if the query looks like a username, also search the API
        if (searchQuery.length() >= 2) { // Only search if query has at least 2 characters
            Call<BaseDTO<User>> call = apiService.getUserByUsername(searchQuery);
            call.enqueue(new Callback<BaseDTO<User>>() {
                @Override
                public void onResponse(Call<BaseDTO<User>> call, Response<BaseDTO<User>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        BaseDTO<User> result = response.body();
                        if (result.isSuccess() && result.getData() != null) {
                            User foundUser = result.getData();
                            Long currentUserId = sharedPrefManager.getId();
                            if (currentUserId == null || !foundUser.getId().equals(currentUserId)) {
                                runOnUiThread(() -> {
                                    // Add the found user to the adapter
                                    adapter.addUser(foundUser);
                                    Log.d(TAG, "Added user to group dialog: " + foundUser.getUsername());
                                });
                            }
                        }
                    }
                }

                @Override
                public void onFailure(Call<BaseDTO<User>> call, Throwable t) {
                    Log.e(TAG, "Search by username failed in group dialog", t);
                    // Don't show error toast for search failures in group dialog
                }
            });
        }
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

        if (chatRoom.getType() == EnumRoomType.PERSONAL) {
            // Personal chat - navigate to ChatActivity
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("chat_room_id", chatRoom.getId());

            if (otherParticipant != null) {
                // Personal chat - pass other user's info
                intent.putExtra("user_id", otherParticipant.getUserId());
                intent.putExtra("username", otherParticipant.getUsername());
                intent.putExtra("full_name", otherParticipant.getFullName());
                intent.putExtra("profile_picture", otherParticipant.getAvatarUrl());
            }

            startActivity(intent);
        } else if (chatRoom.getType() == EnumRoomType.GROUP) {
            // Group chat - navigate to GroupChatActivity
            Intent intent = new Intent(this, GroupChatActivity.class);
            intent.putExtra("chat_room", chatRoom);
            intent.putExtra("is_new_group", false);
            startActivity(intent);

        } else if (chatRoom.getType() == EnumRoomType.CHANNEL) {
            // Channel - navigate to ChannelActivity (future implementation)
            Toast.makeText(this, "Channel support coming soon", Toast.LENGTH_SHORT).show();
        }
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