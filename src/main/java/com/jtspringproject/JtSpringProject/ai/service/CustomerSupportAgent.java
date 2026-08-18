package com.jtspringproject.JtSpringProject.ai.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.models.Product;
import com.jtspringproject.JtSpringProject.services.OrderService;
import com.jtspringproject.JtSpringProject.services.productService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

@Service
public class CustomerSupportAgent {

    private static final Logger log = LoggerFactory.getLogger(CustomerSupportAgent.class);
    private static final int MEMORY_WINDOW_SIZE = 20;

    private static final String SYSTEM_PROMPT = """
            You are an AI customer support assistant for an e-commerce grocery store.
            You help customers with:
            - Finding products and getting product recommendations
            - Checking order status and order history
            - Answering questions about shipping, returns, and payments
            - Providing information about product availability and pricing
            
            Be friendly, concise, and helpful. If you don't know something,
            say so honestly. Use the product and order context provided to give
            accurate answers.
            
            Available payment methods: COD (Cash on Delivery), CARD, UPI.
            Order statuses: CREATED, PAID, SHIPPED, DELIVERED, CANCELLED.
            """;

    private final ChatLanguageModel chatModel;
    private final RagProductSearchService ragSearchService;
    private final OrderService orderService;
    private final productService productService;
    private final Map<String, ChatMemory> sessionMemories = new ConcurrentHashMap<>();

    public CustomerSupportAgent(ChatLanguageModel chatModel,
                                RagProductSearchService ragSearchService,
                                OrderService orderService,
                                productService productService) {
        this.chatModel = chatModel;
        this.ragSearchService = ragSearchService;
        this.orderService = orderService;
        this.productService = productService;
    }

    /**
     * Process a customer message and return an AI-generated response.
     */
    public String chat(String sessionId, String userMessage) {
        log.info("Chat session '{}': user said '{}'", sessionId, userMessage);

        ChatMemory memory = sessionMemories.computeIfAbsent(sessionId,
                id -> MessageWindowChatMemory.withMaxMessages(MEMORY_WINDOW_SIZE));

        // Build augmented context using RAG
        String context = buildContext(userMessage);

        // Build the augmented prompt
        String augmentedMessage = userMessage;
        if (!context.isEmpty()) {
            augmentedMessage = "Context:\n" + context + "\n\nUser question: " + userMessage;
        }

        // Add to memory
        memory.add(UserMessage.from(augmentedMessage));

        // Build messages list
        var messages = new java.util.ArrayList<>(List.of(SystemMessage.from(SYSTEM_PROMPT)));
        messages.addAll(memory.messages());

        // Call Gemini
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .build();

        ChatResponse response = chatModel.chat(request);
        String reply = response.aiMessage().text();

        // Store AI response in memory
        memory.add(AiMessage.from(reply));

        log.info("Chat session '{}': AI replied with {} chars", sessionId, reply.length());
        return reply;
    }

    /**
     * Get suggested follow-up actions based on the conversation.
     */
    public List<String> getSuggestedActions(String userMessage) {
        String lowerMsg = userMessage.toLowerCase();

        if (lowerMsg.contains("order") || lowerMsg.contains("track")) {
            return Arrays.asList("Check order status", "View order history", "Cancel order");
        } else if (lowerMsg.contains("product") || lowerMsg.contains("find") || lowerMsg.contains("search")) {
            return Arrays.asList("Search products", "View categories", "Check availability");
        } else if (lowerMsg.contains("pay") || lowerMsg.contains("refund")) {
            return Arrays.asList("Payment methods", "Request refund", "Payment status");
        }
        return Arrays.asList("Browse products", "Check orders", "Contact support");
    }

    /**
     * Clear conversation history for a session.
     */
    public void clearSession(String sessionId) {
        sessionMemories.remove(sessionId);
        log.info("Cleared chat session: {}", sessionId);
    }

    /**
     * Build context from RAG search and available tools.
     */
    private String buildContext(String userMessage) {
        StringBuilder context = new StringBuilder();

        // RAG product search context
        try {
            String productContext = ragSearchService.buildSearchContext(userMessage);
            if (productContext != null && !productContext.contains("No relevant products")) {
                context.append(productContext);
            }
        } catch (Exception e) {
            log.warn("RAG search failed for context building", e);
        }

        // If message mentions order ID, fetch order details
        try {
            String orderId = extractOrderId(userMessage);
            if (orderId != null) {
                Order order = orderService.getOrderById(Integer.parseInt(orderId));
                if (order != null) {
                    context.append("\nOrder #").append(order.getId())
                            .append(": Status=").append(order.getStatus())
                            .append(", Total=$").append(order.getTotalAmount())
                            .append(", Items=").append(order.getItems().size());
                }
            }
        } catch (Exception e) {
            log.warn("Order lookup failed for context building", e);
        }

        return context.toString();
    }

    /**
     * Extract order ID from user message if present.
     */
    private String extractOrderId(String message) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\border[#\\s]*(\\d+)\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }
}
