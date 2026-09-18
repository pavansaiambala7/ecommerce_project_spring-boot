package com.jtspringproject.JtSpringProject.controller.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.catalogue.StorefrontService;
import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.response.StorefrontHomeResponse;

/** Public content for the landing page. */
@RestController
@RequestMapping("/api/storefront")
public class StorefrontApiController {

	private final StorefrontService storefrontService;

	public StorefrontApiController(StorefrontService storefrontService) {
		this.storefrontService = storefrontService;
	}

	@GetMapping("/home")
	public ResponseEntity<ApiResponse<StorefrontHomeResponse>> home() {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(1)).cachePublic())
				.body(ApiResponse.success(storefrontService.home()));
	}
}
