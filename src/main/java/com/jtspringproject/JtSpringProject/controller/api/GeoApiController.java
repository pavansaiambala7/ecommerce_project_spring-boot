package com.jtspringproject.JtSpringProject.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.geo.GeoService;

/**
 * Address autofill for the checkout form. Authenticated: only a signed-in
 * shopper adding an address has a reason to call it, which keeps the shop from
 * becoming a free geocoding proxy.
 */
@RestController
@RequestMapping("/api/geo")
public class GeoApiController {

	private final GeoService geoService;

	public GeoApiController(GeoService geoService) {
		this.geoService = geoService;
	}

	@GetMapping("/reverse")
	public ResponseEntity<ApiResponse<GeoService.GeoAddress>> reverse(@RequestParam double lat,
			@RequestParam double lng) {
		return ResponseEntity.ok(ApiResponse.success(geoService.reverse(lat, lng)));
	}

	@GetMapping("/pincode/{pincode}")
	public ResponseEntity<ApiResponse<GeoService.PincodeInfo>> pincode(@PathVariable String pincode) {
		return ResponseEntity.ok(ApiResponse.success(geoService.pincode(pincode)));
	}
}
