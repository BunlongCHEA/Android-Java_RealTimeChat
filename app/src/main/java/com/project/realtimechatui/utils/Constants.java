package com.project.realtimechatui.utils;

public class Constants {
    // API Configuration
    public static final String BASE_URL = "http://10.0.2.2:8080/api/"; // For Android Emulator
    // public static final String BASE_URL = "http://192.168.1.100:8080/api/"; // For Real Device

    // WebSocket Configuration
    public static final String WS_BASE_URL = "ws://10.0.2.2:8080/ws";

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

    // Chat Room Types
    public static final String ROOM_TYPE_PERSONAL = "PERSONAL";  // Changed from PRIVATE
    public static final String ROOM_TYPE_GROUP = "GROUP";
    public static final String ROOM_TYPE_CHANNEL = "CHANNEL";

    // Message Types
    public static final String MESSAGE_TYPE_TEXT = "TEXT";
    public static final String MESSAGE_TYPE_IMAGE = "IMAGE";
    public static final String MESSAGE_TYPE_FILE = "FILE";
    public static final String MESSAGE_TYPE_SYSTEM = "SYSTEM";

    // Message Status
    public static final String MESSAGE_STATUS_SENT = "SENT";
    public static final String MESSAGE_STATUS_DELIVERED = "DELIVERED";
    public static final String MESSAGE_STATUS_READ = "READ";

    // WebSocket Message Types
    public static final String WS_TYPE_MESSAGE = "message";
    public static final String WS_TYPE_TYPING = "typing";
    public static final String WS_TYPE_USER_STATUS = "user_status";
}
