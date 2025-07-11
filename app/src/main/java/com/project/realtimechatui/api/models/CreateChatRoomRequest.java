package com.project.realtimechatui.api.models;

import java.util.List;

public class CreateChatRoomRequest {
    private String name;
    private String type;
    private List<String> participants;

    public CreateChatRoomRequest() {
    }

    public CreateChatRoomRequest(String name, String type, List<String> participants) {
        this.name = name;
        this.type = type;
        this.participants = participants;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getParticipants() {
        return participants;
    }

    public void setParticipants(List<String> participants) {
        this.participants = participants;
    }
}
