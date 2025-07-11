package com.project.realtimechatui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.project.realtimechatui.databinding.ActivityNewChatBinding;

public class NewChatActivity extends AppCompatActivity {

    private ActivityNewChatBinding binding;

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

        setupUI();
    }

    private void setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("New Chat");
        }
        
        // Setup click listeners
        binding.btnCreateChat.setOnClickListener(v -> createNewChat());
        binding.btnJoinChat.setOnClickListener(v -> joinExistingChat());
    }

    private void createNewChat() {
        String chatName = binding.etChatName.getText().toString().trim();
        String chatDescription = binding.etChatDescription.getText().toString().trim();
        
        if (TextUtils.isEmpty(chatName)) {
            binding.etChatName.setError("Chat name is required");
            return;
        }
        
        // TODO: Implement API call to create new chat room
        showLoading(true);
        
        // For now, just show success message and return to main activity
        Toast.makeText(this, "Chat room creation will be implemented with the backend API", Toast.LENGTH_LONG).show();
        showLoading(false);
        finish();
    }

    private void joinExistingChat() {
        String chatId = binding.etChatId.getText().toString().trim();
        
        if (TextUtils.isEmpty(chatId)) {
            binding.etChatId.setError("Chat ID is required");
            return;
        }
        
        // TODO: Implement API call to join existing chat room
        showLoading(true);
        
        // For now, just show success message and return to main activity
        Toast.makeText(this, "Join chat feature will be implemented with the backend API", Toast.LENGTH_LONG).show();
        showLoading(false);
        finish();
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnCreateChat.setEnabled(!isLoading);
        binding.btnJoinChat.setEnabled(!isLoading);
        binding.etChatName.setEnabled(!isLoading);
        binding.etChatDescription.setEnabled(!isLoading);
        binding.etChatId.setEnabled(!isLoading);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}