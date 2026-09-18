package com.jtspringproject.JtSpringProject.services;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jtspringproject.JtSpringProject.dao.productDao;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.models.Product;

@Service
public class productService {
	private final productDao productDao;

	public productService(productDao productDao) {
		this.productDao = productDao;
	}

	@Transactional(readOnly = true)
	public List<Product> getProducts() {
		return this.productDao.getProducts();
	}

	@Transactional(readOnly = true)
	public Page<Product> getProductsPaged(Pageable pageable) {
		return this.productDao.findAll(pageable);
	}

	@Transactional
	public Product addProduct(Product product) {
		return this.productDao.addProduct(product);
	}

	@Transactional(readOnly = true)
	public Product getProduct(int id) {
		return this.productDao.getProduct(id);
	}

	@Transactional(readOnly = true)
	public Product requireProduct(int id) {
		return this.productDao.findById(id)
				.orElseThrow(() -> ResourceNotFoundException.of("Product", id));
	}

	/**
	 * Applies changes to an existing product.
	 *
	 * <p>This used to set the id on a detached instance and call {@code save},
	 * which inserted a new row for an unknown id instead of reporting 404, and
	 * silently nulled every column the caller did not supply.
	 */
	@Transactional
	public Product updateProduct(int id, Product changes) {
		Product existing = requireProduct(id);

		existing.setName(changes.getName());
		existing.setDescription(changes.getDescription());
		existing.setImage(changes.getImage());
		existing.setPrice(changes.getPrice());
		existing.setMrp(changes.getMrp());
		existing.setWeight(changes.getWeight());
		existing.setQuantity(changes.getQuantity());
		if (changes.getCategory() != null) {
			existing.setCategory(changes.getCategory());
		}

		return this.productDao.save(existing);
	}

	@Transactional
	public boolean deleteProduct(int id) {
		return this.productDao.deleteProduct(id);
	}
}
