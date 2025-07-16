package com.project.realtimechatui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_MESSAGE_SENT = 1;
    private static final int VIEW_TYPE_MESSAGE_RECEIVED = 2;
    private static final int VIEW_TYPE_MESSAGE_SYSTEM = 3;

    private Context context;
    private List<ChatMessage> messages;
    private SharedPrefManager sharedPrefManager;
    private SimpleDateFormat timeFormat;

    private Set<Long> messageIds = new HashSet<>();

    public ChatMessageAdapter(Context context) {
        this.context = context;
        this.messages = new ArrayList<>();
        this.sharedPrefManager = SharedPrefManager.getInstance();
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);

        if (message.isSystemMessage() || isDateSeparator(message)) {
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
                ((SystemMessageViewHolder) holder).bind(message, isDateSeparator(message));
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
        messageIds.clear();

        if (messages != null) {
            Collections.sort(messages, new Comparator<ChatMessage>() {
                @Override
                public int compare(ChatMessage m1, ChatMessage m2) {
                    try {
                        long time1 = parseTimestamp(m1.getTimestamp());
                        long time2 = parseTimestamp(m2.getTimestamp());
                        return Long.compare(time1, time2);
                    } catch (Exception e) {
                        return 0;
                    }
                }
            });

            for (ChatMessage message : messages) {
                // Only track real messages (positive IDs)
                if (message.getId() != null && message.getId() > 0) {
                    messageIds.add(message.getId());
                }
            }

            this.messages.addAll(messages);
        }

        notifyDataSetChanged();
    }

    // Add clear messages method
    public void clearMessages() {
        this.messages.clear();
        this.messageIds.clear();
        notifyDataSetChanged();
    }

    private long parseTimestamp(String timestamp) {
        try {
            if (TextUtils.isEmpty(timestamp)) {
                return 0;
            }

            if (timestamp.matches("\\d{13}")) {
                return Long.parseLong(timestamp);
            }

            if (timestamp.matches("\\d{10}")) {
                return Long.parseLong(timestamp) * 1000;
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = sdf.parse(timestamp);
            return date != null ? date.getTime() : System.currentTimeMillis();

        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    public void addMessage(ChatMessage message) {
        if (message != null && !isDuplicateMessage(message)) {
            messages.add(message);

            // Only track positive IDs (real messages)
            if (message.getId() != null && message.getId() > 0) {
                messageIds.add(message.getId());
            }

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
        if (newMessage.getId() == null || newMessage.getId() < 0) {
            // Virtual messages (negative IDs) are never considered duplicates
            return false;
        }
        return messageIds.contains(newMessage.getId());

//        for (ChatMessage existingMessage : messages) {
//            if (newMessage.getId().equals(existingMessage.getId())) {
//                return true;
//            }
//        }
//        return false;
    }

    private String formatTimestamp(String timestamp) {
        try {
            if (TextUtils.isEmpty(timestamp)) {
                return "";
            }

            long timestampLong;
            if (timestamp.length() == 13) {
                timestampLong = Long.parseLong(timestamp);
            } else {
                timestampLong = System.currentTimeMillis();
            }

            Date date = new Date(timestampLong);
            return timeFormat.format(date);
        } catch (Exception e) {
            return "";
        }
    }

    // Add method to identify system messages as date separators
    private boolean isDateSeparator(ChatMessage message) {
        return message.getType() != null &&
                message.getType().equals("SYSTEM") &&
                (message.getContent().contains("Today") ||
                        message.getContent().contains("Yesterday") ||
                        message.getContent().matches(".*\\d{2}:\\d{2}.*") ||
                        message.getContent().matches(".*\\d{2}/\\d{2}/\\d{4}.*"));
    }

    // ViewHolder for sent messages (right side)
    public class SentMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView tvSenderName;
        private TextView tvMessageContent;
        private TextView tvTimestamp;
        private TextView tvEditedIndicator;
        private ImageView ivMessageStatus;

        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvMessageContent = itemView.findViewById(R.id.tvMessageContent);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvEditedIndicator = itemView.findViewById(R.id.tvEditedIndicator);
            ivMessageStatus = itemView.findViewById(R.id.ivMessageStatus);
        }

        public void bind(ChatMessage message) {
            String senderName = message.getSenderName();
            if (TextUtils.isEmpty(senderName)) {
                senderName = "User " + message.getSenderId();
            }
            tvSenderName.setText(senderName);

            tvMessageContent.setText(message.getContent());
            tvTimestamp.setText(formatTimestamp(message.getTimestamp()));

            if (message.isEdited()) {
                tvEditedIndicator.setVisibility(View.VISIBLE);
            } else {
                tvEditedIndicator.setVisibility(View.GONE);
            }

            ivMessageStatus.setImageResource(R.drawable.ic_check);

            itemView.setOnLongClickListener(v -> {
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
            String senderName = message.getSenderName();
            if (TextUtils.isEmpty(senderName)) {
                senderName = "User " + message.getSenderId();
            }
            tvSenderName.setText(senderName);

            tvMessageContent.setText(message.getContent());
            tvTimestamp.setText(formatTimestamp(message.getTimestamp()));

            if (message.isEdited()) {
                tvEditedIndicator.setVisibility(View.VISIBLE);
            } else {
                tvEditedIndicator.setVisibility(View.GONE);
            }

            itemView.setOnLongClickListener(v -> {
                return true;
            });
        }
    }

    // ViewHolder for system messages (center) - UPDATED
    public class SystemMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView tvSystemMessage;

        public SystemMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSystemMessage = itemView.findViewById(R.id.tvSystemMessage);
        }

        public void bind(ChatMessage message, boolean isDateSeparator) {
            tvSystemMessage.setText(message.getContent());

            if (isDateSeparator) {
                // Style as date separator
                tvSystemMessage.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                tvSystemMessage.setTypeface(tvSystemMessage.getTypeface(), Typeface.ITALIC);
                tvSystemMessage.setTextColor(Color.GRAY);
                tvSystemMessage.setTextSize(12); // Smaller text for date separators

                // Add some padding for better visual separation
                tvSystemMessage.setPadding(16, 8, 16, 8);
            } else {
                // Regular system message styling
                tvSystemMessage.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                tvSystemMessage.setTypeface(tvSystemMessage.getTypeface(), Typeface.NORMAL);
                tvSystemMessage.setTextColor(Color.DKGRAY);
                tvSystemMessage.setTextSize(14);
                tvSystemMessage.setPadding(16, 4, 16, 4);
            }
        }
    }
}