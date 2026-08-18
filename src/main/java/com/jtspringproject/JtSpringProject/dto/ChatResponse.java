package com.jtspringproject.JtSpringProject.dto;

import java.util.List;

public class ChatResponse {

    private String sessionId;
    private String reply;
    private List<String> suggestedActions;

    public ChatResponse() {}

    public ChatResponse(String sessionId, String reply, List<String> suggestedActions) {
        this.sessionId = sessionId;
        this.reply = reply;
        this.suggestedActions = suggestedActions;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public List<String> getSuggestedActions() {
        return suggestedActions;
    }

    public void setSuggestedActions(List<String> suggestedActions) {
        this.suggestedActions = suggestedActions;
    }
}
