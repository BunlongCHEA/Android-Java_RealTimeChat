package com.project.realtimechatui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.project.realtimechatui.api.models.User;

import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {
    private List<User> userList;
    private OnUserSelectionListener listener;

    public interface OnUserSelectionListener {
        void onUserSelectionChanged(User user, boolean isSelected);
    }

    public UserAdapter(List<User> userList, OnUserSelectionListener listener) {
        this.userList = userList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = userList.get(position);
        holder.bind(user);
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        private CheckBox cbSelect;
        private ImageView ivAvatar;
        private TextView tvFullName;
        private TextView tvUsername;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            cbSelect = itemView.findViewById(R.id.cbSelect);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvFullName = itemView.findViewById(R.id.tvFullName);
            tvUsername = itemView.findViewById(R.id.tvUsername);

            // Set click listeners
            itemView.setOnClickListener(v -> {
                cbSelect.setChecked(!cbSelect.isChecked());
                notifySelectionChange();
            });

            cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (buttonView.isPressed()) { // Only handle user interactions
                    notifySelectionChange();
                }
            });
        }

        public void bind(User user) {
            // Set user data
            tvFullName.setText(user.getFullName() != null ? user.getFullName() : "Unknown User");
            tvUsername.setText(user.getUsername() != null ? "@" + user.getUsername() : "");

            // Reset checkbox state
            cbSelect.setOnCheckedChangeListener(null);
            cbSelect.setChecked(false);
            cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (buttonView.isPressed()) {
                    notifySelectionChange();
                }
            });

            // TODO: Load avatar image
            // For now, use a placeholder
        }

        private void notifySelectionChange() {
            if (listener != null) {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    User user = userList.get(position);
                    listener.onUserSelectionChanged(user, cbSelect.isChecked());
                }
            }
        }
    }
}