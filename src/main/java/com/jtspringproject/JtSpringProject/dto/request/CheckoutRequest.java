package com.jtspringproject.JtSpringProject.dto.request;

import jakarta.validation.constraints.NotNull;

/** Turns the cart into an order delivered to one of the caller's saved addresses. */
public class CheckoutRequest {

	@NotNull(message = "Choose a delivery address")
	private Integer addressId;

	public Integer getAddressId() {
		return addressId;
	}

	public void setAddressId(Integer addressId) {
		this.addressId = addressId;
	}
}
