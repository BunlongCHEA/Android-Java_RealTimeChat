package com.project.realtimechatui.api.models;

import com.project.realtimechatui.enums.EnumRoomType;

import java.util.List;
import java.util.Set;

public class ChatRoom {
    private Long id;
    private String name;
    private EnumRoomType type; // Changed from String to EnumRoomType
    private Set<Participant> participants;
    private Long lastMessageId;
    private String lastMessageContent;
    private String lastMessageSenderUsername;
    private String lastMessageTimestamp;
    private String lastMessageType;
    private Integer lastMessageAttachmentCount;


    // Helper method to get the other participant (for PERSONAL chats)
    public Participant getOtherParticipant(Long currentUserId) {
        if (participants != null) {
            for (Participant participant : participants) {
                if (participant.getUserId() != null && !participant.getUserId().equals(currentUserId)) {
                    return participant;
                }
            }
        }
        return null;
    }

    // Helper method to check if there's a last message
    public boolean hasLastMessage() {
        return lastMessageContent != null && !lastMessageContent.trim().isEmpty();
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

    public EnumRoomType getType() {
        return type;
    }

    public void setType(EnumRoomType type) {
        this.type = type;
    }

    public Set<Participant> getParticipants() {
        return participants;
    }

    public void setParticipants(Set<Participant> participants) {
        this.participants = participants;
    }

    public Long getLastMessageId() {
        return lastMessageId;
    }

    public void setLastMessageId(Long lastMessageId) {
        this.lastMessageId = lastMessageId;
    }

    public String getLastMessageContent() {
        return lastMessageContent;
    }

    public void setLastMessageContent(String lastMessageContent) {
        this.lastMessageContent = lastMessageContent;
    }

    public String getLastMessageSenderUsername() {
        return lastMessageSenderUsername;
    }

    public void setLastMessageSenderUsername(String lastMessageSenderUsername) {
        this.lastMessageSenderUsername = lastMessageSenderUsername;
    }

    public String getLastMessageTimestamp() {
        return lastMessageTimestamp;
    }

    public void setLastMessageTimestamp(String lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }

    public String getLastMessageType() {
        return lastMessageType;
    }

    public void setLastMessageType(String lastMessageType) {
        this.lastMessageType = lastMessageType;
    }

    public Integer getLastMessageAttachmentCount() {
        return lastMessageAttachmentCount;
    }

    public void setLastMessageAttachmentCount(Integer lastMessageAttachmentCount) {
        this.lastMessageAttachmentCount = lastMessageAttachmentCount;
    }
}
