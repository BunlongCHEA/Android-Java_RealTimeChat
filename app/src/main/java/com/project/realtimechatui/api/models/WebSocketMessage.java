package com.project.realtimechatui.api.models;

import com.google.gson.annotations.SerializedName;

public class WebSocketMessage {
    @SerializedName("type")
    private String type; // MESSAGE, JOIN_CHAT, LEAVE_CHAT, USER_TYPING, etc.
    
    @SerializedName("chatId")
    private Long chatId;
    
    @SerializedName("senderId")
    private Long senderId;
    
    @SerializedName("content")
    private String content;
    
    @SerializedName("message")
    private Message message;
    
    @SerializedName("timestamp")
    private String timestamp;

    // Constructor
    public WebSocketMessage() {}

    public WebSocketMessage(String type, Long chatId, Long senderId, String content) {
        this.type = type;
        this.chatId = chatId;
        this.senderId = senderId;
        this.content = content;
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}