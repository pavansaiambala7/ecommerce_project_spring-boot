package com.jtspringproject.JtSpringProject.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.jtspringproject.JtSpringProject.dto.request.CatalogueQuery;
import com.jtspringproject.JtSpringProject.dto.response.ProductResponse;
import com.jtspringproject.JtSpringProject.services.OrderService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerSupportAgentTest {

    @Mock
    private ChatLanguageModel chatModel;

    @Mock
    private CatalogueSearchService catalogueSearchService;

    @Mock
    private OrderService orderService;

    private CustomerSupportAgent customerSupportAgent;

    private static final int USER_ID = 42;

    @BeforeEach
    void setUp() {
        // Constructed explicitly rather than with @InjectMocks: the cache bounds
        // are @Value constructor parameters, and Mockito would inject 0 for them,
        // producing a zero-capacity session cache.
        customerSupportAgent = new CustomerSupportAgent(chatModel, catalogueSearchService, orderService, 100, 60);
        when(catalogueSearchService.search(any())).thenReturn(new CatalogueSearchService.Page(List.of(), 0, 0, 6));
    }

    private static Response<AiMessage> reply(String text) {
        return Response.from(AiMessage.from(text));
    }

    private static ProductResponse product(int id, String name, String price) {
        ProductResponse p = new ProductResponse();
        p.setId(id);
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        p.setInStock(true);
        return p;
    }

    @Test
    void chat_shouldReturnAiResponse() {
        when(chatModel.generate(anyList())).thenReturn(reply("Hello! How can I help you today?"));

        CustomerSupportAgent.ChatResult result = customerSupportAgent.chat("session-1", "Hi", USER_ID);

        assertEquals("Hello! How can I help you today?", result.reply());
        assertTrue(!result.degraded());
    }

    @Test
    @SuppressWarnings("unchecked")
    void chat_shouldRetainSessionMemoryAcrossTurns() {
        when(chatModel.generate(anyList())).thenReturn(reply("Sure."));

        customerSupportAgent.chat("session-1", "First question", USER_ID);
        customerSupportAgent.chat("session-1", "Second question", USER_ID);

        // System prompt + first user turn + stored AI reply + second user turn.
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(chatModel, Mockito.times(2)).generate(captor.capture());
        assertEquals(4, captor.getAllValues().get(1).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void chat_shouldClearSessionOnRequest() {
        when(chatModel.generate(anyList())).thenReturn(reply("ok"));

        customerSupportAgent.chat("session-1", "First", USER_ID);
        customerSupportAgent.clearSession("session-1");
        customerSupportAgent.chat("session-1", "Second", USER_ID);

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(chatModel, Mockito.times(2)).generate(captor.capture());
        // Memory was dropped, so the second call starts fresh: prompt + 1 user turn.
        assertEquals(2, captor.getAllValues().get(1).size());
    }

    /**
     * What production did when gemini-2.0-flash was retired: every message
     * failed. The assistant must still answer, from retrieval alone.
     */
    @Test
    void chat_shouldAnswerFromTheCatalogueWhenTheModelIsUnavailable() {
        when(chatModel.generate(anyList())).thenThrow(new RuntimeException(
                "NOT_FOUND (code 404) This model models/gemini-2.0-flash is no longer available."));
        ProductResponse phone = product(7, "Redmi Note 13 5G (8GB RAM 256GB, Black)", "14999");
        when(catalogueSearchService.search(any())).thenReturn(new CatalogueSearchService.Page(List.of(phone), 1, 0, 6));
        when(catalogueSearchService.hasKeywordMatch(anyString())).thenReturn(true);

        CustomerSupportAgent.ChatResult result = customerSupportAgent.chat("s", "redmi phone", USER_ID);

        assertTrue(result.degraded());
        assertTrue(result.reply().contains("Redmi Note 13 5G"), result.reply());
        assertTrue(result.reply().contains("₹14,999"), result.reply());
        assertEquals(1, result.products().size());
    }

    @Test
    void chat_shouldNotShowProductsForSmallTalkWhenTheModelIsUnavailable() {
        when(chatModel.generate(anyList())).thenThrow(new RuntimeException("503 UNAVAILABLE"));
        when(catalogueSearchService.search(any())).thenReturn(
                new CatalogueSearchService.Page(List.of(product(1, "Yoga Mat", "499")), 1, 0, 6));
        when(catalogueSearchService.hasKeywordMatch(anyString())).thenReturn(false);

        CustomerSupportAgent.ChatResult result = customerSupportAgent.chat("s", "hello there", USER_ID);

        assertTrue(result.products().isEmpty());
        assertTrue(result.reply().contains("temporarily unavailable"));
    }

    @Test
    void chat_shouldSearchWithTheBudgetAsAPriceFilter() {
        when(chatModel.generate(anyList())).thenReturn(reply("Here are some options."));

        customerSupportAgent.chat("s", "best phones under 15,000 rupees", USER_ID);

        ArgumentCaptor<CatalogueQuery> query = ArgumentCaptor.forClass(CatalogueQuery.class);
        Mockito.verify(catalogueSearchService).search(query.capture());
        assertEquals(0, new BigDecimal("15000").compareTo(query.getValue().getMaxPrice()));
        assertEquals("phones", query.getValue().getQ());
    }

    @Test
    void chat_shouldShowCardsOnlyForProductsTheReplyNames() {
        ProductResponse named = product(1, "boAt Airdopes 141 (Bluetooth 5.3, Black)", "1299");
        ProductResponse ignored = product(2, "JBL Tune 520 (Wireless, Blue)", "3999");
        when(catalogueSearchService.search(any()))
                .thenReturn(new CatalogueSearchService.Page(List.of(ignored, named), 2, 0, 6));
        when(chatModel.generate(anyList())).thenReturn(reply("I'd pick the boAt Airdopes 141 for the price."));

        CustomerSupportAgent.ChatResult result = customerSupportAgent.chat("s", "earbuds", USER_ID);

        assertEquals(List.of(1), result.products().stream().map(ProductResponse::getId).toList());
    }

    @Test
    void parseIntent_shouldUnderstandIndianPriceFormats() {
        assertEquals(0, new BigDecimal("2000").compareTo(CustomerSupportAgent.parseIntent("shoes within 2k").maxPrice()));
        assertEquals(0, new BigDecimal("100000")
                .compareTo(CustomerSupportAgent.parseIntent("laptop below ₹1 lakh").maxPrice()));
        CustomerSupportAgent.ProductIntent range = CustomerSupportAgent.parseIntent("tv above 20000 under 40,000");
        assertEquals(0, new BigDecimal("20000").compareTo(range.minPrice()));
        assertEquals(0, new BigDecimal("40000").compareTo(range.maxPrice()));
        assertEquals("tv", range.searchText());
        assertNull(CustomerSupportAgent.parseIntent("red saree").maxPrice());
    }

    /**
     * The controller used to call {@code toLowerCase()} on the raw body value, so
     * a request without a message was an unhandled 500.
     */
    @Test
    void chat_shouldRejectBlankMessage() {
        assertThrows(IllegalArgumentException.class, () -> customerSupportAgent.chat("session-1", "  ", USER_ID));
        assertThrows(IllegalArgumentException.class, () -> customerSupportAgent.chat("session-1", null, USER_ID));
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
        assertTrue(customerSupportAgent.getSuggestedActions(null).contains("Check my orders"));
    }
}
