package com.project.realtimechatui.utils;

public class Constants {
    // API Configuration
    public static final String BASE_URL = "http://10.0.2.2:8080/api/"; // For Android Emulator
    // public static final String BASE_URL = "http://192.168.1.100:8080/api/"; // For Real Device

    // WebSocket Configuration
    public static final String WEBSOCKET_URL = "ws://10.0.2.2:8080/ws";

    // SharedPreferences Keys
    public static final String PREF_NAME = "RealtimeChatPrefs";
    public static final String KEY_ACCESS_TOKEN = "access_token";
    public static final String KEY_REFRESH_TOKEN = "refresh_token";
    public static final String KEY_USER_ID = "id";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_FULL_NAME = "full_name";
    public static final String KEY_IS_LOGGED_IN = "is_logged_in";

    // Request Codes
    public static final int REQUEST_LOGIN = 1001;
    public static final int REQUEST_REGISTER = 1002;

    // Validation Constants
    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_USERNAME_LENGTH = 20;
    public static final int MIN_USERNAME_LENGTH = 3;
}
