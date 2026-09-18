package com.jtspringproject.JtSpringProject.dto;

import java.util.List;

import com.jtspringproject.JtSpringProject.dto.response.ProductResponse;

public class ChatResponse {

    private String sessionId;
    private String reply;
    private List<String> suggestedActions;
    /** Products the reply refers to, so the chat can show them as clickable cards. */
    private List<ProductResponse> products = List.of();

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

    public List<ProductResponse> getProducts() {
        return products;
    }

    public void setProducts(List<ProductResponse> products) {
        this.products = products;
    }
}
