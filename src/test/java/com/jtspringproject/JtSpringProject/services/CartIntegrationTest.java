package com.jtspringproject.JtSpringProject.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.jtspringproject.JtSpringProject.dto.request.AddressRequest;
import com.jtspringproject.JtSpringProject.models.Address;
import com.jtspringproject.JtSpringProject.models.CartProduct;
import com.jtspringproject.JtSpringProject.support.PostgresTestBase;

/**
 * Cart changes against a real database.
 *
 * <p>These exist because the mocked service tests could not see the bug they
 * now cover: removing an item called the repository's delete, the test verified
 * that call, and the row still came back. {@code Cart.items} cascades ALL, so
 * with the cart loaded and still holding the line, the cascade re-saved it at
 * flush time - visible only when something actually reads the row back.
 */
@SpringBootTest
@ActiveProfiles("test")
class CartIntegrationTest extends PostgresTestBase {

	@Autowired
	private cartService cartService;
	@Autowired
	private AddressService addressService;
	@Autowired
	private JdbcTemplate jdbc;

	private int customerId;
	private int firstProductId;
	private int secondProductId;

	@BeforeEach
	void setUp() {
		customerId = jdbc.queryForObject("SELECT id FROM customer WHERE username = 'lisa'", Integer.class);
		List<Integer> products = jdbc.queryForList(
				"SELECT product_id FROM product WHERE quantity > 5 ORDER BY product_id LIMIT 2", Integer.class);
		firstProductId = products.get(0);
		secondProductId = products.get(1);
		cartService.clear(customerId);
	}

	@AfterEach
	void tearDown() {
		cartService.clear(customerId);
		jdbc.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE ship_pincode = '560091')");
		jdbc.update("DELETE FROM orders WHERE ship_pincode = '560091'");
		jdbc.update("DELETE FROM address WHERE pincode = '560091'");
	}

	private List<Integer> productIdsInCart() {
		return jdbc.queryForList(
				"SELECT product_id FROM cart_product cp JOIN cart c ON c.id = cp.cart_id WHERE c.customer_id = ?",
				Integer.class, customerId);
	}

	@Test
	void removeItem_shouldActuallyDeleteTheRow() {
		cartService.addItem(customerId, firstProductId, 2);
		cartService.addItem(customerId, secondProductId, 1);
		assertEquals(2, productIdsInCart().size());

		cartService.removeItem(customerId, firstProductId);

		// Read from the database, not from the returned object: the bug this
		// covers reported success while leaving the row in place.
		assertEquals(List.of(secondProductId), productIdsInCart());
		assertEquals(1, cartService.getItems(customerId).size());
	}

	@Test
	void removeItem_shouldLeaveTheTotalMatchingWhatIsLeft() {
		cartService.addItem(customerId, firstProductId, 2);
		cartService.addItem(customerId, secondProductId, 3);
		CartProduct remaining = cartService.getItems(customerId).stream()
				.filter(line -> line.getProduct().getId() == secondProductId)
				.findFirst().orElseThrow();

		cartService.removeItem(customerId, firstProductId);

		assertEquals(0, remaining.getLineTotal().compareTo(cartService.getTotal(customerId)));
	}

	@Test
	void clear_shouldEmptyTheCart() {
		cartService.addItem(customerId, firstProductId, 1);
		cartService.addItem(customerId, secondProductId, 1);

		cartService.clear(customerId);

		assertTrue(productIdsInCart().isEmpty());
	}

	@Test
	void checkout_shouldEmptyTheCart() {
		AddressRequest request = new AddressRequest();
		request.setFullName("Asha Rao");
		request.setPhone("9876543210");
		request.setLine1("8, Church Street");
		request.setCity("Bengaluru");
		request.setState("Karnataka");
		request.setPincode("560091");
		Address address = addressService.create(customerId, request);

		cartService.addItem(customerId, firstProductId, 1);
		cartService.checkout(customerId, address.getId());

		assertTrue(productIdsInCart().isEmpty(), "an ordered cart is emptied");
	}
}
