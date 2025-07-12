package com.project.realtimechatui.utils;

import android.util.Log;
import com.project.realtimechatui.api.ApiClient;
import com.project.realtimechatui.api.ApiService;
import com.project.realtimechatui.api.models.BaseDTO;
import com.project.realtimechatui.api.models.User;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthDebugHelper {
    private static final String TAG = "AuthDebug";

    public static void logAuthStatus() {
        SharedPrefManager sharedPref = SharedPrefManager.getInstance();

        Log.d(TAG, "=== AUTH STATUS ===");
        Log.d(TAG, "Is Logged In: " + sharedPref.isLoggedIn());
        Log.d(TAG, "User ID: " + sharedPref.getId());
        Log.d(TAG, "Username: " + sharedPref.getUsername());
        Log.d(TAG, "Access Token: " + (sharedPref.getAccessToken() != null ? "Present" : "NULL"));
        Log.d(TAG, "Token Length: " + (sharedPref.getAccessToken() != null ? sharedPref.getAccessToken().length() : 0));

        if (sharedPref.getAccessToken() != null) {
            Log.d(TAG, "Token Preview: " + sharedPref.getAccessToken().substring(0, Math.min(50, sharedPref.getAccessToken().length())) + "...");
        }
        Log.d(TAG, "==================");
    }

    public static void testTokenValidity() {
        ApiService apiService = ApiClient.getApiService();

        // Test with a simple authenticated endpoint
        Call<BaseDTO<java.util.List<User>>> call = apiService.getAllUsers();
        call.enqueue(new Callback<BaseDTO<java.util.List<User>>>() {
            @Override
            public void onResponse(Call<BaseDTO<java.util.List<User>>> call, Response<BaseDTO<java.util.List<User>>> response) {
                Log.d(TAG, "Token test - Response code: " + response.code());
                if (response.isSuccessful()) {
                    Log.d(TAG, "Token is VALID ✓");
                } else {
                    Log.e(TAG, "Token is INVALID ✗ - Code: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<java.util.List<User>>> call, Throwable t) {
                Log.e(TAG, "Token test failed: " + t.getMessage());
            }
        });
    }
}