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
import com.project.realtimechatui.api.models.Participant;
import com.project.realtimechatui.api.models.User;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class UserListAdapter extends RecyclerView.Adapter<UserListAdapter.UserViewHolder> {

    private List<Participant> participants;
    private Context context;
    private OnUserClickListener listener;

    public interface OnUserClickListener {
        void onUserClick(Participant participant);
    }

    public UserListAdapter(Context context, OnUserClickListener listener) {
        this.context = context;
        this.listener = listener;
        this.participants = new ArrayList<>();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_list_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        Participant participant = participants.get(position);
        holder.bind(participant);
    }

    @Override
    public int getItemCount() {
        return participants.size();
    }

    public void setParticipants(List<Participant> participants) {
        this.participants = participants != null ? participants : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addParticipant(Participant participant) {
        if (participant != null) {
            participants.add(participant);
            notifyItemInserted(participants.size() - 1);
        }
    }

    public void removeParticipant(Long participantId) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).getId().equals(participantId)) {
                participants.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    public void updateParticipant(Participant updatedParticipant) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).getId().equals(updatedParticipant.getId())) {
                participants.set(i, updatedParticipant);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public class UserViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivProfilePicture;
        private TextView tvUsername;
        private TextView tvFullName;
        private TextView tvLastReadMessage;
        private TextView tvLastSeen;
        private View vOnlineStatus;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfilePicture = itemView.findViewById(R.id.ivProfilePicture);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvFullName = itemView.findViewById(R.id.tvFullName);
            tvLastReadMessage = itemView.findViewById(R.id.tvLastReadMessage);
            tvLastSeen = itemView.findViewById(R.id.tvLastSeen);
            vOnlineStatus = itemView.findViewById(R.id.vOnlineStatus);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        listener.onUserClick(participants.get(position));
                    }
                }
            });
        }

        public void bind(Participant participant) {
            // Check if participant and user are not null
            if (participant == null) {
                return;
            }

//            User user = participant.getUser();
            if (participant == null) {
                // Handle case where user is null
                tvUsername.setText("@unknown");
                tvFullName.setText("Unknown User");
                tvFullName.setVisibility(View.VISIBLE);
                vOnlineStatus.setVisibility(View.GONE);
                tvLastSeen.setText("Offline");
                tvLastSeen.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
                tvLastReadMessage.setVisibility(View.GONE);
                ivProfilePicture.setImageResource(R.drawable.ic_person);
                return;
            }

            // Set username
            if (participant.getUsername() != null && !participant.getUsername().isEmpty()) {
                tvUsername.setText(participant.getUsername());
            } else {
                tvUsername.setText("@unknown");
            }

            // Set full name
            if (participant.getFullName() != null && !participant.getFullName().isEmpty()) {
                tvFullName.setText(participant.getFullName());
                tvFullName.setVisibility(View.VISIBLE);
            } else {
                tvFullName.setText("");
                tvFullName.setVisibility(View.GONE);
            }


            // Set online status
            vOnlineStatus.setVisibility(participant.isOnline() ? View.VISIBLE : View.GONE);

            // Set last read message info
            if (participant.getLastReadMessageId() != null && participant.getLastReadMessageId() > 0) {
                tvLastReadMessage.setText("Last read: Message #" + participant.getLastReadMessageId());
                tvLastReadMessage.setVisibility(View.VISIBLE);
            } else {
                tvLastReadMessage.setVisibility(View.GONE);
            }

            // Set last seen
            if (participant.isOnline()) {
                tvLastSeen.setText("Online");
                tvLastSeen.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
            } else if (participant.getLastSeen() != null && !participant.getLastSeen().isEmpty()) {
                try {
                    // Format last seen time
                    String formattedTime = formatLastSeen(participant.getLastSeen());
                    tvLastSeen.setText(formattedTime);
                    tvLastSeen.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
                } catch (Exception e) {
                    tvLastSeen.setText("Offline");
                    tvLastSeen.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
                }
            } else {
                tvLastSeen.setText("Offline");
                tvLastSeen.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
            }

            // Load profile picture
            if (participant.getAvatarUrl() != null && !participant.getAvatarUrl().isEmpty()) {
                // TODO: Implement image loading with Glide or Picasso
                // Glide.with(context)
                //     .load(participant.getAvatarUrl())
                //     .placeholder(R.drawable.ic_person)
                //     .error(R.drawable.ic_person)
                //     .into(ivProfilePicture);
                ivProfilePicture.setImageResource(R.drawable.ic_person);
            } else {
                ivProfilePicture.setImageResource(R.drawable.ic_person);
            }
        }

        private String formatLastSeen(String lastSeenStr) {
            try {
                // Assuming lastSeen is in format "yyyy-MM-dd HH:mm:ss" or "yyyy-MM-ddTHH:mm:ss"
                SimpleDateFormat inputFormat;
                if (lastSeenStr.contains("T")) {
                    inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                } else {
                    inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                }

                Date lastSeenDate = inputFormat.parse(lastSeenStr);

                if (lastSeenDate != null) {
                    long diffInMillis = System.currentTimeMillis() - lastSeenDate.getTime();
                    long diffInMinutes = diffInMillis / (60 * 1000);
                    long diffInHours = diffInMinutes / 60;
                    long diffInDays = diffInHours / 24;

                    if (diffInMinutes < 1) {
                        return "Just now";
                    } else if (diffInMinutes < 60) {
                        return diffInMinutes + "m ago";
                    } else if (diffInHours < 24) {
                        return diffInHours + "h ago";
                    } else if (diffInDays < 7) {
                        return diffInDays + "d ago";
                    } else {
                        SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
                        return outputFormat.format(lastSeenDate);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "Offline";
        }
    }
}