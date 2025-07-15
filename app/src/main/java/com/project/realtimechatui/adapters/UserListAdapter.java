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
import com.project.realtimechatui.api.models.ChatRoom;
import com.project.realtimechatui.api.models.Participant;
import com.project.realtimechatui.api.models.User;
import com.project.realtimechatui.enums.EnumRoomType;
import com.project.realtimechatui.utils.SharedPrefManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class UserListAdapter extends RecyclerView.Adapter<UserListAdapter.UserViewHolder> {

    private List<ChatRoom> chatRooms;
    private Context context;
    private OnUserClickListener listener;
    private SharedPrefManager sharedPrefManager;

    public interface OnUserClickListener {
        void onUserClick(ChatRoom chatRoom, Participant otherParticipant);
    }

    public UserListAdapter(Context context, OnUserClickListener listener) {
        this.context = context;
        this.listener = listener;
        this.chatRooms = new ArrayList<>();
        this.sharedPrefManager = SharedPrefManager.getInstance();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_list_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        ChatRoom chatRoom = chatRooms.get(position);
        holder.bind(chatRoom);
    }

    @Override
    public int getItemCount() {
        return chatRooms.size();
    }

    // Updated method to accept ChatRoom list instead of Participant list
    public void setChatRooms(List<ChatRoom> chatRooms) {
        this.chatRooms = chatRooms != null ? chatRooms : new ArrayList<>();
        notifyDataSetChanged();
    }

    // Keep this method for backward compatibility if needed
    public void setParticipants(List<Participant> participants) {
        // Convert participants to chat rooms if needed
        // This method can be removed if not used elsewhere
    }

    public void addChatRoom(ChatRoom chatRoom) {
        if (chatRoom != null) {
            chatRooms.add(0, chatRoom); // Add at top
            notifyItemInserted(0);
        }
    }

    public void removeChatRoom(Long chatRoomId) {
        for (int i = 0; i < chatRooms.size(); i++) {
            if (chatRooms.get(i).getId().equals(chatRoomId)) {
                chatRooms.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    public void updateChatRoom(ChatRoom updatedChatRoom) {
        for (int i = 0; i < chatRooms.size(); i++) {
            if (chatRooms.get(i).getId().equals(updatedChatRoom.getId())) {
                chatRooms.set(i, updatedChatRoom);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public class UserViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivProfilePicture;
        private TextView tvUsername;
        private TextView tvFullName;
        private TextView tvLastMessage;
        private TextView tvLastMessageTime;
        private View vOnlineStatus;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfilePicture = itemView.findViewById(R.id.ivProfilePicture);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvFullName = itemView.findViewById(R.id.tvFullName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvLastMessageTime = itemView.findViewById(R.id.tvLastMessageTime);
            vOnlineStatus = itemView.findViewById(R.id.vOnlineStatus);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        ChatRoom chatRoom = chatRooms.get(position);
                        Long currentUserId = sharedPrefManager.getId();
                        Participant otherParticipant = null;

                        if (chatRoom.getType() == EnumRoomType.PERSONAL) {
                            otherParticipant = chatRoom.getOtherParticipant(currentUserId);
                        }

                        listener.onUserClick(chatRoom, otherParticipant);
                    }
                }
            });
        }

        public void bind(ChatRoom chatRoom) {
            if (chatRoom == null) {
                return;
            }

            Long currentUserId = sharedPrefManager.getId();
            String currentUsername = sharedPrefManager.getUsername();

            if (chatRoom.getType() == EnumRoomType.PERSONAL) {
                bindPersonalChat(chatRoom, currentUserId);
            } else if (chatRoom.getType() == EnumRoomType.GROUP) {
                bindGroupChat(chatRoom, currentUserId, currentUsername);
            } else {
                bindChannelChat(chatRoom, currentUserId, currentUsername);
            }

            // Set last message content (common for all types)
            bindLastMessage(chatRoom, currentUsername);
        }

        private void bindPersonalChat(ChatRoom chatRoom, Long currentUserId) {
            // For personal chats, show the other participant
            Participant otherParticipant = chatRoom.getOtherParticipant(currentUserId);

            if (otherParticipant != null) {
                // Set username
                if (otherParticipant.getUsername() != null && !otherParticipant.getUsername().isEmpty()) {
                    tvUsername.setText(otherParticipant.getUsername());
                } else {
                    tvUsername.setText("@unknown");
                }

                // Set full name
                if (otherParticipant.getFullName() != null && !otherParticipant.getFullName().isEmpty()) {
                    tvFullName.setText(otherParticipant.getFullName());
                    tvFullName.setVisibility(View.VISIBLE);
                } else {
                    tvFullName.setVisibility(View.GONE);
                }

                // Set online status for personal chats
                vOnlineStatus.setVisibility(otherParticipant.isOnline() ? View.VISIBLE : View.GONE);

                // Load profile picture
                loadProfilePicture(otherParticipant.getAvatarUrl(), false);
            } else {
                setUnknownUser();
            }
        }

        private void bindGroupChat(ChatRoom chatRoom, Long currentUserId, String currentUsername) {
            // For group chats, show room name and participant count
            String groupName = chatRoom.getName();
            if (groupName == null || groupName.isEmpty()) {
                groupName = "Group Chat";
            }

            tvUsername.setText(groupName);

            // Show participant count as subtitle
            int participantCount = chatRoom.getParticipants() != null ? chatRoom.getParticipants().size() : 0;
            tvFullName.setText(participantCount + " members");
            tvFullName.setVisibility(View.VISIBLE);

            // No online status for group chats
            vOnlineStatus.setVisibility(View.GONE);

            // Group chat icon
            loadProfilePicture(null, true);
        }

        private void bindChannelChat(ChatRoom chatRoom, Long currentUserId, String currentUsername) {
            // For channels, show channel name and # prefix
            String channelName = chatRoom.getName();
            if (channelName == null || channelName.isEmpty()) {
                channelName = "Channel";
            }

            tvUsername.setText("#" + channelName);

            // Show channel info as subtitle
            int participantCount = chatRoom.getParticipants() != null ? chatRoom.getParticipants().size() : 0;
            tvFullName.setText(participantCount + " subscribers");
            tvFullName.setVisibility(View.VISIBLE);

            // No online status for channels
            vOnlineStatus.setVisibility(View.GONE);

            // Channel icon
            ivProfilePicture.setImageResource(R.drawable.ic_channel);
        }

        private void bindLastMessage(ChatRoom chatRoom, String currentUsername) {
            // Set last message content
            if (chatRoom.hasLastMessage()) {
                String lastMessageText = chatRoom.getLastMessageContent();
                String senderUsername = chatRoom.getLastMessageSenderUsername();

                // For group chats or channels, always show sender name
                // For personal chats, show sender name only if it's not the current user
                if (chatRoom.getType() != EnumRoomType.PERSONAL) {
                    // Group chat or channel - always show sender
                    if (senderUsername != null && !senderUsername.isEmpty()) {
                        if (senderUsername.equals(currentUsername)) {
                            lastMessageText = "You: " + lastMessageText;
                        } else {
                            lastMessageText = senderUsername + ": " + lastMessageText;
                        }
                    }
                } else {
                    // Personal chat - show "You:" only if current user sent it
                    if (senderUsername != null && senderUsername.equals(currentUsername)) {
                        lastMessageText = "You: " + lastMessageText;
                    }
                    // If other user sent it, just show the message without prefix
                }

                tvLastMessage.setText(lastMessageText);
                tvLastMessage.setVisibility(View.VISIBLE);

                // Set last message timestamp
                if (chatRoom.getLastMessageTimestamp() != null) {
                    String formattedTime = formatMessageTime(chatRoom.getLastMessageTimestamp());
                    tvLastMessageTime.setText(formattedTime);
                    tvLastMessageTime.setVisibility(View.VISIBLE);
                } else {
                    tvLastMessageTime.setVisibility(View.GONE);
                }
            } else {
                // No messages yet
                if (chatRoom.getType() == EnumRoomType.PERSONAL) {
                    tvLastMessage.setText("Start a conversation");
                } else {
                    tvLastMessage.setText("No messages yet");
                }
                tvLastMessage.setVisibility(View.VISIBLE);
                tvLastMessageTime.setVisibility(View.GONE);
            }
        }

        private void loadProfilePicture(String avatarUrl, boolean isGroup) {
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                // TODO: Implement image loading with Glide or Picasso
                // Glide.with(context)
                //     .load(avatarUrl)
                //     .placeholder(isGroup ? R.drawable.ic_group : R.drawable.ic_person)
                //     .error(isGroup ? R.drawable.ic_group : R.drawable.ic_person)
                //     .into(ivProfilePicture);
                ivProfilePicture.setImageResource(isGroup ? R.drawable.ic_group : R.drawable.ic_person);
            } else {
                ivProfilePicture.setImageResource(isGroup ? R.drawable.ic_group : R.drawable.ic_person);
            }
        }

        private void setUnknownUser() {
            tvUsername.setText("@unknown");
            tvFullName.setText("Unknown User");
            tvFullName.setVisibility(View.VISIBLE);
            vOnlineStatus.setVisibility(View.GONE);
            ivProfilePicture.setImageResource(R.drawable.ic_person);
            tvLastMessage.setText("No messages");
            tvLastMessage.setVisibility(View.VISIBLE);
            tvLastMessageTime.setVisibility(View.GONE);
        }

        private String formatMessageTime(String timestamp) {
            try {
                // Handle different timestamp formats
                SimpleDateFormat inputFormat;
                if (timestamp.contains("T")) {
                    inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                } else {
                    inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                }

                Date messageDate = inputFormat.parse(timestamp);

                if (messageDate != null) {
                    long diffInMillis = System.currentTimeMillis() - messageDate.getTime();
                    long diffInMinutes = diffInMillis / (60 * 1000);
                    long diffInHours = diffInMinutes / 60;
                    long diffInDays = diffInHours / 24;

                    if (diffInMinutes < 1) {
                        return "now";
                    } else if (diffInMinutes < 60) {
                        return diffInMinutes + "m";
                    } else if (diffInHours < 24) {
                        return diffInHours + "h";
                    } else if (diffInDays < 7) {
                        return diffInDays + "d";
                    } else {
                        SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
                        return outputFormat.format(messageDate);
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("UserListAdapter", "Error parsing timestamp: " + timestamp, e);
            }
            return "";
        }
    }
}