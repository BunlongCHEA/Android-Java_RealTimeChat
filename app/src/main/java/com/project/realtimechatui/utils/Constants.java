package com.project.realtimechatui.utils;

public class Constants {
    // API Configuration
    public static final String BASE_URL = "http://10.0.2.2:8080/api/"; // For Android Emulator
    // public static final String BASE_URL = "http://192.168.1.100:8080/api/"; // For Real Device

    // WebSocket Configuration
    public static final String WS_BASE_URL = "ws://10.0.2.2:8080/ws";

    // WebSocket Topics and Destinations
    public static final String WS_CHAT_TOPIC = "/topic/chat/";
    public static final String WS_TYPING_TOPIC = "/typing";
    public static final String WS_EVENTS_TOPIC = "/events";
    public static final String WS_ERROR_QUEUE = "/queue/errors";
    public static final String WS_SEND_MESSAGE = "/app/chat.sendMessage/";
    public static final String WS_TYPING_INDICATOR = "/app/chat.typing/";
    public static final String WS_JOIN_ROOM = "/app/chat.join/";
    public static final String WS_LEAVE_ROOM = "/app/chat.leave/";

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
    public static final String WS_TYPE_USER_JOINED = "user_joined";
    public static final String WS_TYPE_USER_LEFT = "user_left";
    public static final String WS_TYPE_MESSAGE_UPDATED = "message_updated";
    public static final String WS_TYPE_MESSAGE_DELETED = "message_deleted";

    // Connection States
    public static final String CONNECTION_STATE_CONNECTING = "CONNECTING";
    public static final String CONNECTION_STATE_CONNECTED = "CONNECTED";
    public static final String CONNECTION_STATE_DISCONNECTED = "DISCONNECTED";
    public static final String CONNECTION_STATE_ERROR = "ERROR";

    // Typing Indicator Constants
    public static final long TYPING_INDICATOR_DELAY = 3000; // 3 seconds
    public static final int HEARTBEAT_INTERVAL = 10000; // 10 seconds
}
