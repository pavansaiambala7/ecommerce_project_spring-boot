package com.jtspringproject.JtSpringProject.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtspringproject.JtSpringProject.exception.GlobalApiExceptionHandler;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.models.Category;
import com.jtspringproject.JtSpringProject.models.Product;
import com.jtspringproject.JtSpringProject.models.User;
import com.jtspringproject.JtSpringProject.services.categoryService;
import com.jtspringproject.JtSpringProject.services.productService;
import com.jtspringproject.JtSpringProject.support.SliceTestConfig;

@WebMvcTest(ProductApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ SliceTestConfig.class, GlobalApiExceptionHandler.class })
class ProductApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private productService productService;

    @MockBean
    private categoryService categoryService;

    @MockBean
    private com.jtspringproject.JtSpringProject.ai.service.CatalogueSearchService catalogueSearchService;

    private Product testProduct;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = new Category();
        testCategory.setId(1);
        testCategory.setName("Fruits");

        testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Test Product");
        testProduct.setPrice(new BigDecimal("10.00"));
        testProduct.setQuantity(100);
        testProduct.setDescription("Sample description");
        testProduct.setCategory(testCategory);
    }

    private String validProductJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", "Test Product",
                "description", "Sample description",
                "price", "10.00",
                "quantity", 100,
                "weight", 5,
                "categoryId", 1));
    }

    @Test
    void getAllProducts_shouldReturnList() throws Exception {
        when(productService.getProducts()).thenReturn(List.of(testProduct));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Test Product"))
                .andExpect(jsonPath("$.data[0].category.name").value("Fruits"));
    }

    /**
     * The product response must never carry the owning user. Serialising the
     * entity exposed {@code customer}, and with it that user's password hash.
     */
    @Test
    void getAllProducts_shouldNotExposeOwningUser() throws Exception {
        User owner = new User();
        owner.setId(9);
        owner.setUsername("owner");
        owner.setPassword("$2b$10$someverysecrethash");
        testProduct.setCustomer(owner);

        when(productService.getProducts()).thenReturn(List.of(testProduct));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].customer").doesNotExist())
                .andExpect(jsonPath("$.data[0].password").doesNotExist());
    }

    @Test
    void getProductById_shouldReturnProductWhenFound() throws Exception {
        when(productService.requireProduct(1)).thenReturn(testProduct);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Test Product"));
    }

    @Test
    void getProductById_shouldReturn404WhenNotFound() throws Exception {
        when(productService.requireProduct(999))
                .thenThrow(ResourceNotFoundException.of("Product", 999));

        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createProduct_shouldReturnCreatedProduct() throws Exception {
        when(categoryService.requireCategory(1)).thenReturn(testCategory);
        when(productService.addProduct(any(Product.class))).thenReturn(testProduct);

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validProductJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Test Product"));
    }

    @Test
    void createProduct_shouldRejectNegativePrice() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Bad Product",
                "price", "-1.00",
                "quantity", 1,
                "weight", 1,
                "categoryId", 1));

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.price").exists());
    }

    @Test
    void createProduct_shouldRejectBlankName() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "",
                "price", "5.00",
                "quantity", 1,
                "weight", 1,
                "categoryId", 1));

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void updateProduct_shouldReturnUpdatedProduct() throws Exception {
        when(categoryService.requireCategory(1)).thenReturn(testCategory);
        when(productService.updateProduct(eq(1), any(Product.class))).thenReturn(testProduct);

        mockMvc.perform(put("/api/products/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validProductJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Regression: updating an unknown id used to insert a new row rather than
     * reporting that the product does not exist.
     */
    @Test
    void updateProduct_shouldReturn404ForUnknownId() throws Exception {
        when(categoryService.requireCategory(1)).thenReturn(testCategory);
        when(productService.updateProduct(eq(999), any(Product.class)))
                .thenThrow(ResourceNotFoundException.of("Product", 999));

        mockMvc.perform(put("/api/products/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validProductJson()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProductsPaged_shouldRejectUnsortableColumn() throws Exception {
        mockMvc.perform(get("/api/products/paged?sortBy=password"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void deleteProduct_shouldReturnSuccess() throws Exception {
        when(productService.deleteProduct(1)).thenReturn(true);

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
