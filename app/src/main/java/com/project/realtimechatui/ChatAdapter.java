package com.project.realtimechatui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.project.realtimechatui.api.models.Chat;
import com.project.realtimechatui.api.models.Message;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {
    private List<Chat> chatList;
    private OnChatClickListener listener;

    public interface OnChatClickListener {
        void onChatClick(Chat chat);
    }

    public ChatAdapter(List<Chat> chatList, OnChatClickListener listener) {
        this.chatList = chatList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        Chat chat = chatList.get(position);
        holder.bind(chat);
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    public void updateChats(List<Chat> newChats) {
        this.chatList.clear();
        this.chatList.addAll(newChats);
        notifyDataSetChanged();
    }

    class ChatViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivAvatar;
        private TextView tvChatName;
        private TextView tvLastMessage;
        private TextView tvTimestamp;
        private TextView tvUnreadCount;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvChatName = itemView.findViewById(R.id.tvChatName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onChatClick(chatList.get(position));
                    }
                }
            });
        }

        public void bind(Chat chat) {
            // Set chat name
            tvChatName.setText(chat.getName() != null ? chat.getName() : "Unknown Chat");

            // Set last message
            Message lastMessage = chat.getLastMessage();
            if (lastMessage != null) {
                tvLastMessage.setText(lastMessage.getContent());
                tvLastMessage.setVisibility(View.VISIBLE);
                
                // Set timestamp
                if (lastMessage.getTimestamp() != null) {
                    tvTimestamp.setText(formatTimestamp(lastMessage.getTimestamp()));
                    tvTimestamp.setVisibility(View.VISIBLE);
                } else {
                    tvTimestamp.setVisibility(View.GONE);
                }
            } else {
                tvLastMessage.setText("No messages yet");
                tvLastMessage.setVisibility(View.VISIBLE);
                tvTimestamp.setVisibility(View.GONE);
            }

            // Set unread count
            int unreadCount = chat.getUnreadCount();
            if (unreadCount > 0) {
                tvUnreadCount.setText(String.valueOf(unreadCount));
                tvUnreadCount.setVisibility(View.VISIBLE);
            } else {
                tvUnreadCount.setVisibility(View.GONE);
            }

            // TODO: Load avatar image
            // For now, use a placeholder
        }

        private String formatTimestamp(String timestamp) {
            try {
                // Assuming timestamp is in ISO format
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                Date date = inputFormat.parse(timestamp);
                return outputFormat.format(date);
            } catch (Exception e) {
                return "";
            }
        }
    }
}