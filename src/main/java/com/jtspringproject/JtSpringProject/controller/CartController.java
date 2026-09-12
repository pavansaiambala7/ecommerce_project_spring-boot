package com.jtspringproject.JtSpringProject.controller;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.jtspringproject.JtSpringProject.models.Cart;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.security.AppUserDetails;
import com.jtspringproject.JtSpringProject.services.cartService;

/**
 * Server-rendered cart pages.
 *
 * <p>Replaces the deleted {@code cartproduct.jsp}, which queried a hardcoded
 * MySQL database directly from the view with root credentials.
 */
@Controller
@RequestMapping("/cart")
public class CartController {

	private final cartService cartService;

	public CartController(cartService cartService) {
		this.cartService = cartService;
	}

	@GetMapping
	public ModelAndView viewCart() {
		Cart cart = cartService.getOrCreateCart(currentUserId());
		ModelAndView mv = new ModelAndView("cart");
		mv.addObject("items", cart.getItems());
		mv.addObject("total", cartService.getTotal(currentUserId()));
		return mv;
	}

	@PostMapping("/add")
	public String addToCart(@RequestParam("productId") int productId,
			@RequestParam(value = "quantity", defaultValue = "1") int quantity) {
		cartService.addItem(currentUserId(), productId, quantity);
		return "redirect:/cart";
	}

	@PostMapping("/update")
	public String updateQuantity(@RequestParam("productId") int productId,
			@RequestParam("quantity") int quantity) {
		cartService.updateQuantity(currentUserId(), productId, quantity);
		return "redirect:/cart";
	}

	@PostMapping("/remove")
	public String removeFromCart(@RequestParam("productId") int productId) {
		cartService.removeItem(currentUserId(), productId);
		return "redirect:/cart";
	}

	@PostMapping("/checkout")
	public ModelAndView checkout() {
		Order order = cartService.checkout(currentUserId());
		ModelAndView mv = new ModelAndView("orderConfirmation");
		mv.addObject("order", order);
		return mv;
	}

	private int currentUserId() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		return ((AppUserDetails) principal).getId();
	}
}
