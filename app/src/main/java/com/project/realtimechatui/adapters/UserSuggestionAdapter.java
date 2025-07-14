package com.project.realtimechatui.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.project.realtimechatui.R;
import com.project.realtimechatui.api.models.User;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class UserSuggestionAdapter extends RecyclerView.Adapter<UserSuggestionAdapter.UserViewHolder> {

    private List<User> users;
    private List<User> filteredUsers;
    private Context context;
    private OnUserClickListener listener;

    public interface OnUserClickListener {
        void onUserClick(User user);
    }

    public UserSuggestionAdapter(Context context, OnUserClickListener listener) {
        this.context = context;
        this.listener = listener;
        this.users = new ArrayList<>();
        this.filteredUsers = new ArrayList<>();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_user_suggestion, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = filteredUsers.get(position);
        holder.bind(user);
    }

    @Override
    public int getItemCount() {
        return filteredUsers.size();
    }

    public void setUsers(List<User> users) {
        this.users = users != null ? users : new ArrayList<>();
        this.filteredUsers = new ArrayList<>(this.users);
        notifyDataSetChanged();
    }

    // ADD THIS METHOD to add a single user
    public void addUser(User user) {
        if (user != null) {
            if (users == null) {
                users = new ArrayList<>();
            }
            if (filteredUsers == null) {
                filteredUsers = new ArrayList<>();
            }

            // Check if user already exists
            boolean exists = false;
            for (User existingUser : users) {
                if (existingUser.getId().equals(user.getId())) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                users.add(0, user); // Add at the top
                filteredUsers.add(0, user);
                notifyItemInserted(0);
                Log.d("UserSuggestionAdapter", "User added: " + user.getUsername() + ", total users: " + users.size());
            } else {
                Log.d("UserSuggestionAdapter", "User already exists: " + user.getUsername());
            }
        }
    }

    public void filter(String query) {
        filteredUsers.clear();

        if (TextUtils.isEmpty(query)) {
            filteredUsers.addAll(users);
        } else {
            String searchQuery = query.toLowerCase().trim();
            // Remove @ symbol if present
            if (searchQuery.startsWith("@")) {
                searchQuery = searchQuery.substring(1);
            }

            for (User user : users) {
                if (matchesQuery(user, searchQuery)) {
                    filteredUsers.add(user);
                }
            }
        }

        notifyDataSetChanged();
    }

    private boolean matchesQuery(User user, String query) {
        if (TextUtils.isEmpty(query)) {
            return true;
        }

        // Check username match
        if (user.getUsername() != null &&
                user.getUsername().toLowerCase().contains(query)) {
            return true;
        }

        // Check full name match
        if (user.getFullName() != null &&
                user.getFullName().toLowerCase().contains(query)) {
            return true;
        }

        // Check email match
        if (user.getEmail() != null &&
                user.getEmail().toLowerCase().contains(query)) {
            return true;
        }

        // Regex pattern matching for similar names
        try {
            String regexPattern = ".*" + Pattern.quote(query) + ".*";
            Pattern pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);

            if (user.getUsername() != null && pattern.matcher(user.getUsername()).matches()) {
                return true;
            }

            if (user.getFullName() != null && pattern.matcher(user.getFullName()).matches()) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public class UserViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivProfilePicture;
        private TextView tvUsername;
        private TextView tvFullName;
        private View vOnlineStatus;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfilePicture = itemView.findViewById(R.id.ivProfilePicture);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvFullName = itemView.findViewById(R.id.tvFullName);
            vOnlineStatus = itemView.findViewById(R.id.vOnlineStatus);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        listener.onUserClick(filteredUsers.get(position));
                    }
                }
            });
        }

        public void bind(User user) {
            if (user.getUsername() != null) {
                tvUsername.setText("@" + user.getUsername());
            } else {
                tvUsername.setText("@unknown");
            }

            if (user.getFullName() != null && !user.getFullName().isEmpty()) {
                tvFullName.setText(user.getFullName());
                tvFullName.setVisibility(View.VISIBLE);
            } else {
                tvFullName.setText("");
                tvFullName.setVisibility(View.GONE);
            }

            // Show online status
            //vOnlineStatus.setVisibility(user.isOnline() ? View.VISIBLE : View.GONE);

            // Load profile picture with Glide (if available)
            // if (!TextUtils.isEmpty(user.getProfilePicture())) {
            //     Glide.with(context)
            //         .load(user.getProfilePicture())
            //         .placeholder(R.drawable.ic_person)
            //         .error(R.drawable.ic_person)
            //         .into(ivProfilePicture);
            // } else {
            //     ivProfilePicture.setImageResource(R.drawable.ic_person);
            // }
        }
    }
}