package com.project.realtimechatui.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ChatRoom {
    @SerializedName("id")
    private Long id;
    
    @SerializedName("name")
    private String name;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("createdBy")
    private Long createdBy;
    
    @SerializedName("createdAt")
    private String createdAt;
    
    @SerializedName("participants")
    private List<User> participants;
    
    @SerializedName("lastMessage")
    private Message lastMessage;
    
    @SerializedName("isPrivate")
    private boolean isPrivate;

    public ChatRoom() {}

    public ChatRoom(String name, String description, Long createdBy, boolean isPrivate) {
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
        this.isPrivate = isPrivate;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public List<User> getParticipants() {
        return participants;
    }

    public void setParticipants(List<User> participants) {
        this.participants = participants;
    }

    public Message getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(Message lastMessage) {
        this.lastMessage = lastMessage;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }
}