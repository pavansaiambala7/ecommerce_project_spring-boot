package com.jtspringproject.JtSpringProject.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtspringproject.JtSpringProject.controller.api.OrderApiController;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.services.OrderService;

@WebMvcTest(OrderApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        testOrder = new Order();
        testOrder.setId(1);
        testOrder.setTotalAmount(50.0);
        testOrder.setStatus(Order.OrderStatus.CREATED);
    }

    @Test
    void createOrder_shouldReturnCreatedOrder() throws Exception {
        when(orderService.createOrder(anyInt(), anyList())).thenReturn(testOrder);

        mockMvc.perform(post("/api/orders?userId=1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getOrderById_shouldReturnOrderWhenFound() throws Exception {
        when(orderService.getOrderById(1)).thenReturn(testOrder);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    @Test
    void getOrderById_shouldReturn404WhenNotFound() throws Exception {
        when(orderService.getOrderById(999)).thenReturn(null);

        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrdersByUser_shouldReturnUserOrders() throws Exception {
        when(orderService.getOrdersByUser(1)).thenReturn(List.of(testOrder));

        mockMvc.perform(get("/api/orders/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    void cancelOrder_shouldReturnCancelledOrder() throws Exception {
        testOrder.setStatus(Order.OrderStatus.CANCELLED);
        when(orderService.cancelOrder(1)).thenReturn(testOrder);

        mockMvc.perform(post("/api/orders/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}
