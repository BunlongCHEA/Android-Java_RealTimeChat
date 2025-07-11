package com.project.realtimechatui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.project.realtimechatui.api.ApiClient;
import com.project.realtimechatui.api.models.BaseDTO;
import com.project.realtimechatui.api.models.LoginResponse;
import com.project.realtimechatui.api.models.RegisterRequest;
import com.project.realtimechatui.api.models.RegisterResponse;
import com.project.realtimechatui.databinding.ActivityRegisterBinding;
import com.project.realtimechatui.utils.SharedPrefManager;
import com.project.realtimechatui.utils.ValidationUtils;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private SharedPrefManager sharedPrefManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.register), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize ViewBinding
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize SharedPreferences
        sharedPrefManager = SharedPrefManager.getInstance(this);

        // Setup UI components
        setupUI();
    }

    private void setupUI() {
        // Set click listeners
        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.tvLogin.setOnClickListener(v -> navigateToLogin());
        binding.btnBack.setOnClickListener(v -> onBackPressed());
    }

    private void attemptRegister() {
        // Get input values
        String username = binding.etUsername.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String fullName = binding.etFullName.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String confirmPassword = binding.etConfirmPassword.getText().toString().trim();

        // Reset previous errors
        binding.etUsername.setError(null);
        binding.etEmail.setError(null);
        binding.etFullName.setError(null);
        binding.etPassword.setError(null);
        binding.etConfirmPassword.setError(null);

        // Validate input
        boolean isValid = true;

        String usernameError = ValidationUtils.getUsernameError(username);
        if (usernameError != null) {
            binding.etUsername.setError(usernameError);
            isValid = false;
        }

        String emailError = ValidationUtils.getEmailError(email);
        if (emailError != null) {
            binding.etEmail.setError(emailError);
            isValid = false;
        }

        String fullNameError = ValidationUtils.getFullNameError(fullName);
        if (fullNameError != null) {
            binding.etFullName.setError(fullNameError);
            isValid = false;
        }

        String passwordError = ValidationUtils.getPasswordError(password);
        if (passwordError != null) {
            binding.etPassword.setError(passwordError);
            isValid = false;
        }

        if (!password.equals(confirmPassword)) {
            binding.etConfirmPassword.setError("Passwords do not match");
            isValid = false;
        }

        if (!isValid) {
            return;
        }

        // Show loading state
        setLoading(true);

        // Create register request
        RegisterRequest registerRequest = new RegisterRequest(username, email, password, fullName);

        // Make API call
        ApiClient.getApiService().register(registerRequest).enqueue(new Callback<BaseDTO<RegisterResponse>>() {
            @Override
            public void onResponse(Call<BaseDTO<RegisterResponse>> call, Response<BaseDTO<RegisterResponse>> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<RegisterResponse> baseResponse = response.body();

                    if (baseResponse.isSuccess() && baseResponse.getData() != null) {
                        showSuccess("Registration successful! Welcome to RealTime Chat!");

                        // Go back to LoginActivity
                        navigateToLogin();
                    } else {
                        showError("Registration failed. Please try again.");
                    }
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<RegisterResponse>> call, Throwable t) {
                setLoading(false);
                showError("Error Connection: Please check your connection and try again.");
            }
        });

    }

    // Helper method
    private void navigateToLogin() {
//        Intent intent = new Intent(this, LoginActivity.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        startActivity(intent);
        finish();
    }

    private void setLoading(boolean isLoading) {
        // Show/hide progress bar
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);

        // Enable/disable UI elements
        binding.btnRegister.setEnabled(!isLoading);
        binding.etUsername.setEnabled(!isLoading);
        binding.etEmail.setEnabled(!isLoading);
        binding.etFullName.setEnabled(!isLoading);
        binding.etPassword.setEnabled(!isLoading);
        binding.etConfirmPassword.setEnabled(!isLoading);
        binding.btnBack.setEnabled(!isLoading);
        binding.tvLogin.setEnabled(!isLoading);

        // Update button text
        binding.btnRegister.setText(isLoading ? "Creating Account..." : "Register");
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showSuccess(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // Optional: Add custom back button behavior
        navigateToLogin();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up binding reference
        binding = null;
    }
}