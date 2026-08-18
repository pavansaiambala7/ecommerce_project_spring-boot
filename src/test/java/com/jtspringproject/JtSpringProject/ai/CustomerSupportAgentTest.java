package com.jtspringproject.JtSpringProject.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jtspringproject.JtSpringProject.services.OrderService;
import com.jtspringproject.JtSpringProject.services.productService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

@ExtendWith(MockitoExtension.class)
class CustomerSupportAgentTest {

    @Mock
    private ChatLanguageModel chatModel;

    @Mock
    private RagProductSearchService ragSearchService;

    @Mock
    private OrderService orderService;

    @Mock
    private productService productService;

    @InjectMocks
    private CustomerSupportAgent customerSupportAgent;

    @BeforeEach
    void setUp() {
    }

    @Test
    void chat_shouldReturnAiResponse() {
        AiMessage aiMessage = AiMessage.from("Hello! How can I help you today?");
        ChatResponse chatResponse = ChatResponse.builder().aiMessage(aiMessage).build();
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse);

        String reply = customerSupportAgent.chat("session-1", "Hi");

        assertEquals("Hello! How can I help you today?", reply);
    }

    @Test
    void getSuggestedActions_shouldReturnRelevantSuggestions() {
        List<String> orderSuggestions = customerSupportAgent.getSuggestedActions("Where is my order?");
        assertTrue(orderSuggestions.contains("Check order status"));

        List<String> productSuggestions = customerSupportAgent.getSuggestedActions("Find apples");
        assertTrue(productSuggestions.contains("Search products"));
    }
}
