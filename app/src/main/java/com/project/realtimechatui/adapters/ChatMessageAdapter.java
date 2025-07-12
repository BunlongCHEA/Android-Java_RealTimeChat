package com.project.realtimechatui.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.project.realtimechatui.R;
import com.project.realtimechatui.api.models.ChatMessage;
import com.project.realtimechatui.utils.Constants;
import com.project.realtimechatui.utils.SharedPrefManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_MESSAGE_SENT = 1;
    private static final int VIEW_TYPE_MESSAGE_RECEIVED = 2;
    private static final int VIEW_TYPE_MESSAGE_SYSTEM = 3;

    private Context context;
    private List<ChatMessage> messages;
    private SharedPrefManager sharedPrefManager;
    private SimpleDateFormat timeFormat;

    public ChatMessageAdapter(Context context) {
        this.context = context;
        this.messages = new ArrayList<>();
        this.sharedPrefManager = SharedPrefManager.getInstance();
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);

        if (message.isSystemMessage()) {
            return VIEW_TYPE_MESSAGE_SYSTEM;
        } else if (message.isFromCurrentUser(sharedPrefManager.getId())) {
            return VIEW_TYPE_MESSAGE_SENT;
        } else {
            return VIEW_TYPE_MESSAGE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);

        switch (viewType) {
            case VIEW_TYPE_MESSAGE_SENT:
                return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false));
            case VIEW_TYPE_MESSAGE_RECEIVED:
                return new ReceivedMessageViewHolder(inflater.inflate(R.layout.item_message_received, parent, false));
            case VIEW_TYPE_MESSAGE_SYSTEM:
                return new SystemMessageViewHolder(inflater.inflate(R.layout.item_message_system, parent, false));
            default:
                throw new IllegalArgumentException("Unknown view type: " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);

        switch (holder.getItemViewType()) {
            case VIEW_TYPE_MESSAGE_SENT:
                ((SentMessageViewHolder) holder).bind(message);
                break;
            case VIEW_TYPE_MESSAGE_RECEIVED:
                ((ReceivedMessageViewHolder) holder).bind(message);
                break;
            case VIEW_TYPE_MESSAGE_SYSTEM:
                ((SystemMessageViewHolder) holder).bind(message);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // Public methods for managing messages
    public void setMessages(List<ChatMessage> messages) {
        this.messages.clear();
        if (messages != null) {
            this.messages.addAll(messages);
        }
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        if (message != null) {
            messages.add(message);
            notifyItemInserted(messages.size() - 1);
        }
    }

    public void updateMessage(Long messageId, String newContent) {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.getId().equals(messageId)) {
                message.setContent(newContent);
                message.setEdited(true);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void removeMessage(Long messageId) {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.getId().equals(messageId)) {
                messages.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    private boolean isDuplicateMessage(ChatMessage newMessage) {
        if (newMessage.getId() == null) {
            return false;
        }

        for (ChatMessage existingMessage : messages) {
            if (newMessage.getId().equals(existingMessage.getId())) {
                return true;
            }
        }
        return false;
    }

    private String formatTimestamp(String timestamp) {
        try {
            if (TextUtils.isEmpty(timestamp)) {
                return "";
            }

            // Try to parse as milliseconds first
            long timestampLong;
            if (timestamp.length() == 13) {
                timestampLong = Long.parseLong(timestamp);
            } else {
                // Try to parse as ISO format
                timestampLong = System.currentTimeMillis(); // fallback
            }

            Date date = new Date(timestampLong);
            return timeFormat.format(date);
        } catch (Exception e) {
            return "";
        }
    }

    // ViewHolder for sent messages (right side)
    public class SentMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView tvMessageContent;
        private TextView tvTimestamp;
        private TextView tvEditedIndicator;
        private ImageView ivMessageStatus;

        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessageContent = itemView.findViewById(R.id.tvMessageContent);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvEditedIndicator = itemView.findViewById(R.id.tvEditedIndicator);
            ivMessageStatus = itemView.findViewById(R.id.ivMessageStatus);
        }

        public void bind(ChatMessage message) {
            tvMessageContent.setText(message.getContent());
            tvTimestamp.setText(formatTimestamp(message.getTimestamp()));

            // Show edited indicator
            if (message.isEdited()) {
                tvEditedIndicator.setVisibility(View.VISIBLE);
            } else {
                tvEditedIndicator.setVisibility(View.GONE);
            }

            // Set message status icon (you can enhance this based on your needs)
            ivMessageStatus.setImageResource(R.drawable.ic_check);

            // Handle long click for message options (edit, delete, etc.)
            itemView.setOnLongClickListener(v -> {
                // TODO: Show message options dialog
                return true;
            });
        }
    }

    // ViewHolder for received messages (left side)
    public class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView tvSenderName;
        private TextView tvMessageContent;
        private TextView tvTimestamp;
        private TextView tvEditedIndicator;
        private ImageView ivUserAvatar;

        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvMessageContent = itemView.findViewById(R.id.tvMessageContent);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvEditedIndicator = itemView.findViewById(R.id.tvEditedIndicator);
            ivUserAvatar = itemView.findViewById(R.id.ivUserAvatar);
        }

        public void bind(ChatMessage message) {
            // Set sender name
            String senderName = message.getSenderName();
            if (TextUtils.isEmpty(senderName)) {
                senderName = "User " + message.getSenderId();
            }
            tvSenderName.setText(senderName);

            tvMessageContent.setText(message.getContent());
            tvTimestamp.setText(formatTimestamp(message.getTimestamp()));

            // Show edited indicator
            if (message.isEdited()) {
                tvEditedIndicator.setVisibility(View.VISIBLE);
            } else {
                tvEditedIndicator.setVisibility(View.GONE);
            }

            // Load user avatar (you can use Glide here)
            // Glide.with(context)
            //     .load(userAvatarUrl)
            //     .placeholder(R.drawable.ic_person)
            //     .into(ivUserAvatar);

            // Handle long click for message options
            itemView.setOnLongClickListener(v -> {
                // TODO: Show message options dialog (reply, copy, etc.)
                return true;
            });
        }
    }

    // ViewHolder for system messages (center)
    public class SystemMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView tvSystemMessage;

        public SystemMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSystemMessage = itemView.findViewById(R.id.tvSystemMessage);
        }

        public void bind(ChatMessage message) {
            tvSystemMessage.setText(message.getContent());
        }
    }
}