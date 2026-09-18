package com.jtspringproject.JtSpringProject.controller.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jtspringproject.JtSpringProject.dto.ApiResponse;
import com.jtspringproject.JtSpringProject.dto.request.AddressRequest;
import com.jtspringproject.JtSpringProject.dto.response.AddressResponse;
import com.jtspringproject.JtSpringProject.security.AppUserDetails;
import com.jtspringproject.JtSpringProject.services.AddressService;

import jakarta.validation.Valid;

/** The caller's own address book. No path names another customer. */
@RestController
@RequestMapping("/api/addresses")
public class AddressApiController {

	private final AddressService addressService;

	public AddressApiController(AddressService addressService) {
		this.addressService = addressService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<AddressResponse>>> list(@AuthenticationPrincipal AppUserDetails principal) {
		List<AddressResponse> addresses = addressService.list(principal.getId()).stream()
				.map(AddressResponse::from)
				.toList();
		return ResponseEntity.ok(ApiResponse.success(addresses));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<AddressResponse>> create(@AuthenticationPrincipal AppUserDetails principal,
			@Valid @RequestBody AddressRequest request) {
		AddressResponse created = AddressResponse.from(addressService.create(principal.getId(), request));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Address saved", created));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<AddressResponse>> update(@AuthenticationPrincipal AppUserDetails principal,
			@PathVariable int id, @Valid @RequestBody AddressRequest request) {
		AddressResponse updated = AddressResponse.from(addressService.update(principal.getId(), id, request));
		return ResponseEntity.ok(ApiResponse.success("Address updated", updated));
	}

	@PostMapping("/{id}/default")
	public ResponseEntity<ApiResponse<AddressResponse>> makeDefault(@AuthenticationPrincipal AppUserDetails principal,
			@PathVariable int id) {
		AddressResponse updated = AddressResponse.from(addressService.makeDefault(principal.getId(), id));
		return ResponseEntity.ok(ApiResponse.success("Default address updated", updated));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal AppUserDetails principal,
			@PathVariable int id) {
		addressService.delete(principal.getId(), id);
		return ResponseEntity.ok(ApiResponse.success("Address removed", null));
	}
}
