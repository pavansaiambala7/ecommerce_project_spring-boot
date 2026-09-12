package com.jtspringproject.JtSpringProject.controller;

import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;
import com.jtspringproject.JtSpringProject.models.Product;
import com.jtspringproject.JtSpringProject.models.User;
import com.jtspringproject.JtSpringProject.services.productService;
import com.jtspringproject.JtSpringProject.services.userService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class UserController {

	private final userService userService;
	private final productService productService;

	public UserController(userService userService, productService productService) {
		this.userService = userService;
		this.productService = productService;
	}

	@GetMapping("/register")
	public String registerUser() {
		return "register";
	}

	@GetMapping("/login")
	public ModelAndView userLogin(@RequestParam(required = false) String error) {
		ModelAndView mv = new ModelAndView("userLogin");
		if ("true".equals(error)) {
			mv.addObject("msg", "Please enter a correct username and password");
		}
		return mv;
	}

	@GetMapping("/")
	public ModelAndView indexPage() {
		ModelAndView mView = new ModelAndView("index");
		mView.addObject("username", currentUsername());
		List<Product> products = this.productService.getProducts();

		if (products.isEmpty()) {
			mView.addObject("msg", "No products are available");
		} else {
			mView.addObject("products", products);
		}
		return mView;
	}

	@GetMapping("/user/products")
	public ModelAndView getProducts() {

		ModelAndView mView = new ModelAndView("uproduct");

		List<Product> products = this.productService.getProducts();

		if (products.isEmpty()) {
			mView.addObject("msg", "No products are available");
		} else {
			mView.addObject("products", products);
		}

		return mView;
	}

	@PostMapping("newuserregister")
	public ModelAndView registerNewUser(@RequestParam("username") String username,
			@RequestParam("email") String email,
			@RequestParam("password") String password,
			@RequestParam(value = "address", required = false) String address) {
		try {
			this.userService.register(username, email, password, address);
		} catch (BusinessRuleException e) {
			ModelAndView mView = new ModelAndView("register");
			mView.addObject("msg", e.getMessage());
			return mView;
		}
		ModelAndView mView = new ModelAndView("userLogin");
		mView.addObject("msg", "Registration successful. Please sign in.");
		return mView;
	}

	@GetMapping("/profileDisplay")
	public String profileDisplay(Model model) {
		User user = this.userService.requireUserByUsername(currentUsername());
		model.addAttribute("username", user.getUsername());
		model.addAttribute("email", user.getEmail());
		model.addAttribute("password", "");
		model.addAttribute("address", user.getAddress());
		return "updateProfile";
	}

	/**
	 * Updates the signed-in user profile.
	 *
	 * <p>The account being edited is resolved from the security context. It used to
	 * be read from a hidden {@code userid} form field, so any authenticated user
	 * could post {@code userid=1} and take over the administrator account.
	 */
	@PostMapping("/updateuser")
	public String updateUserProfile(@RequestParam("username") String username,
			@RequestParam("email") String email,
			@RequestParam("password") String password,
			@RequestParam("address") String address,
			HttpServletRequest request) {
		User current = this.userService.requireUserByUsername(currentUsername());
		boolean identityChanged = !current.getUsername().equals(username)
				|| (password != null && !password.isBlank());

		this.userService.updateUserProfile(current.getId(), username, email, password, address);

		if (identityChanged) {
			// Identity or credentials changed: end the session so the user
			// re-authenticates instead of continuing under a stale principal.
			SecurityContextHolder.clearContext();
			request.getSession().invalidate();
			return "redirect:/login";
		}
		return "redirect:/";
	}

	private String currentUsername() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}
}
