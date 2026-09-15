package com.jtspringproject.JtSpringProject.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React SPA shell for client-side routes.
 *
 * <p>React Router owns paths like {@code /cart} and {@code /product/12}, but a
 * deep link or refresh sends those to the server first, where no controller
 * matches and Spring answers 404. Forwarding them to the bundled
 * {@code index.html} lets the router take over once the app boots.
 *
 * <p>The routes are listed explicitly rather than matched with a catch-all
 * pattern: a catch-all would also swallow {@code /api/**}, {@code /admin/**}
 * and the static asset requests, breaking the API and static assets, breaking the API and the bundle itself.
 * {@code /} needs no entry here - Spring Boot's welcome page handler already
 * serves the static {@code index.html}.
 */
@Controller
public class SpaForwardController {

	@GetMapping({ "/products/**", "/product/**", "/cart", "/checkout", "/login", "/register",
			"/orders/**", "/search", "/admin", "/admin/**" })
	public String forwardToSpa() {
		return "forward:/index.html";
	}
}
