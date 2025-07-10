package com.project.realtimechatui.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.project.realtimechatui.api.models.User;

public class SharedPrefManager {
    private static SharedPrefManager instance;
    private static Context context;
    private SharedPreferences sharedPreferences;

    private SharedPrefManager(Context context) {
        SharedPrefManager.context = context;
        sharedPreferences = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SharedPrefManager getInstance(Context context) {
        if (instance == null) {
            instance = new SharedPrefManager(context);
        }
        return instance;
    }

    public static synchronized SharedPrefManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SharedPrefManager not initialized. Call getInstance(Context) first.");
        }
        return instance;
    }

    public void saveAuthData(String token, String refreshToken, User user) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.KEY_ACCESS_TOKEN, token);
        editor.putString(Constants.KEY_REFRESH_TOKEN, refreshToken);
        editor.putLong(Constants.KEY_USER_ID, user.getId());
        editor.putString(Constants.KEY_USERNAME, user.getUsername());
        editor.putString(Constants.KEY_EMAIL, user.getEmail());
        editor.putString(Constants.KEY_FULL_NAME, user.getFullName());
        editor.putBoolean(Constants.KEY_IS_LOGGED_IN, true);
        editor.apply();
    }

    public String getAuthToken() {
        return sharedPreferences.getString(Constants.KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return sharedPreferences.getString(Constants.KEY_REFRESH_TOKEN, null);
    }

    public Long getId() {
        return sharedPreferences.getLong(Constants.KEY_USER_ID, -1L);
    }

    public String getUsername() {
        return sharedPreferences.getString(Constants.KEY_USERNAME, null);
    }

    public String getEmail() {
        return sharedPreferences.getString(Constants.KEY_EMAIL, null);
    }

    public String getFullName() {
        return sharedPreferences.getString(Constants.KEY_FULL_NAME, null);
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(Constants.KEY_IS_LOGGED_IN, false);
    }

    public void clearAuthData() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(Constants.KEY_ACCESS_TOKEN);
        editor.remove(Constants.KEY_REFRESH_TOKEN);
        editor.remove(Constants.KEY_USER_ID);
        editor.remove(Constants.KEY_USERNAME);
        editor.remove(Constants.KEY_EMAIL);
        editor.remove(Constants.KEY_FULL_NAME);
        editor.putBoolean(Constants.KEY_IS_LOGGED_IN, false);
        editor.apply();
    }

    public User getCurrentUser() {
        if (!isLoggedIn()) {
            return null;
        }

        User user = new User();
        user.setId(getId());
        user.setUsername(getUsername());
        user.setEmail(getEmail());
        user.setFullName(getFullName());
        return user;
    }
}
