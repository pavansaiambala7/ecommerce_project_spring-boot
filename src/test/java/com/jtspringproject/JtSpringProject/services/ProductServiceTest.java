package com.jtspringproject.JtSpringProject.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.jtspringproject.JtSpringProject.dao.productDao;
import com.jtspringproject.JtSpringProject.models.Category;
import com.jtspringproject.JtSpringProject.models.Product;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private productDao productDao;

    @InjectMocks
    private productService productService;

    private Product testProduct;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = new Category();
        testCategory.setId(1);
        testCategory.setName("Fruits");

        testProduct = new Product();
        testProduct.setId(1);
        testProduct.setName("Apple");
        testProduct.setDescription("Fresh and juicy");
        testProduct.setPrice(3);
        testProduct.setQuantity(40);
        testProduct.setWeight(76);
        testProduct.setCategory(testCategory);
    }

    @Test
    void getProducts_shouldReturnAllProducts() {
        List<Product> products = Arrays.asList(testProduct);
        when(productDao.getProducts()).thenReturn(products);

        List<Product> result = productService.getProducts();

        assertEquals(1, result.size());
        assertEquals("Apple", result.get(0).getName());
        verify(productDao).getProducts();
    }

    @Test
    void getProducts_shouldReturnEmptyListWhenNoProducts() {
        when(productDao.getProducts()).thenReturn(List.of());

        List<Product> result = productService.getProducts();

        assertTrue(result.isEmpty());
    }

    @Test
    void getProductsPaged_shouldReturnPagedResults() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(testProduct));
        when(productDao.findAll(pageable)).thenReturn(page);

        Page<Product> result = productService.getProductsPaged(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Apple", result.getContent().get(0).getName());
    }

    @Test
    void addProduct_shouldSaveAndReturnProduct() {
        when(productDao.addProduct(testProduct)).thenReturn(testProduct);

        Product result = productService.addProduct(testProduct);

        assertNotNull(result);
        assertEquals("Apple", result.getName());
        verify(productDao).addProduct(testProduct);
    }

    @Test
    void getProduct_shouldReturnProductWhenExists() {
        when(productDao.getProduct(1)).thenReturn(testProduct);

        Product result = productService.getProduct(1);

        assertNotNull(result);
        assertEquals(1, result.getId());
    }

    @Test
    void getProduct_shouldReturnNullWhenNotExists() {
        when(productDao.getProduct(999)).thenReturn(null);

        Product result = productService.getProduct(999);

        assertNull(result);
    }

    @Test
    void updateProduct_shouldSetIdAndUpdate() {
        when(productDao.updateProduct(any(Product.class))).thenReturn(testProduct);

        Product result = productService.updateProduct(1, testProduct);

        assertEquals(1, testProduct.getId());
        assertNotNull(result);
        verify(productDao).updateProduct(testProduct);
    }

    @Test
    void deleteProduct_shouldReturnTrueWhenExists() {
        when(productDao.deleteProduct(1)).thenReturn(true);

        boolean result = productService.deleteProduct(1);

        assertTrue(result);
        verify(productDao).deleteProduct(1);
    }

    @Test
    void deleteProduct_shouldReturnFalseWhenNotExists() {
        when(productDao.deleteProduct(999)).thenReturn(false);

        boolean result = productService.deleteProduct(999);

        assertFalse(result);
    }
}
