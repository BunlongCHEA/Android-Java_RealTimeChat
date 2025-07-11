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

import com.project.realtimechatui.api.ApiClient;
import com.project.realtimechatui.api.models.BaseDTO;
import com.project.realtimechatui.api.models.LoginRequest;
import com.project.realtimechatui.api.models.LoginResponse;
import com.project.realtimechatui.api.models.User;
import com.project.realtimechatui.databinding.ActivityLoginBinding;
import com.project.realtimechatui.utils.SharedPrefManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private SharedPrefManager sharedPrefManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize ViewBinding
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize SharedPrefManager
        sharedPrefManager = SharedPrefManager.getInstance(this);

        // Check if user is already logged in
//        if (sharedPrefManager.isLoggedIn()) {
//            navigateToMain();
//            return;
//        }

        setupUI();
    }

    private void setupUI() {
        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.tvRegister.setOnClickListener(v -> navigateToRegister());
        binding.tvForgotPassword.setOnClickListener(v -> handleForgotPassword());
    }

    private void attemptLogin() {
        String username = binding.etUsername.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        // Reset errors
        binding.etUsername.setError(null);
        binding.etPassword.setError(null);

        // Validate input
        boolean isValid = true;

        if (TextUtils.isEmpty(username)) {
            binding.etUsername.setError("Username is required");
            isValid = false;
        }

        if (TextUtils.isEmpty(password)) {
            binding.etPassword.setError("Password is required");
            isValid = false;
        }

        if (!isValid) {
            return;
        }

        // Show loading
        setLoading(true);

        // Create login request
        LoginRequest loginRequest = new LoginRequest(username, password);

        // Make API call
        ApiClient.getApiService().login(loginRequest).enqueue(new Callback<BaseDTO<LoginResponse>>() {
            @Override
            public void onResponse(Call<BaseDTO<LoginResponse>> call, Response<BaseDTO<LoginResponse>> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<LoginResponse> baseResponse = response.body();

                    if (baseResponse.isSuccess() && baseResponse.getData() != null) {
                        LoginResponse loginResponse = baseResponse.getData();
                        User user = loginResponse.getUser();
                        // Save auth data
                        sharedPrefManager.saveAuthData(
                                loginResponse.getAccessToken(),
                                loginResponse.getRefreshToken(),
                                user
                        );

                        Toast.makeText(LoginActivity.this, "Login successful", Toast.LENGTH_SHORT).show();
                        navigateToMain();
                    } else {
                        // Handle API-level errors (from BaseDTO)
                        String errorMessage = baseResponse.getMessage() != null ?
                                baseResponse.getMessage() : "Login failed";
                        showError(errorMessage);
                    }
                } else {
                    showError("Error Connection: Please check your connection and try again.");
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<LoginResponse>> call, Throwable t) {
                setLoading(false);
                showError("Network error. Please check your connection.");
            }
        });

    }

    private void navigateToRegister() {
        Intent intent = new Intent(this, RegisterActivity.class);
        startActivity(intent);
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void handleForgotPassword() {
        // TODO: Implement forgot password functionality
        Toast.makeText(this, "Forgot password functionality not implemented yet", Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(!isLoading);
        binding.etUsername.setEnabled(!isLoading);
        binding.etPassword.setEnabled(!isLoading);
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