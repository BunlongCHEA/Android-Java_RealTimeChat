package com.project.realtimechatui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.project.realtimechatui.api.ApiClient;
import com.project.realtimechatui.api.models.User;
import com.project.realtimechatui.databinding.ActivityNewChatBinding;
import com.project.realtimechatui.utils.SharedPrefManager;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NewChatActivity extends AppCompatActivity {
    
    private ActivityNewChatBinding binding;
    private SharedPrefManager sharedPrefManager;
    private UserAdapter userAdapter;
    private List<User> userList;
    private List<User> filteredUserList;
    private List<User> selectedUsers;
    private Handler searchHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Initialize ViewBinding
        binding = ActivityNewChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize components
        sharedPrefManager = SharedPrefManager.getInstance(this);
        userList = new ArrayList<>();
        filteredUserList = new ArrayList<>();
        selectedUsers = new ArrayList<>();
        searchHandler = new Handler(Looper.getMainLooper());

        setupUI();
        loadUsers();
    }

    private void setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        
        // Setup RecyclerView
        userAdapter = new UserAdapter(filteredUserList, this::onUserSelectionChanged);
        binding.rvUsers.setLayoutManager(new LinearLayoutManager(this));
        binding.rvUsers.setAdapter(userAdapter);
        
        // Setup search
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacksAndMessages(null);
                searchHandler.postDelayed(() -> filterUsers(s.toString()), 300);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Setup start chat button
        binding.btnStartChat.setOnClickListener(v -> createNewChat());
        
        updateStartChatButton();
    }

    private void loadUsers() {
        setLoading(true);
        
        // TODO: Implement API call to get users for chat
        // For now, we'll create some mock data
        createMockUsers();
        
        /*
        String token = "Bearer " + sharedPrefManager.getAccessToken();
        ApiClient.getApiService().getUsers(token).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    userList.clear();
                    userList.addAll(response.body());
                    filterUsers("");
                } else {
                    showError("Failed to load users");
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                setLoading(false);
                showError("Network error: " + t.getMessage());
            }
        });
        */
    }

    private void createMockUsers() {
        // Create mock users for demonstration
        setLoading(false);
        
        User user1 = new User();
        user1.setId(2L);
        user1.setUsername("john_doe");
        user1.setFullName("John Doe");
        user1.setEmail("john@example.com");
        
        User user2 = new User();
        user2.setId(3L);
        user2.setUsername("jane_smith");
        user2.setFullName("Jane Smith");
        user2.setEmail("jane@example.com");
        
        User user3 = new User();
        user3.setId(4L);
        user3.setUsername("bob_wilson");
        user3.setFullName("Bob Wilson");
        user3.setEmail("bob@example.com");
        
        userList.clear();
        userList.add(user1);
        userList.add(user2);
        userList.add(user3);
        
        filterUsers("");
    }

    private void filterUsers(String query) {
        filteredUserList.clear();
        
        if (query.isEmpty()) {
            filteredUserList.addAll(userList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (User user : userList) {
                if ((user.getFullName() != null && user.getFullName().toLowerCase().contains(lowerQuery)) ||
                    (user.getUsername() != null && user.getUsername().toLowerCase().contains(lowerQuery)) ||
                    (user.getEmail() != null && user.getEmail().toLowerCase().contains(lowerQuery))) {
                    filteredUserList.add(user);
                }
            }
        }
        
        userAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void onUserSelectionChanged(User user, boolean isSelected) {
        if (isSelected) {
            if (!selectedUsers.contains(user)) {
                selectedUsers.add(user);
            }
        } else {
            selectedUsers.remove(user);
        }
        
        updateStartChatButton();
    }

    private void updateStartChatButton() {
        boolean hasSelection = !selectedUsers.isEmpty();
        binding.btnStartChat.setEnabled(hasSelection);
        
        if (hasSelection) {
            String buttonText = selectedUsers.size() == 1 ? 
                "Start Chat with " + selectedUsers.get(0).getFullName() :
                "Start Group Chat (" + selectedUsers.size() + " users)";
            binding.btnStartChat.setText(buttonText);
        } else {
            binding.btnStartChat.setText(getString(R.string.start_chat));
        }
    }

    private void createNewChat() {
        if (selectedUsers.isEmpty()) {
            showError("Please select at least one user");
            return;
        }
        
        setLoading(true);
        
        // TODO: Implement API call to create new chat
        // For now, we'll simulate success
        simulateCreateChat();
        
        /*
        String token = "Bearer " + sharedPrefManager.getAccessToken();
        CreateChatRequest request = new CreateChatRequest(selectedUsers);
        ApiClient.getApiService().createChat(request, token).enqueue(new Callback<Chat>() {
            @Override
            public void onResponse(Call<Chat> call, Response<Chat> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    // Chat created successfully
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("new_chat", response.body());
                    setResult(RESULT_OK, resultIntent);
                    finish();
                } else {
                    showError("Failed to create chat");
                }
            }

            @Override
            public void onFailure(Call<Chat> call, Throwable t) {
                setLoading(false);
                showError("Network error: " + t.getMessage());
            }
        });
        */
    }

    private void simulateCreateChat() {
        // Simulate API call delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            setLoading(false);
            Toast.makeText(this, "Chat created successfully!", Toast.LENGTH_SHORT).show();
            
            Intent resultIntent = new Intent();
            setResult(RESULT_OK, resultIntent);
            finish();
        }, 1500);
    }

    private void updateEmptyState() {
        boolean isEmpty = filteredUserList.isEmpty();
        binding.llEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvUsers.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void setLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnStartChat.setEnabled(!isLoading && !selectedUsers.isEmpty());
        binding.etSearch.setEnabled(!isLoading);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}