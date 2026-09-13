package com.jtspringproject.JtSpringProject.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jtspringproject.JtSpringProject.dao.categoryDao;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.models.Category;

@Service
public class categoryService {
	private final categoryDao categoryDao;

	public categoryService(categoryDao categoryDao) {
		this.categoryDao = categoryDao;
	}

	@Transactional
	public Category addCategory(String name) {
		return this.categoryDao.addCategory(name);
	}

	@Transactional(readOnly = true)
	public List<Category> getCategories() {
		return this.categoryDao.getCategories();
	}

	@Transactional
	public Boolean deleteCategory(int id) {
		return this.categoryDao.deleteCategory(id);
	}

	@Transactional
	public Category updateCategory(int id, String name) {
		Category updated = this.categoryDao.updateCategory(id, name);
		if (updated == null) {
			throw ResourceNotFoundException.of("Category", id);
		}
		return updated;
	}

	@Transactional(readOnly = true)
	public Category getCategory(int id) {
		return this.categoryDao.getCategory(id);
	}

	@Transactional(readOnly = true)
	public Category requireCategory(int id) {
		Category category = this.categoryDao.getCategory(id);
		if (category == null) {
			throw ResourceNotFoundException.of("Category", id);
		}
		return category;
	}
}
