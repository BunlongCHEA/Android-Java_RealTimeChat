package com.project.realtimechatui.api.models;

import com.google.gson.annotations.SerializedName;

public class RegisterResponse extends BaseDTO {
    private User data;

    public User getUser() {
        return data;
    }

    public void setUser(User data) {
        this.data = data;
    }
}
