
package com.project.realtimechatui;

import static com.project.realtimechatui.utils.Constants.MAX_IMAGE_SIZE;
import static com.project.realtimechatui.utils.Constants.TYPING_TIMEOUT;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.project.realtimechatui.adapters.ChatMessageAdapter;
import com.project.realtimechatui.api.ApiService;
import com.project.realtimechatui.api.ApiClient;
import com.project.realtimechatui.api.models.BaseDTO;
import com.project.realtimechatui.api.models.ChatMessage;
import com.project.realtimechatui.api.models.ChatRoom;
import com.project.realtimechatui.api.models.Participant;
import com.project.realtimechatui.enums.EnumRoomType;
import com.project.realtimechatui.utils.Constants;
import com.project.realtimechatui.utils.SharedPrefManager;
import com.project.realtimechatui.websocket.WebSocketChatManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity implements
        WebSocketChatManager.ChatMessageListener,
        WebSocketChatManager.ConnectionListener {

    private static final String TAG = "ChatActivity";

    // UI Components
    private TextView tvUserName, tvUserStatus, tvConnectionStatus, tvTypingIndicator;
    private ImageView ivBack, ivUserProfile;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private CardView cvSend, cvAttachment;

    // User and Chat Data
    private Long targetUserId;
    private String targetUsername;
    private String targetFullName;
    private String targetProfilePicture;
    private Long chatRoomId;

    // Services and Managers
    private WebSocketChatManager webSocketManager;
    private ApiService apiService;
    private SharedPrefManager sharedPrefManager;
    private ChatMessageAdapter messageAdapter;
    private LinearLayoutManager layoutManager;
    private Handler typingHandler;
    private Set<String> typingUsers;
    private boolean isTyping = false;
    private Runnable stopTypingRunnable;

    // For duplicate prevention
    private boolean isSendingMessage = false;
    private Set<Long> receivedMessageIds = new HashSet<>();
    private String lastSentContent = "";
    private long lastSentTime = 0;

    // Image handling
    private ActivityResultLauncher<Intent> imagePickerLauncher;
//    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<String[]> multiplePermissionLauncher;
//    private ActivityResultLauncher<Boolean> cameraLauncher;
private ActivityResultLauncher<Uri> cameraLauncher;
    private Uri currentPhotoUri;
    private String pendingAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        sharedPrefManager = SharedPrefManager.getInstance();
        typingHandler = new Handler(Looper.getMainLooper());
        typingUsers = new HashSet<>();

        initViews();
        setupImageHandling();
        getIntentData();
        setupRecyclerView();
        setupWebSocket();
        setupApiService();
        setupMessageInput();

        // Check if we need to find existing chat room first
//        findExistingChatRoom();

        // Enhanced: Check if we have chat_room_id from MainActivity
        chatRoomId = getIntent().getLongExtra("chat_room_id", -1);
        if (chatRoomId != -1) {
            // Existing chat room - join and load messages
            Log.d(TAG, "Using existing chat room: " + chatRoomId);
            joinChatRoom();
            loadChatMessages();
        } else {
            // New personal chat - find or create chat room
            Log.d(TAG, "Finding or creating personal chat room");
            findOrCreatePersonalChatRoom();
        }
    }

    private void initViews() {
        tvUserName = findViewById(R.id.tvUserName);
        tvUserStatus = findViewById(R.id.tvUserStatus);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvTypingIndicator = findViewById(R.id.tvTypingIndicator);
        ivBack = findViewById(R.id.ivBack);
        ivUserProfile = findViewById(R.id.ivUserProfile);
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        cvSend = findViewById(R.id.cvSend);
        cvAttachment = findViewById(R.id.cvAttachment);

        ivBack.setOnClickListener(v -> finish());

        // Image attachment button click listener
        cvAttachment.setOnClickListener(v -> showImagePickerDialog());

        // Initially hide typing indicator
        tvTypingIndicator.setVisibility(View.GONE);

        // Update connection status
        updateConnectionStatus(Constants.CONNECTION_STATE_CONNECTING);
    }

    // Handle image logic for add and send image to chat
    private void setupImageHandling() {
        // Setup image picker launcher
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "Image picker result received");

                    // Ensure WebSocket is connected after permission dialog
                    ensureWebSocketConnection();

                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            handleSelectedImage(imageUri);
                        }
                    }
                });

        // Setup camera launcher
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    Log.d(TAG, "Camera result received: " + success);

                    // Ensure WebSocket is connected after permission dialog
                    ensureWebSocketConnection();

                    if (success && currentPhotoUri != null) {
                        handleSelectedImage(currentPhotoUri);
                    }
                });

        // Setup permission launcher
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    Log.d(TAG, "Camera permission result: " + isGranted);

                    // Ensure WebSocket is connected after permission dialog
                    ensureWebSocketConnection();

                    if (isGranted) {
                        openCamera();
                    } else {
                        showError("Camera permission is required to take photos");
                    }
                });

        // Setup multiple permissions launcher for gallery (Android 13+)
        multiplePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Log.d(TAG, "Multiple permissions result: " + result);
                    ensureWebSocketConnection();

                    boolean hasImagePermission = false;
                    boolean hasCameraPermission = Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA));

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Android 13+
                        hasImagePermission = Boolean.TRUE.equals(result.get(Manifest.permission.READ_MEDIA_IMAGES));
                    } else {
                        // Android 12 and below
                        hasImagePermission = Boolean.TRUE.equals(result.get(Manifest.permission.READ_EXTERNAL_STORAGE));
                    }

                    if (pendingAction != null) {
                        switch (pendingAction) {
                            case "gallery":
                                if (hasImagePermission) {
                                    openGallery();
                                } else {
                                    showError("Storage permission is required to access photos");
                                }
                                break;
                            case "camera":
                                if (hasCameraPermission) {
                                    openCamera();
                                } else {
                                    showError("Camera permission is required to take photos");
                                }
                                break;
                        }
                        pendingAction = null;
                    }
                });
    }

    // Add this helper method
    private void ensureWebSocketConnection() {
        if (!webSocketManager.isConnected()) {
            Log.d(TAG, "WebSocket disconnected, reconnecting...");
            webSocketManager.connect();

            // Wait and rejoin chat room
            if (chatRoomId != null) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (webSocketManager.isConnected()) {
                        webSocketManager.subscribeToChatRoom(chatRoomId);
                        webSocketManager.joinChatRoom(chatRoomId);
                        updateConnectionStatus(Constants.CONNECTION_STATE_CONNECTED);
                    }
                }, 1500);
            }
        }
    }

    private void showImagePickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Image");

        String[] options = {"Gallery", "Camera"};
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    checkGalleryPermissionAndOpen();
                    break;
                case 1:
                    checkCameraPermissionAndOpen();
                    break;
            }
        });

        builder.show();
    }

    private void checkGalleryPermissionAndOpen() {
        if (hasStoragePermission()) {
            openGallery();
        } else {
            pendingAction = "gallery";
            requestStoragePermission();
        }
    }

    private void openGallery() {
        Intent intent;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Use ACTION_OPEN_DOCUMENT for better compatibility with modern Android
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");

            // Add extra MIME types for better compatibility
            String[] mimeTypes = {"image/jpeg", "image/png", "image/gif", "image/webp"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        } else {
            // Fallback for older versions
            intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
        }

        imagePickerLauncher.launch(intent);
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) - Granular media permissions
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6+ (API 23+) - Runtime permissions
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            // Below Android 6 - permissions granted at install time
            return true;
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - Request new granular media permissions
            String[] permissions = {
                    Manifest.permission.READ_MEDIA_IMAGES
            };
            multiplePermissionLauncher.launch(permissions);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6+ - Request legacy storage permission
            String[] permissions = {
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
            multiplePermissionLauncher.launch(permissions);
        } else {
            // Below Android 6 - no runtime permissions needed
            openGallery();
        }
    }

    private void checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingAction = "camera";
                String[] permissions = {Manifest.permission.CAMERA};
                multiplePermissionLauncher.launch(permissions);
            } else {
                // Below Android 6 - permissions granted at install time
                openCamera();
            }
        }
    }

    private void openCamera() {
        try {
            // Create a temporary file for the photo
            currentPhotoUri = createImageFileUri();
            if (currentPhotoUri != null) {
                cameraLauncher.launch(currentPhotoUri);
            } else {
                showError("Unable to create temporary file for camera");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            showError("Camera not available: " + e.getMessage());
        }
    }

    private Uri createImageFileUri() {
        try {
            // Create an image file name with timestamp
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Use MediaStore for scoped storage
                ContentResolver resolver = getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, imageFileName + ".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

                return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            } else {
                // Android 9 and below - Use external storage
                File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                if (storageDir != null) {
                    File imageFile = File.createTempFile(imageFileName, ".jpg", storageDir);
                    return FileProvider.getUriForFile(this,
                            getPackageName() + ".fileprovider", imageFile);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating image file URI", e);
        }
        return null;
    }

    private void handleSelectedImage(Uri imageUri) {
        try {
            // Handle both content:// and file:// URIs
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            if (inputStream != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();

                if (bitmap != null) {
                    // Get filename from URI
                    String filename = getFileNameFromUri(imageUri);
                    sendImageMessage(bitmap, filename);
                } else {
                    showError("Failed to decode image");
                }
            } else {
                showError("Failed to open image stream");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading selected image", e);
            showError("Failed to load selected image: " + e.getMessage());
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String filename = "image.jpg";

        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        String displayName = cursor.getString(nameIndex);
                        if (displayName != null && !displayName.isEmpty()) {
                            filename = displayName;
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error getting filename from URI", e);
            }
        } else if (uri.getPath() != null) {
            filename = new File(uri.getPath()).getName();
        }

        // Ensure proper extension
        if (!filename.toLowerCase().endsWith(".jpg") &&
                !filename.toLowerCase().endsWith(".jpeg") &&
                !filename.toLowerCase().endsWith(".png")) {
            filename += ".jpg";
        }

        return filename;
    }

    private void handleCameraImage(Bitmap bitmap) {
        if (bitmap != null) {
            sendImageMessage(bitmap, "camera_image.jpg");
        }
    }

    private void sendImageMessage(Bitmap bitmap, String filename) {
        if (chatRoomId == null || !webSocketManager.isConnected()) {
            showError("Not connected to chat server");
            return;
        }

        if (isSendingMessage) {
            return;
        }

        isSendingMessage = true;

        try {
            // Compress and convert bitmap to base64
            Bitmap compressedBitmap = compressImage(bitmap);
            String base64Image = bitmapToBase64(compressedBitmap);
            String contentType = "image/jpeg";

            // Create message payload for WebSocket
            Map<String, Object> messagePayload = new HashMap<>();
            messagePayload.put("imageData", "data:" + contentType + ";base64," + base64Image);
            messagePayload.put("filename", filename);
            messagePayload.put("contentType", contentType);

            // Send via WebSocket
            webSocketManager.sendImageMessage(chatRoomId, messagePayload);

            Log.d(TAG, "Image message sent via WebSocket");

        } catch (Exception e) {
            Log.e(TAG, "Error sending image message", e);
            showError("Failed to send image: " + e.getMessage());
        } finally {
            // Reset sending state after a delay
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                isSendingMessage = false;
            }, 2000);
        }
    }

    private Bitmap compressImage(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();

        // Calculate scaling factor
        float scaleFactor = Math.min(
                (float) MAX_IMAGE_SIZE / width,
                (float) MAX_IMAGE_SIZE / height
        );

        if (scaleFactor < 1.0f) {
            int newWidth = Math.round(width * scaleFactor);
            int newHeight = Math.round(height * scaleFactor);
            return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
        }

        return original;
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        // Use higher quality for smaller images, lower for larger ones
        int quality = bitmap.getByteCount() > 1000000 ? 70 : 85; // 1MB threshold
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream);

        byte[] byteArray = byteArrayOutputStream.toByteArray();

        try {
            byteArrayOutputStream.close();
        } catch (IOException e) {
            Log.w(TAG, "Error closing ByteArrayOutputStream", e);
        }

        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }


    // Get intend data from user
    private void getIntentData() {
        targetUserId = getIntent().getLongExtra("user_id", -1);
        targetUsername = getIntent().getStringExtra("username");
        targetFullName = getIntent().getStringExtra("full_name");
        targetProfilePicture = getIntent().getStringExtra("profile_picture");

        Log.d(TAG, "Target User ID: " + targetUserId);
        Log.d(TAG, "Target Username: " + targetUsername);

        // Validate required data for personal chat
        if (targetUserId == -1 || TextUtils.isEmpty(targetUsername)) {
            showError("Invalid user data");
            finish();
            return;
        }

        // Prevent chatting with self
        Long currentUserId = sharedPrefManager.getId();
        if (currentUserId != null && currentUserId.equals(targetUserId)) {
            showError("Cannot chat with yourself");
            finish();
            return;
        }

        tvUserName.setText(!TextUtils.isEmpty(targetFullName) ? targetFullName : "@" + targetUsername);
        tvUserStatus.setText("Online");
    }

    // Go to ChatMessageAdapter for chat content and position
    private void setupRecyclerView() {
        messageAdapter = new ChatMessageAdapter(this);
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // Start from bottom

        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(messageAdapter);

        // Scroll to bottom when new message is added
        messageAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                scrollToBottom();
            }
        });
    }

    private void setupWebSocket() {
        webSocketManager = WebSocketChatManager.getInstance();
        webSocketManager.setMessageListener(this);
        webSocketManager.setConnectionListener(this);

        if (!webSocketManager.isConnected()) {
            webSocketManager.connect();
        }
    }

    private void setupApiService() {
        apiService = ApiClient.getApiService();
    }

    private void setupMessageInput() {
        // Send button click listener
        cvSend.setOnClickListener(v -> sendMessage());

        // Text change listener for typing indicator
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (chatRoomId != null && webSocketManager.isConnected()) {
                    if (s.toString().trim().length() > 0 && !isTyping) {
                        // Start typing
                        isTyping = true;
                        webSocketManager.sendTypingIndicator(chatRoomId, true);
                        scheduleStopTyping();
                    } else if (s.toString().trim().length() == 0 && isTyping) {
                        // Stop typing
                        stopTyping();
                    } else if (isTyping) {
                        // Reset the stop typing timer
                        scheduleStopTyping();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void scheduleStopTyping() {
        // Remove previous runnable
        if (stopTypingRunnable != null) {
            typingHandler.removeCallbacks(stopTypingRunnable);
        }

        // Schedule new runnable
        stopTypingRunnable = this::stopTyping;
        typingHandler.postDelayed(stopTypingRunnable, TYPING_TIMEOUT);
    }

    private void stopTyping() {
        if (isTyping && chatRoomId != null && webSocketManager.isConnected()) {
            isTyping = false;
            webSocketManager.sendTypingIndicator(chatRoomId, false);
        }
        if (stopTypingRunnable != null) {
            typingHandler.removeCallbacks(stopTypingRunnable);
            stopTypingRunnable = null;
        }
    }

    private void findOrCreatePersonalChatRoom() {
        Long currentUserId = sharedPrefManager.getId();

        if (currentUserId == null || targetUserId == null || targetUserId == -1) {
            showError("Invalid user data");
            return;
        }

        Log.d(TAG, "Looking for existing personal chat between user " + currentUserId + " and " + targetUserId);

        // Use getChatRoomsByUserId to get only user's chat rooms
        Call<BaseDTO<List<ChatRoom>>> call = apiService.getChatRoomsByUserId(currentUserId);
        call.enqueue(new Callback<BaseDTO<List<ChatRoom>>>() {
            @Override
            public void onResponse(Call<BaseDTO<List<ChatRoom>>> call, Response<BaseDTO<List<ChatRoom>>> response) {
                Log.d(TAG, "Response code: " + response.code());

                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<List<ChatRoom>> result = response.body();
                    if (result.isSuccess() && result.getData() != null) {
                        List<ChatRoom> userChatRooms = result.getData();
                        Log.d(TAG, "Found " + userChatRooms.size() + " chat rooms for user " + currentUserId);

                        // Filter for personal chats only to improve performance
                        List<ChatRoom> personalChats = userChatRooms.stream()
                                .filter(room -> room.getType() == EnumRoomType.PERSONAL)
                                .collect(Collectors.toList());

                        ChatRoom existingRoom = findPersonalChatRoom(personalChats, currentUserId, targetUserId);
                        if (existingRoom != null) {
                            // Found existing chat room
                            chatRoomId = existingRoom.getId();
                            Log.d(TAG, "✅ Found existing Personal chat room: " + chatRoomId + " for users " + currentUserId + " and " + targetUserId);

                            // Clear any existing messages before loading
                            messageAdapter.clearMessages();
                            joinChatRoom();
                            loadChatMessages();
                            return;
                        } else {
                            Log.d(TAG, "❌ No existing personal chat found, creating new one");
                            createPersonalChatRoom();
                        }
                    } else {
                        Log.e(TAG, "API returned error: " + (result != null ? result.getMessage() : "null result"));
                        createPersonalChatRoom();
                    }
                } else {
                    Log.e(TAG, "Failed to get user chat rooms: " + response.code());
                    createPersonalChatRoom();
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<List<ChatRoom>>> call, Throwable t) {
                Log.e(TAG, "Network error getting chat rooms", t);
                showError("Network error: " + t.getMessage());
            }
        });
    }

    private ChatRoom findPersonalChatRoom(List<ChatRoom> chatRooms, Long currentUserId, Long targetUserId) {
        Log.d(TAG, "🔍 Searching for personal chat between " + currentUserId + " and " + targetUserId);

        for (ChatRoom room : chatRooms) {
            Log.d(TAG, "Checking room: " + room.getId() + ", type: " + room.getType() + ", name: " + room.getName());

            if (room.getType() == EnumRoomType.PERSONAL && room.getParticipants() != null) {

                // Count participants and collect their IDs
                Set<Long> participantIds = new HashSet<>();
                for (Participant participant : room.getParticipants()) {
                    if (participant != null && participant.getUserId() != null) {
                        participantIds.add(participant.getUserId());
                        Log.d(TAG, "  Found participant: " + participant.getUserId());
                    }
                }

                // For personal chat, must have exactly 2 participants: current user and target user
                if (participantIds.size() == 2 &&
                        participantIds.contains(currentUserId) &&
                        participantIds.contains(targetUserId)) {
                    return room;
                }
            }
        }

        Log.d(TAG, "❌ No matching personal chat room found");
        return null;
    }

    private void createPersonalChatRoom() {
        Long currentUserId = sharedPrefManager.getId();

        if (currentUserId == null) {
            showError("User not logged in");
            return;
        }

        // Create ChatRoom DTO for personal chat
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setType(EnumRoomType.PERSONAL);
        chatRoom.setName(targetUsername); // Backend will handle the name

        // Add target user as participant
        Set<Participant> participants = new HashSet<>();
        Participant targetParticipant = new Participant();
        targetParticipant.setUserId(targetUserId);
        participants.add(targetParticipant);
        chatRoom.setParticipants(participants);

        Log.d(TAG, "Creating personal chat room with user: " + targetUserId);

        Call<BaseDTO<ChatRoom>> call = apiService.createChatRoom(chatRoom, currentUserId);
        call.enqueue(new Callback<BaseDTO<ChatRoom>>() {
            @Override
            public void onResponse(Call<BaseDTO<ChatRoom>> call, Response<BaseDTO<ChatRoom>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<ChatRoom> result = response.body();
                    if (result.isSuccess() && result.getData() != null) {
                        chatRoomId = result.getData().getId();
                        Log.d(TAG, "Created chat room: " + chatRoomId);
                        joinChatRoom();
                        loadChatMessages();
                    } else {
                        showError("Failed to create chat room: " + result.getMessage());
                    }
                } else {
                    if (response.code() == 400 && response.errorBody() != null) {
                        try {
                            String errorBody = response.errorBody().string();
                            if (errorBody.contains("personal chat already exists")) {
                                // Chat room already exists, try to find it again
                                findOrCreatePersonalChatRoom();
                                return;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing error response", e);
                        }
                    }
                    showError("Failed to create chat room");
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<ChatRoom>> call, Throwable t) {
                Log.e(TAG, "Failed to create chat room", t);
                showError("Network error: " + t.getMessage());
            }
        });
    }

    private void joinChatRoom() {
        if (chatRoomId != null && webSocketManager.isConnected()) {
            webSocketManager.subscribeToChatRoom(chatRoomId);
            webSocketManager.joinChatRoom(chatRoomId);
            updateConnectionStatus(Constants.CONNECTION_STATE_CONNECTED);
        }
    }

    private void loadChatMessages() {
        if (chatRoomId == null) return;

        Call<BaseDTO<List<ChatMessage>>> call = apiService.getMessagesByChatRoom(chatRoomId, 0, 50);
        call.enqueue(new Callback<BaseDTO<List<ChatMessage>>>() {
            @Override
            public void onResponse(Call<BaseDTO<List<ChatMessage>>> call, Response<BaseDTO<List<ChatMessage>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseDTO<List<ChatMessage>> result = response.body();
                    if (result.isSuccess() && result.getData() != null) {
                        List<ChatMessage> messages = result.getData();

                        // Sort messages by timestamp (oldest first, latest at bottom) - ADD THIS
                        Collections.sort(messages, new Comparator<ChatMessage>() {
                            @Override
                            public int compare(ChatMessage m1, ChatMessage m2) {
                                try {
                                    // Parse timestamps and compare
                                    long time1 = parseTimestamp(m1.getTimestamp());
                                    long time2 = parseTimestamp(m2.getTimestamp());
                                    return Long.compare(time1, time2); // Ascending order (oldest first)
                                } catch (Exception e) {
                                    return 0;
                                }
                            }
                        });

                        messageAdapter.setMessages(messages);
                        scrollToBottom();
                    }
                }
            }

            @Override
            public void onFailure(Call<BaseDTO<List<ChatMessage>>> call, Throwable t) {
                Log.e(TAG, "Failed to load messages", t);
            }
        });
    }

    private long parseTimestamp(String timestamp) {
        try {
            if (TextUtils.isEmpty(timestamp)) {
                return 0;
            }

            // If timestamp is already in milliseconds (13 digits)
            if (timestamp.matches("\\d{13}")) {
                return Long.parseLong(timestamp);
            }

            // If timestamp is in seconds (10 digits), convert to milliseconds
            if (timestamp.matches("\\d{10}")) {
                return Long.parseLong(timestamp) * 1000;
            }

            // If timestamp is in ISO format, parse it
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC")); // Parse as UTC
            Date date = sdf.parse(timestamp);
            return date != null ? date.getTime() : System.currentTimeMillis();

        } catch (Exception e) {
            Log.e(TAG, "Error parsing timestamp: " + timestamp, e);
            return System.currentTimeMillis();
        }
    }

    private void sendMessage() {
        String messageText = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(messageText) || chatRoomId == null) {
            return;
        }

        if (!webSocketManager.isConnected()) {
            showError("Not connected to chat server");
            return;
        }

        // Prevent duplicate sends
        if (isSendingMessage) {
            return;
        }

        // Prevent sending same message within 2 seconds
        long currentTime = System.currentTimeMillis();
        if (messageText.equals(lastSentContent) && (currentTime - lastSentTime) < 2000) {
            Log.d(TAG, "Preventing duplicate message send");
            return;
        }

        // Set sending state - ADD THIS
        isSendingMessage = true;
        lastSentContent = messageText;
        lastSentTime = currentTime;

        // Stop typing indicator
        stopTyping();

        // Send message via WebSocket
        webSocketManager.sendMessage(chatRoomId, messageText);

        // Clear the input
        etMessage.setText("");

        // Reset sending state after a delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isSendingMessage = false;
        }, 1000);
    }

    private void scrollToBottom() {
        if (messageAdapter.getItemCount() > 0) {
            rvMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
        }
    }

    private void updateConnectionStatus(String status) {
        tvConnectionStatus.setText(status);
        switch (status) {
            case Constants.CONNECTION_STATE_CONNECTED:
                tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                break;
            case Constants.CONNECTION_STATE_CONNECTING:
                tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                break;
            case Constants.CONNECTION_STATE_DISCONNECTED:
                tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                break;
        }
    }

    private void updateTypingIndicator() {
        if (typingUsers.isEmpty()) {
            tvTypingIndicator.setVisibility(View.GONE);
        } else {
            tvTypingIndicator.setVisibility(View.VISIBLE);
            if (typingUsers.size() == 1) {
                tvTypingIndicator.setText(typingUsers.iterator().next() + " is typing...");
            } else {
                tvTypingIndicator.setText("Multiple users are typing...");
            }
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // WebSocketChatManager.ChatMessageListener implementation
    @Override
    public void onMessageReceived(ChatMessage message) {
        // Prevent duplicate messages
        if (message.getId() != null && receivedMessageIds.contains(message.getId())) {
            Log.d(TAG, "Duplicate message received, ignoring: " + message.getId());
            return;
        }

        // Add to received set
        if (message.getId() != null) {
            receivedMessageIds.add(message.getId());

            // Keep only last 1000 message IDs to prevent memory issues
            if (receivedMessageIds.size() > 1000) {
                Iterator<Long> iterator = receivedMessageIds.iterator();
                for (int i = 0; i < 500 && iterator.hasNext(); i++) {
                    iterator.next();
                    iterator.remove();
                }
            }
        }

        runOnUiThread(() -> {
            messageAdapter.addMessage(message);
            scrollToBottom();
        });
    }

    @Override
    public void onTypingIndicator(String username, boolean isTyping) {
        runOnUiThread(() -> {
            if (isTyping) {
                typingUsers.add(username);
            } else {
                typingUsers.remove(username);
            }
            updateTypingIndicator();
        });
    }

    @Override
    public void onUserStatusChanged(Long userId, boolean isOnline) {
        runOnUiThread(() -> {
            if (userId.equals(targetUserId)) {
                tvUserStatus.setText(isOnline ? "Online" : "Offline");
            }
        });
    }

    @Override
    public void onMessageEdited(Long messageId, String newContent) {
        runOnUiThread(() -> messageAdapter.updateMessage(messageId, newContent));
    }

    @Override
    public void onMessageDeleted(Long messageId) {
        runOnUiThread(() -> messageAdapter.removeMessage(messageId));
    }

    @Override
    public void onUserJoined(String username) {
        runOnUiThread(() -> {
            // Handle user joined event if needed
            Log.d(TAG, "User joined: " + username);
        });
    }

    @Override
    public void onUserLeft(String username) {
        runOnUiThread(() -> {
            // Handle user left event if needed
            Log.d(TAG, "User left: " + username);
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> showError("Chat error: " + error));
    }

    // WebSocketChatManager.ConnectionListener implementation
    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            updateConnectionStatus(Constants.CONNECTION_STATE_CONNECTED);
            if (chatRoomId != null) {
                joinChatRoom();
            }
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            updateConnectionStatus(Constants.CONNECTION_STATE_DISCONNECTED);
            // Stop typing indicator
            stopTyping();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "ChatActivity onResume - checking WebSocket connection");

        if (!webSocketManager.isConnected()) {
            Log.d(TAG, "WebSocket not connected, attempting reconnection");
            webSocketManager.connect();
        }

        // Rejoin chat room after reconnection
        if (chatRoomId != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (webSocketManager.isConnected()) {
                    webSocketManager.subscribeToChatRoom(chatRoomId);
                    webSocketManager.joinChatRoom(chatRoomId);
                }
            }, 1000); // Wait 1 second for connection to establish
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "ChatActivity onPause - stopping typing indicator");

        // Stop typing indicator when leaving
        stopTyping();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up typing handler
        if (stopTypingRunnable != null) {
            typingHandler.removeCallbacks(stopTypingRunnable);
        }

        // Leave chat room
        if (chatRoomId != null && webSocketManager.isConnected()) {
            webSocketManager.leaveChatRoom(chatRoomId);
            webSocketManager.unsubscribeFromChatRoom(chatRoomId);
        }
    }

    // Add this method to handle image selection result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Ensure WebSocket is reconnected after returning from gallery
        if (!webSocketManager.isConnected()) {
            Log.d(TAG, "Reconnecting WebSocket after returning from external activity");
            webSocketManager.connect();

            // Rejoin chat room
            if (chatRoomId != null) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (webSocketManager.isConnected()) {
                        webSocketManager.subscribeToChatRoom(chatRoomId);
                        webSocketManager.joinChatRoom(chatRoomId);
                    }
                }, 1000);
            }
        }
    }
}
