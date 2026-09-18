package com.jtspringproject.JtSpringProject.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtspringproject.JtSpringProject.controller.api.OrderApiController;
import com.jtspringproject.JtSpringProject.exception.GlobalApiExceptionHandler;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.models.User;
import com.jtspringproject.JtSpringProject.services.OrderService;
import com.jtspringproject.JtSpringProject.support.SliceTestConfig;
import com.jtspringproject.JtSpringProject.support.WithMockAppUser;

@WebMvcTest(OrderApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ SliceTestConfig.class, GlobalApiExceptionHandler.class })
class OrderApiControllerTest {

    private static final int CALLER_ID = 1;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private com.jtspringproject.JtSpringProject.services.AddressService addressService;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        User customer = new User();
        customer.setId(CALLER_ID);
        customer.setUsername("testuser");
        customer.setPassword("$2b$10$secrethashvaluethatmustnotleak");

        testOrder = new Order();
        testOrder.setId(1);
        testOrder.setCustomer(customer);
        testOrder.setTotalAmount(new BigDecimal("50.00"));
        testOrder.setStatus(Order.OrderStatus.CREATED);
    }

    private String orderRequestJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", 1, "quantity", 2))));
    }

    @Test
    @WithMockAppUser(id = CALLER_ID)
    void createOrder_shouldReturnCreatedOrder() throws Exception {
        when(orderService.createOrder(anyInt(), anyList())).thenReturn(testOrder);

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    /**
     * The order response must not carry the customer entity. Serialising it
     * exposed the account's password hash, and Order to OrderItem to Order sent
     * Jackson into unbounded recursion.
     */
    @Test
    @WithMockAppUser(id = CALLER_ID)
    void getOrderById_shouldNotExposeCustomerPassword() throws Exception {
        when(orderService.getOrderForCaller(eq(1), eq(CALLER_ID), eq(false))).thenReturn(testOrder);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value(CALLER_ID))
                .andExpect(jsonPath("$.data.customerUsername").value("testuser"))
                .andExpect(jsonPath("$.data.customer").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @WithMockAppUser(id = CALLER_ID)
    void getOrderById_shouldReturnOrderWhenFound() throws Exception {
        when(orderService.getOrderForCaller(eq(1), eq(CALLER_ID), eq(false))).thenReturn(testOrder);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    @Test
    @WithMockAppUser(id = CALLER_ID)
    void getOrderById_shouldReturn404WhenNotFound() throws Exception {
        when(orderService.getOrderForCaller(eq(999), eq(CALLER_ID), eq(false)))
                .thenThrow(ResourceNotFoundException.of("Order", 999));

        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound());
    }

    /**
     * Regression: any caller could read any order by id.
     */
    @Test
    @WithMockAppUser(id = CALLER_ID)
    void getOrderById_shouldReturn403ForSomeoneElsesOrder() throws Exception {
        when(orderService.getOrderForCaller(eq(42), eq(CALLER_ID), eq(false)))
                .thenThrow(new AccessDeniedException("nope"));

        mockMvc.perform(get("/api/orders/42"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockAppUser(id = CALLER_ID)
    void getMyOrders_shouldReturnCallerOrders() throws Exception {
        when(orderService.getOrdersByUser(CALLER_ID)).thenReturn(List.of(testOrder));

        mockMvc.perform(get("/api/orders/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @WithMockAppUser(id = CALLER_ID)
    void cancelOrder_shouldReturnCancelledOrder() throws Exception {
        testOrder.setStatus(Order.OrderStatus.CANCELLED);
        when(orderService.cancelOrder(eq(1), eq(CALLER_ID), eq(false))).thenReturn(testOrder);

        mockMvc.perform(post("/api/orders/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @WithMockAppUser(id = CALLER_ID)
    void createOrder_shouldRejectEmptyItemList() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("items", List.of()));

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").exists());
    }

    @Test
    @WithMockAppUser(id = CALLER_ID)
    void createOrder_shouldRejectZeroQuantity() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", 1, "quantity", 0))));

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }
}
