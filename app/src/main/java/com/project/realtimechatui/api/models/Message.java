package com.project.realtimechatui.api.models;

import com.google.gson.annotations.SerializedName;

public class Message {
    @SerializedName("id")
    private Long id;
    
    @SerializedName("content")
    private String content;
    
    @SerializedName("senderId")
    private Long senderId;
    
    @SerializedName("senderUsername")
    private String senderUsername;
    
    @SerializedName("chatRoomId")
    private Long chatRoomId;
    
    @SerializedName("timestamp")
    private String timestamp;
    
    @SerializedName("messageType")
    private String messageType;

    public Message() {}

    public Message(String content, Long senderId, String senderUsername, Long chatRoomId) {
        this.content = content;
        this.senderId = senderId;
        this.senderUsername = senderUsername;
        this.chatRoomId = chatRoomId;
        this.messageType = "TEXT";
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

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }

    public Long getChatRoomId() {
        return chatRoomId;
    }

    public void setChatRoomId(Long chatRoomId) {
        this.chatRoomId = chatRoomId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
}