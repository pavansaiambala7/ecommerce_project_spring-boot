package com.jtspringproject.JtSpringProject.dto.response;

import com.jtspringproject.JtSpringProject.models.Category;

public class CategoryResponse {

    private int id;
    private String name;

    public CategoryResponse() {
    }

    public CategoryResponse(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public static CategoryResponse from(Category category) {
        return category == null ? null : new CategoryResponse(category.getId(), category.getName());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
