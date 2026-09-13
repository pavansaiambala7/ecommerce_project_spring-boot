package com.jtspringproject.JtSpringProject.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jtspringproject.JtSpringProject.dao.cartDao;
import com.jtspringproject.JtSpringProject.dao.cartProductDao;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.models.Cart;
import com.jtspringproject.JtSpringProject.models.CartProduct;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.models.Product;
import com.jtspringproject.JtSpringProject.models.User;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

	private static final int USER_ID = 5;

	@Mock
	private cartDao cartDao;

	@Mock
	private cartProductDao cartProductDao;

	@Mock
	private productService productService;

	@Mock
	private userService userService;

	@Mock
	private OrderService orderService;

	@InjectMocks
	private cartService cartService;

	private User user;
	private Cart cart;
	private Product product;

	@BeforeEach
	void setUp() {
		user = new User();
		user.setId(USER_ID);
		user.setUsername("shopper");

		cart = new Cart(user);
		cart.setId(11);

		product = new Product();
		product.setId(3);
		product.setName("Apple");
		product.setPrice(new BigDecimal("2.50"));
		product.setQuantity(10);
	}

	@Test
	void getOrCreateCart_shouldCreateWhenAbsent() {
		when(cartDao.findByCustomerId(USER_ID)).thenReturn(Optional.empty());
		when(userService.requireUserById(USER_ID)).thenReturn(user);
		when(cartDao.save(any(Cart.class))).thenAnswer(i -> i.getArgument(0));

		Cart result = cartService.getOrCreateCart(USER_ID);

		assertEquals(USER_ID, result.getCustomer().getId());
	}

	@Test
	void addItem_shouldCreateNewLine() {
		when(cartDao.findByCustomerId(USER_ID)).thenReturn(Optional.of(cart));
		when(productService.requireProduct(3)).thenReturn(product);
		when(cartProductDao.findByCartIdAndProductId(11, 3)).thenReturn(Optional.empty());
		when(cartProductDao.save(any(CartProduct.class))).thenAnswer(i -> i.getArgument(0));

		CartProduct result = cartService.addItem(USER_ID, 3, 2);

		assertEquals(2, result.getQuantity());
	}

	/** Adding the same product twice accumulates rather than duplicating a row. */
	@Test
	void addItem_shouldAccumulateOntoExistingLine() {
		CartProduct existing = new CartProduct(cart, product, 2);
		when(cartDao.findByCustomerId(USER_ID)).thenReturn(Optional.of(cart));
		when(productService.requireProduct(3)).thenReturn(product);
		when(cartProductDao.findByCartIdAndProductId(11, 3)).thenReturn(Optional.of(existing));
		when(cartProductDao.save(any(CartProduct.class))).thenAnswer(i -> i.getArgument(0));

		CartProduct result = cartService.addItem(USER_ID, 3, 3);

		assertEquals(5, result.getQuantity());
	}

	@Test
	void addItem_shouldRejectQuantityBeyondStock() {
		when(cartDao.findByCustomerId(USER_ID)).thenReturn(Optional.of(cart));
		when(productService.requireProduct(3)).thenReturn(product);
		when(cartProductDao.findByCartIdAndProductId(11, 3)).thenReturn(Optional.empty());

		assertThrows(BusinessRuleException.class, () -> cartService.addItem(USER_ID, 3, 99));

		verify(cartProductDao, never()).save(any(CartProduct.class));
	}

	@Test
	void addItem_shouldRejectNonPositiveQuantity() {
		assertThrows(BusinessRuleException.class, () -> cartService.addItem(USER_ID, 3, 0));
	}

	@Test
	void updateQuantity_shouldRejectZero() {
		assertThrows(BusinessRuleException.class, () -> cartService.updateQuantity(USER_ID, 3, 0));
	}

	@Test
	void updateQuantity_shouldThrowWhenProductNotInCart() {
		when(cartDao.findByCustomerId(USER_ID)).thenReturn(Optional.of(cart));
		when(cartProductDao.findByCartIdAndProductId(11, 3)).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class, () -> cartService.updateQuantity(USER_ID, 3, 2));
	}

	@Test
	void getTotal_shouldSumLineTotalsExactly() {
		product.setPrice(new BigDecimal("0.10"));
		cart.getItems().add(new CartProduct(cart, product, 3));
		when(cartDao.findByCustomerId(USER_ID)).thenReturn(Optional.of(cart));

		assertEquals(0, new BigDecimal("0.30").compareTo(cartService.getTotal(USER_ID)));
	}

	@Test
	void checkout_shouldRejectAnEmptyCart() {
		when(cartDao.findByCustomerId(USER_ID)).thenReturn(Optional.of(cart));

		assertThrows(BusinessRuleException.class, () -> cartService.checkout(USER_ID));

		verify(orderService, never()).createOrder(anyInt(), anyList());
	}

	@Test
	void checkout_shouldCreateAnOrderAndEmptyTheCart() {
		cart.getItems().add(new CartProduct(cart, product, 2));
		Order order = new Order();
		order.setId(77);

		when(cartDao.findByCustomerId(USER_ID)).thenReturn(Optional.of(cart));
		when(orderService.createOrder(anyInt(), anyList())).thenReturn(order);

		Order result = cartService.checkout(USER_ID);

		assertEquals(77, result.getId());
		verify(cartProductDao).deleteByCartId(11);
	}

	@Test
	void getItems_shouldReturnEmptyWhenNoCartExists() {
		when(cartDao.findByCustomerId(USER_ID)).thenReturn(Optional.empty());

		assertEquals(List.of(), cartService.getItems(USER_ID));
	}
}
