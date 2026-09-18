package com.jtspringproject.JtSpringProject.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.ai.service.CustomerSupportAgent;
import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.ChatRequest;
import com.jtspringproject.JtSpringProject.dto.ChatResponse;
import com.jtspringproject.JtSpringProject.security.AppUserDetails;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/chat")
public class ChatApiController {

    private final CustomerSupportAgent supportAgent;

    public ChatApiController(CustomerSupportAgent supportAgent) {
        this.supportAgent = supportAgent;
    }

    /**
     * Sends a message to the AI support assistant.
     *
     * <p>Session ids are namespaced per user so one caller cannot read or poison
     * another caller's conversation by guessing their session id, and cannot grow
     * the session cache under someone else's identity.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @AuthenticationPrincipal AppUserDetails principal,
            @Valid @RequestBody ChatRequest request) {

        String sessionId = scopedSessionId(principal, request.getSessionId());

        CustomerSupportAgent.ChatResult result = supportAgent.chat(sessionId, request.getMessage(), principal.getId());
        List<String> suggestedActions = supportAgent.getSuggestedActions(request.getMessage());

        ChatResponse response = new ChatResponse(request.getSessionId(), result.reply(), suggestedActions);
        response.setProducts(result.products());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/history/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> clearHistory(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable String sessionId) {
        supportAgent.clearSession(scopedSessionId(principal, sessionId));
        return ResponseEntity.ok(ApiResponse.success("Chat history cleared", null));
    }

    private String scopedSessionId(AppUserDetails principal, String clientSessionId) {
        String suffix = (clientSessionId == null || clientSessionId.isBlank()) ? "default" : clientSessionId;
        return principal.getId() + ":" + suffix;
    }
}
