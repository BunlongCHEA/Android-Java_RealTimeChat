package com.project.realtimechatui.api.models;

import com.google.gson.annotations.SerializedName;

public class Message {
    @SerializedName("id")
    private Long id;
    
    @SerializedName("content")
    private String content;
    
    @SerializedName("sender")
    private User sender;
    
    @SerializedName("chatId")
    private Long chatId;
    
    @SerializedName("messageType")
    private String messageType; // TEXT, IMAGE, FILE, etc.
    
    @SerializedName("timestamp")
    private String timestamp;
    
    @SerializedName("isRead")
    private boolean isRead;

    // Constructor
    public Message() {}

    public Message(String content, User sender, Long chatId) {
        this.content = content;
        this.sender = sender;
        this.chatId = chatId;
        this.messageType = "TEXT";
        this.isRead = false;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }
}