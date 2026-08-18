package com.jtspringproject.JtSpringProject.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jtspringproject.JtSpringProject.dao.categoryDao;
import com.jtspringproject.JtSpringProject.models.Category;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private categoryDao categoryDao;

    @InjectMocks
    private categoryService categoryService;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = new Category();
        testCategory.setId(1);
        testCategory.setName("Fruits");
    }

    @Test
    void addCategory_shouldCreateAndReturnCategory() {
        when(categoryDao.addCategory("Fruits")).thenReturn(testCategory);

        Category result = categoryService.addCategory("Fruits");

        assertNotNull(result);
        assertEquals("Fruits", result.getName());
        verify(categoryDao).addCategory("Fruits");
    }

    @Test
    void getCategories_shouldReturnAllCategories() {
        List<Category> categories = Arrays.asList(testCategory);
        when(categoryDao.getCategories()).thenReturn(categories);

        List<Category> result = categoryService.getCategories();

        assertEquals(1, result.size());
        assertEquals("Fruits", result.get(0).getName());
    }

    @Test
    void deleteCategory_shouldReturnTrueWhenExists() {
        when(categoryDao.deleteCategory(1)).thenReturn(true);

        Boolean result = categoryService.deleteCategory(1);

        assertTrue(result);
    }

    @Test
    void deleteCategory_shouldReturnFalseWhenNotExists() {
        when(categoryDao.deleteCategory(999)).thenReturn(false);

        Boolean result = categoryService.deleteCategory(999);

        assertFalse(result);
    }

    @Test
    void updateCategory_shouldUpdateAndReturnCategory() {
        when(categoryDao.updateCategory(1, "Vegetables")).thenReturn(testCategory);

        Category result = categoryService.updateCategory(1, "Vegetables");

        assertNotNull(result);
        verify(categoryDao).updateCategory(1, "Vegetables");
    }

    @Test
    void getCategory_shouldReturnCategoryWhenExists() {
        when(categoryDao.getCategory(1)).thenReturn(testCategory);

        Category result = categoryService.getCategory(1);

        assertNotNull(result);
        assertEquals(1, result.getId());
    }

    @Test
    void getCategory_shouldReturnNullWhenNotExists() {
        when(categoryDao.getCategory(999)).thenReturn(null);

        Category result = categoryService.getCategory(999);

        assertNull(result);
    }
}
