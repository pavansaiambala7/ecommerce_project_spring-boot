package com.jtspringproject.JtSpringProject.controller.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.jtspringproject.JtSpringProject.ai.service.CustomerSupportAgent;
import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.ChatRequest;
import com.jtspringproject.JtSpringProject.dto.ChatResponse;

@RestController
@RequestMapping("/api/chat")
public class ChatApiController {

    private final CustomerSupportAgent supportAgent;

    public ChatApiController(CustomerSupportAgent supportAgent) {
        this.supportAgent = supportAgent;
    }

    /**
     * Send a message to the AI customer support assistant.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request) {
        // Generate session ID if not provided
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        String reply = supportAgent.chat(sessionId, request.getMessage());
        List<String> suggestedActions = supportAgent.getSuggestedActions(request.getMessage());

        ChatResponse response = new ChatResponse(sessionId, reply, suggestedActions);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Clear conversation history for a session.
     */
    @DeleteMapping("/history/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> clearHistory(@PathVariable String sessionId) {
        supportAgent.clearSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Chat history cleared", null));
    }
}
