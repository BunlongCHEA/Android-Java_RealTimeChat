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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

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

        // Sort chat rooms by last message timestamp (newest first)
        Collections.sort(this.chatRooms, new Comparator<ChatRoom>() {
            @Override
            public int compare(ChatRoom room1, ChatRoom room2) {
                try {
                    String timestamp1 = room1.getLastMessageTimestamp();
                    String timestamp2 = room2.getLastMessageTimestamp();

                    if (timestamp1 == null && timestamp2 == null) return 0;
                    if (timestamp1 == null) return 1; // room1 goes to bottom
                    if (timestamp2 == null) return -1; // room2 goes to bottom

                    long time1 = parseTimestamp(timestamp1);
                    long time2 = parseTimestamp(timestamp2);

                    return Long.compare(time2, time1); // Descending order (newest first)
                } catch (Exception e) {
                    return 0;
                }
            }
        });

        notifyDataSetChanged();
    }

    private long parseTimestamp(String timestamp) {
        try {
            if (TextUtils.isEmpty(timestamp)) {
                return 0;
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date date = sdf.parse(timestamp);
            return date != null ? date.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
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
        private TextView tvUnreadBadge;
        private View vOnlineStatus;
        private ImageView ivChatTypeIcon;
        private ImageView ivChatTypeIndicator;
        private ImageView ivMutedIndicator;
        private ImageView ivPinnedIndicator;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfilePicture = itemView.findViewById(R.id.ivProfilePicture);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvFullName = itemView.findViewById(R.id.tvFullName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvLastMessageTime = itemView.findViewById(R.id.tvLastMessageTime);
            tvUnreadBadge = itemView.findViewById(R.id.tvUnreadBadge);
            vOnlineStatus = itemView.findViewById(R.id.vOnlineStatus);
            ivChatTypeIcon = itemView.findViewById(R.id.ivChatTypeIcon);
            ivChatTypeIndicator = itemView.findViewById(R.id.ivChatTypeIndicator);
            ivMutedIndicator = itemView.findViewById(R.id.ivMutedIndicator);
            ivPinnedIndicator = itemView.findViewById(R.id.ivPinnedIndicator);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        ChatRoom chatRoom = chatRooms.get(position);
                        Long currentUserId = sharedPrefManager.getId();
                        Participant otherParticipant = null;

                        // Only get other participant for personal chats
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

            // Reset all indicators
            resetIndicators();

            // Bind based on chat room type
            switch (chatRoom.getType()) {
                case PERSONAL:
                    bindPersonalChat(chatRoom, currentUserId);
                    break;
                case GROUP:
                    bindGroupChat(chatRoom, currentUserId, currentUsername);
                    break;
                case CHANNEL:
                    bindChannelChat(chatRoom, currentUserId, currentUsername);
                    break;
                default:
                    bindUnknownChat(chatRoom);
                    break;
            }

//            if (chatRoom.getType() == EnumRoomType.PERSONAL) {
//                bindPersonalChat(chatRoom, currentUserId);
//            } else if (chatRoom.getType() == EnumRoomType.GROUP) {
//                bindGroupChat(chatRoom, currentUserId, currentUsername);
//            } else {
//                bindChannelChat(chatRoom, currentUserId, currentUsername);
//            }

            // Set last message content (common for all types)
            bindLastMessage(chatRoom, currentUsername);

            // Set additional features
            bindAdditionalFeatures(chatRoom, currentUserId);
        }

        private void resetIndicators() {
            vOnlineStatus.setVisibility(View.GONE);
            ivChatTypeIcon.setVisibility(View.GONE);
            ivChatTypeIndicator.setVisibility(View.GONE);
            ivMutedIndicator.setVisibility(View.GONE);
            ivPinnedIndicator.setVisibility(View.GONE);
            tvUnreadBadge.setVisibility(View.GONE);
            tvFullName.setVisibility(View.GONE);
        }

        private void bindPersonalChat(ChatRoom chatRoom, Long currentUserId) {
            // For personal chats, show the other participant
            Participant otherParticipant = chatRoom.getOtherParticipant(currentUserId);

            if (otherParticipant != null) {
                // Set username
                String username = otherParticipant.getUsername();
                if (username != null && !username.isEmpty()) {
//                    tvUsername.setText("@" + username);
                    tvUsername.setText(username);
                } else {
                    tvUsername.setText("@unknown");
                }

//                // Set full name
//                String fullName = otherParticipant.getFullName();
//                if (fullName != null && !fullName.isEmpty()) {
//                    tvFullName.setText(fullName);
//                    tvFullName.setVisibility(View.VISIBLE);
//                }

                // Set online status for personal chats
                vOnlineStatus.setVisibility(otherParticipant.isOnline() ? View.VISIBLE : View.GONE);

                // Load profile picture
                loadProfilePicture(otherParticipant.getAvatarUrl(), false);

                // Set personal chat type indicator (optional)
                ivChatTypeIndicator.setImageResource(R.drawable.ic_person);
                ivChatTypeIndicator.setVisibility(View.VISIBLE);
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

            // Show group type indicator
            ivChatTypeIcon.setImageResource(R.drawable.ic_group);
            ivChatTypeIcon.setVisibility(View.VISIBLE);

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

            // Show channel type indicator
            ivChatTypeIcon.setImageResource(R.drawable.ic_channel);
            ivChatTypeIcon.setVisibility(View.VISIBLE);

            // Channel icon
            loadProfilePicture(null, false);
            ivProfilePicture.setImageResource(R.drawable.ic_channel);
        }

        private void bindUnknownChat(ChatRoom chatRoom) {
            tvUsername.setText(chatRoom.getName() != null ? chatRoom.getName() : "Unknown Chat");
            tvFullName.setText("Unknown type");
            tvFullName.setVisibility(View.VISIBLE);
            vOnlineStatus.setVisibility(View.GONE);
            ivProfilePicture.setImageResource(R.drawable.ic_search);
        }

        private void bindLastMessage(ChatRoom chatRoom, String currentUsername) {
            // Set last message content
            if (chatRoom.hasLastMessage()) {
                String lastMessageText = chatRoom.getLastMessageContent();
                String senderUsername = chatRoom.getLastMessageSenderUsername();

                // Handle message display based on chat type and sender
                if (chatRoom.getType() != EnumRoomType.PERSONAL) {
                    // Personal chat - show "You:" only if current user sent it
                    // For Other user will sent username
                    if (senderUsername != null && !senderUsername.isEmpty()) {
                        if (senderUsername.equals(currentUsername)) {
                            lastMessageText = "You: " + lastMessageText;
                        } else {
                            lastMessageText = senderUsername + ": " + lastMessageText;
                        }
                    }
                }  else {
                    // Group chat or channel - always show sender name
                    if (senderUsername != null && !senderUsername.isEmpty()) {
                        if (senderUsername.equals(currentUsername)) {
                            lastMessageText = "You: " + lastMessageText;
                        } else {
                            lastMessageText = senderUsername + ": " + lastMessageText;
                        }
                    }
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
                switch (chatRoom.getType()) {
                    case PERSONAL:
                        tvLastMessage.setText("Start a conversation");
                        break;
                    case GROUP:
                        tvLastMessage.setText("No messages yet");
                        break;
                    case CHANNEL:
                        tvLastMessage.setText("No posts yet");
                        break;
                    default:
                        tvLastMessage.setText("No messages");
                        break;
                }

                tvLastMessage.setVisibility(View.VISIBLE);
                tvLastMessageTime.setVisibility(View.GONE);
            }
        }

        private void bindAdditionalFeatures(ChatRoom chatRoom, Long currentUserId) {
            // Show unread message count (if available)
            // Uncomment when unread count is implemented
//            if (chatRoom.getUnreadCount() > 0) {
//                tvUnreadBadge.setText(String.valueOf(chatRoom.getUnreadCount()));
//                tvUnreadBadge.setVisibility(View.VISIBLE);
//            } else {
//                tvUnreadBadge.setVisibility(View.GONE);
//            }


            // Show muted indicator
            Participant currentParticipant = chatRoom.getCurrentParticipant(currentUserId);
            if (currentParticipant != null && currentParticipant.isMuted()) {
                ivMutedIndicator.setVisibility(View.VISIBLE);
            } else {
                ivMutedIndicator.setVisibility(View.GONE);
            }

            // Show pinned indicator
//            if (chatRoom.isPinned()) {
//                ivPinnedIndicator.setVisibility(View.VISIBLE);
//            } else {
//                ivPinnedIndicator.setVisibility(View.GONE);
//            }

            // Show admin badge for group/channel admins
//            if ((chatRoom.getType() == EnumRoomType.GROUP || chatRoom.getType() == EnumRoomType.CHANNEL)
//                    && currentParticipant != null && "ADMIN".equals(currentParticipant.getRole())) {
//                // Could add an admin badge here if needed
//            }
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
                Date messageDate = null;

                if (timestamp.contains("T")) {
                    inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                    inputFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // Parse as UTC
                    messageDate = inputFormat.parse(timestamp);
                } else {
//                    inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    inputFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // Parse as UTC
                    messageDate = inputFormat.parse(timestamp);
                }

                android.util.Log.e("UserListAdapter", "inputFormat: " + inputFormat);
                android.util.Log.e("UserListAdapter", "messageDate: " + messageDate);

                if (messageDate != null) {
                    // Convert UTC time to local timezone for display
                    long messageTimeInLocal = messageDate.getTime(); // This is already in local time after parsing
                    long currentTime = System.currentTimeMillis();

                    long diffInMillis = currentTime - messageTimeInLocal;
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
                        // For dates older than 7 days, show in user's local timezone
                        SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
                        outputFormat.setTimeZone(TimeZone.getDefault()); // Use local timezone for display
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