package com.project.realtimechatui.api.models;

import java.util.Set;

public class ChatMessage {
    private Long id;
    private Long chatRoomId;
    private Long senderId;
    private String senderName;
    private String content;
    private String type; // TEXT, IMAGE, FILE, SYSTEM
    private String timestamp;
    private Set<String> attachmentUrls;
    private boolean edited;
    private String editedAt;


    // Constructors
    public ChatMessage() {}
    public ChatMessage(Long chatRoomId, Long senderId, String content, String type) {
        this.chatRoomId = chatRoomId;
        this.senderId = senderId;
        this.content = content;
        this.type = type;
        this.timestamp = String.valueOf(System.currentTimeMillis());
    }


    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public Long getChatRoomId() {
        return chatRoomId;
    }
    public void setChatRoomId(Long chatRoomId) {
        this.chatRoomId = chatRoomId;
    }

    public Long getSenderId() {
        return senderId;
    }
    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }
    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }

    public String getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Set<String> getAttachmentUrls() {
        return attachmentUrls;
    }
    public void setAttachmentUrls(Set<String> attachmentUrls) {
        this.attachmentUrls = attachmentUrls;
    }

    public boolean isEdited() { return edited; }
    public void setEdited(boolean edited) { this.edited = edited; }

    public String getEditedAt() { return editedAt; }
    public void setEditedAt(String editedAt) { this.editedAt = editedAt; }


    // Helper methods
    public boolean isFromCurrentUser(Long currentUserId) {
        return senderId != null && senderId.equals(currentUserId);
    }

    public boolean isSystemMessage() {
        return "SYSTEM".equals(type);
    }

    public boolean hasAttachments() {
        return attachmentUrls != null && !attachmentUrls.isEmpty();
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "id=" + id +
                ", chatRoomId=" + chatRoomId +
                ", senderId=" + senderId +
                ", senderName='" + senderName + '\'' +
                ", content='" + content + '\'' +
                ", type='" + type + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}
