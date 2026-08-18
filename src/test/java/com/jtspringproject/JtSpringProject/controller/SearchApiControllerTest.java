package com.jtspringproject.JtSpringProject.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jtspringproject.JtSpringProject.ai.service.EmbeddingService;
import com.jtspringproject.JtSpringProject.ai.service.RagProductSearchService;
import com.jtspringproject.JtSpringProject.controller.api.SearchApiController;
import com.jtspringproject.JtSpringProject.models.Product;

@WebMvcTest(SearchApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class SearchApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RagProductSearchService ragSearchService;

    @MockBean
    private EmbeddingService embeddingService;

    @Test
    void searchProducts_shouldReturnSearchResults() throws Exception {
        Product p = new Product();
        p.setId(1);
        p.setName("Organic Apples");
        p.setPrice(5);

        RagProductSearchService.SearchResult searchResult = new RagProductSearchService.SearchResult(p, 0.95);
        when(ragSearchService.searchProducts(anyString(), anyInt())).thenReturn(List.of(searchResult));

        mockMvc.perform(get("/api/search?q=apples"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].product.name").value("Organic Apples"))
                .andExpect(jsonPath("$.data[0].relevanceScore").value(0.95));
    }

    @Test
    void reindexProducts_shouldTriggerEmbeddingGeneration() throws Exception {
        when(embeddingService.embedAllProducts()).thenReturn(10);

        mockMvc.perform(post("/api/search/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productsIndexed").value(10));
    }
}
