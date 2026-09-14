package com.jtspringproject.JtSpringProject.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.jtspringproject.JtSpringProject.ai.service.CatalogueSearchService;
import com.jtspringproject.JtSpringProject.ai.service.EmbeddingService;
import com.jtspringproject.JtSpringProject.controller.api.SearchApiController;
import com.jtspringproject.JtSpringProject.dto.response.ProductResponse;

@WebMvcTest(SearchApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ com.jtspringproject.JtSpringProject.support.SliceTestConfig.class,
        com.jtspringproject.JtSpringProject.exception.GlobalApiExceptionHandler.class })
class SearchApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogueSearchService catalogueSearchService;

    @MockBean
    private EmbeddingService embeddingService;

    private static CatalogueSearchService.Page onePage() {
        ProductResponse product = new ProductResponse();
        product.setId(1);
        product.setName("Organic Apples");
        product.setPrice(new BigDecimal("5.00"));
        return new CatalogueSearchService.Page(List.of(product), 1, 0, 5);
    }

    @Test
    void searchProducts_shouldReturnSearchResults() throws Exception {
        when(catalogueSearchService.search(any())).thenReturn(onePage());

        mockMvc.perform(get("/api/search?q=apples"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Organic Apples"));
    }

    @Test
    void searchProducts_shouldRejectLimitAboveMaximum() throws Exception {
        // Each search spends a paid embedding call, so the bound is enforced
        // rather than passed through to the database.
        mockMvc.perform(get("/api/search?q=apples&limit=500"))
                .andExpect(status().isConflict());
    }

    @Test
    void reindex_shouldOnlyEmbedMissingByDefault() throws Exception {
        // The default must stay incremental: a full reindex re-embeds the whole
        // catalogue, which at scale is thousands of paid calls.
        when(embeddingService.embedMissing()).thenReturn(10);
        when(embeddingService.countMissing()).thenReturn(0);

        mockMvc.perform(post("/api/search/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productsIndexed").value(10))
                .andExpect(jsonPath("$.data.stillMissing").value(0));

        verify(embeddingService, never()).reindexAll();
    }

    @Test
    void reindex_shouldRebuildEverythingWhenAskedExplicitly() throws Exception {
        when(embeddingService.reindexAll()).thenReturn(97);
        when(embeddingService.countMissing()).thenReturn(0);

        mockMvc.perform(post("/api/search/reindex?full=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productsIndexed").value(97));

        verify(embeddingService, never()).embedMissing();
    }
}
