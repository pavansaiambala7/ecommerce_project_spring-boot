package com.jtspringproject.JtSpringProject.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.jtspringproject.JtSpringProject.services.OrderService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerSupportAgentTest {

    @Mock
    private ChatLanguageModel chatModel;

    @Mock
    private RagProductSearchService ragSearchService;

    @Mock
    private OrderService orderService;

    private CustomerSupportAgent customerSupportAgent;

    @BeforeEach
    void setUp() {
        // Constructed explicitly rather than with @InjectMocks: the cache bounds
        // are @Value constructor parameters, and Mockito would inject 0 for them,
        // producing a zero-capacity session cache.
        customerSupportAgent = new CustomerSupportAgent(chatModel, ragSearchService, orderService, 100, 60);
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
    void chat_shouldRetainSessionMemoryAcrossTurns() {
        AiMessage aiMessage = AiMessage.from("Sure.");
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(aiMessage).build());

        customerSupportAgent.chat("session-1", "First question");
        customerSupportAgent.chat("session-1", "Second question");

        // System prompt + 2 user turns + 1 stored AI reply from the first turn.
        org.mockito.ArgumentCaptor<ChatRequest> captor =
                org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        org.mockito.Mockito.verify(chatModel, org.mockito.Mockito.times(2)).chat(captor.capture());
        assertEquals(4, captor.getAllValues().get(1).messages().size());
    }

    @Test
    void chat_shouldClearSessionOnRequest() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        customerSupportAgent.chat("session-1", "First");
        customerSupportAgent.clearSession("session-1");
        customerSupportAgent.chat("session-1", "Second");

        org.mockito.ArgumentCaptor<ChatRequest> captor =
                org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        org.mockito.Mockito.verify(chatModel, org.mockito.Mockito.times(2)).chat(captor.capture());
        // Memory was dropped, so the second call starts fresh: prompt + 1 user turn.
        assertEquals(2, captor.getAllValues().get(1).messages().size());
    }

    /**
     * The controller used to call {@code toLowerCase()} on the raw body value, so
     * a request without a message was an unhandled 500.
     */
    @Test
    void chat_shouldRejectBlankMessage() {
        assertThrows(IllegalArgumentException.class, () -> customerSupportAgent.chat("session-1", "  "));
        assertThrows(IllegalArgumentException.class, () -> customerSupportAgent.chat("session-1", null));
    }

    @Test
    void getSuggestedActions_shouldReturnRelevantSuggestions() {
        List<String> orderSuggestions = customerSupportAgent.getSuggestedActions("Where is my order?");
        assertTrue(orderSuggestions.contains("Check order status"));

        List<String> productSuggestions = customerSupportAgent.getSuggestedActions("Find apples");
        assertTrue(productSuggestions.contains("Search products"));
    }

    @Test
    void getSuggestedActions_shouldTolerateNull() {
        assertTrue(customerSupportAgent.getSuggestedActions(null).contains("Browse products"));
    }
}
