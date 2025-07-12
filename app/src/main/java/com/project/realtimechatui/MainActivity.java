package com.project.realtimechatui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.project.realtimechatui.adapters.UserSuggestionAdapter;
import com.project.realtimechatui.api.ApiClient;
import com.project.realtimechatui.api.ApiService;
import com.project.realtimechatui.api.models.BaseDTO;
import com.project.realtimechatui.api.models.User;
import com.project.realtimechatui.utils.AuthDebugHelper;
import com.project.realtimechatui.utils.SharedPrefManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.List;

public class MainActivity extends AppCompatActivity implements UserSuggestionAdapter.OnUserClickListener {

    private EditText etSearch;
    private ImageView ivClearSearch, ivNewChat;
    private RecyclerView rvChatList;
    private LinearLayout llEmptyState;
    private ProgressBar pbLoading;

    private UserSuggestionAdapter userAdapter;
    private ApiService apiService;
    private SharedPrefManager sharedPrefManager;

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
        AuthDebugHelper.testTokenValidity(); // Add this line

        setContentView(R.layout.activity_main);

        initViews();
        setupRecyclerView();
        setupApiService();
        setupSearchFunctionality();
        loadAllUsers();
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
        userAdapter = new UserSuggestionAdapter(this, this);
        rvChatList.setLayoutManager(new LinearLayoutManager(this));
        rvChatList.setAdapter(userAdapter);
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
                userAdapter.filter(query);

                if (query.startsWith("@") && query.length() > 1) {
                    String username = query.substring(1);
                    searchUserByUsername(username);
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
            }
        });

        ivNewChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAllUsers();
            }
        });
    }

    private void loadAllUsers() {
        showLoading(true);

        Call<BaseDTO<List<User>>> call = apiService.getAllUsers();
        call.enqueue(new Callback<BaseDTO<List<User>>>() {
            @Override
            public void onResponse(Call<BaseDTO<List<User>>> call, Response<BaseDTO<List<User>>> response) {
                showLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<List<User>> result = response.body();
                    if (result.isSuccess() && result.getData() != null) {
                        List<User> users = result.getData();
                        Long currentUserId = sharedPrefManager.getId();
                        if (currentUserId != null && currentUserId != -1L) {
                            users.removeIf(user -> user.getId().equals(currentUserId));
                        }

                        userAdapter.setUsers(users);
                        updateUIState();
                    } else {
                        showError("Failed to load users: " + result.getMessage());
                    }
                } else {
                    if (response.code() == 401) {
                        handleUnauthorized();
                    } else {
                        showError("Failed to load users");
                    }
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<List<User>>> call, Throwable t) {
                showLoading(false);
                showError("Network error: " + t.getMessage());
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
                            Toast.makeText(MainActivity.this, "User found: @" + foundUser.getUsername(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<User>> call, Throwable t) {
                // Handle error silently for search
            }
        });
    }

    private void showAllUsers() {
        etSearch.setText("");
        loadAllUsers();
    }

    private void showLoading(boolean show) {
        pbLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        rvChatList.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void updateUIState() {
        boolean hasUsers = userAdapter.getItemCount() > 0;
        rvChatList.setVisibility(hasUsers ? View.VISIBLE : View.GONE);
        llEmptyState.setVisibility(hasUsers ? View.GONE : View.VISIBLE);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        updateUIState();
    }

    private void handleUnauthorized() {
        sharedPrefManager.clearAuthData();
        redirectToLogin();
    }

    // This method implements the OnUserClickListener interface
    @Override
    public void onUserClick(User user) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("user_id", user.getId());
        intent.putExtra("username", user.getUsername());
        intent.putExtra("full_name", user.getFullName());
        intent.putExtra("profile_picture", user.getAvatarUrl());
        startActivity(intent);
    }
}