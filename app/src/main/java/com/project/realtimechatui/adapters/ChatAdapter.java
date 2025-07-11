package com.project.realtimechatui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.project.realtimechatui.R;
import com.project.realtimechatui.api.models.ChatRoom;
import com.project.realtimechatui.api.models.Participant;

import org.jspecify.annotations.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private Context context;
    private List<ChatRoom> chatRooms;
    private OnChatClickListener listener;
    private String currentUsername;

    public interface OnChatClickListener {
        void onChatClick(ChatRoom chatRoom);
    }

    public ChatAdapter(Context context, String currentUsername) {
        this.context = context;
        this.chatRooms = new ArrayList<>();
        this.currentUsername = currentUsername;
    }

    public void setChatRooms(List<ChatRoom> chatRooms) {
        this.chatRooms = chatRooms;
        notifyDataSetChanged();
    }

    public void setOnChatClickListener(OnChatClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatRoom chatRoom = chatRooms.get(position);
        holder.bind(chatRoom);
    }

    @Override
    public int getItemCount() {
        return chatRooms.size();
    }

    class ChatViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivAvatar;
        TextView tvChatName;
        TextView tvTime;
        TextView tvSenderName;
        TextView tvLastMessage;
        TextView tvUnreadCount;
        TextView tvChatType;
        View vOnlineIndicator;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);

            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvChatName = itemView.findViewById(R.id.tvChatName);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);
            tvChatType = itemView.findViewById(R.id.tvChatType);
            vOnlineIndicator = itemView.findViewById(R.id.vOnlineIndicator);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onChatClick(chatRooms.get(getAdapterPosition()));
                }
            });
        }

        public void bind(ChatRoom chatRoom) {
            // Set chat name
            if ("PERSONAL".equals(chatRoom.getType())) {
                // For personal chats, show the other participant's name
                String otherParticipantName = getOtherParticipantName(chatRoom);
                tvChatName.setText(otherParticipantName != null ? otherParticipantName : "Unknown User");
                tvChatType.setVisibility(View.GONE);
            } else {
                tvChatName.setText(chatRoom.getName());
                tvChatType.setText(chatRoom.getType());
                tvChatType.setVisibility(View.VISIBLE);
            }

            // Set avatar
            String avatarUrl = getAvatarUrl(chatRoom);
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                Glide.with(context)
                        .load(avatarUrl)
                        .placeholder(R.drawable.default_avatar)
                        .error(R.drawable.default_avatar)
                        .into(ivAvatar);
            } else {
                ivAvatar.setImageResource(R.drawable.default_avatar);
            }

            // Set last message
            if (chatRoom.getLastMessageContent() != null) {
                tvLastMessage.setText(formatMessageContent(chatRoom));

                // Show sender name for group chats
                if (!"PERSONAL".equals(chatRoom.getType()) &&
                        chatRoom.getLastMessageSenderUsername() != null) {

                    if (currentUsername.equals(chatRoom.getLastMessageSenderUsername())) {
                        tvSenderName.setText("You: ");
                    } else {
                        tvSenderName.setText(chatRoom.getLastMessageSenderUsername() + ": ");
                    }
                    tvSenderName.setVisibility(View.VISIBLE);
                } else {
                    tvSenderName.setVisibility(View.GONE);
                }
            } else {
                tvLastMessage.setText("No messages yet");
                tvSenderName.setVisibility(View.GONE);
            }

            // Set timestamp
            if (chatRoom.getLastMessageTimestamp() != null) {
                tvTime.setText(formatTimestamp(chatRoom.getLastMessageTimestamp()));
            } else {
                tvTime.setText("");
            }

            // Show online indicator for personal chats
            if ("PERSONAL".equals(chatRoom.getType())) {
                boolean isOtherUserOnline = isOtherParticipantOnline(chatRoom);
                vOnlineIndicator.setVisibility(isOtherUserOnline ? View.VISIBLE : View.GONE);
            } else {
                vOnlineIndicator.setVisibility(View.GONE);
            }

            // Hide unread count for now (implement if needed)
            tvUnreadCount.setVisibility(View.GONE);
        }

        private String getOtherParticipantName(ChatRoom chatRoom) {
            if (chatRoom.getParticipants() != null) {
                for (Participant participant : chatRoom.getParticipants()) {
                    if (!currentUsername.equals(participant.getUsername())) {
                        return participant.getFullName() != null ?
                                participant.getFullName() : participant.getUsername();
                    }
                }
            }
            return null;
        }

        private String getAvatarUrl(ChatRoom chatRoom) {
            if ("PERSONAL".equals(chatRoom.getType()) && chatRoom.getParticipants() != null) {
                for (Participant participant : chatRoom.getParticipants()) {
                    if (!currentUsername.equals(participant.getUsername())) {
                        // Return avatar URL from User entity if available
                        return null; // You'll need to add avatarUrl to Participant or fetch from User
                    }
                }
            }
            return null;
        }

        private boolean isOtherParticipantOnline(ChatRoom chatRoom) {
            if (chatRoom.getParticipants() != null) {
                for (Participant participant : chatRoom.getParticipants()) {
                    if (!currentUsername.equals(participant.getUsername())) {
                        return participant.isOnline();
                    }
                }
            }
            return false;
        }

        private String formatMessageContent(ChatRoom chatRoom) {
            String content = chatRoom.getLastMessageContent();
            String type = chatRoom.getLastMessageType();

            if (type != null) {
                switch (type) {
                    case "IMAGE":
                        return "📷 Photo";
                    case "FILE":
                        return "📎 File";
                    case "SYSTEM":
                        return content; // System messages are already formatted
                    default:
                        return content;
                }
            }
            return content;
        }

        private String formatTimestamp(String timestamp) {
            try {
                // Parse ISO timestamp
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault());
                Date date = inputFormat.parse(timestamp);

                // Format for display
                SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return outputFormat.format(date);
            } catch (Exception e) {
                return "";
            }
        }

    }
}