package com.jtspringproject.JtSpringProject.dto.response;

import java.math.BigDecimal;

import com.jtspringproject.JtSpringProject.models.Address;
import com.jtspringproject.JtSpringProject.models.ShippingAddress;

/**
 * An address as shown to its owner. Also used for the frozen copy on an order,
 * where there is no id, default flag or coordinates.
 */
public record AddressResponse(
		Integer id,
		String fullName,
		String phone,
		String line1,
		String line2,
		String landmark,
		String city,
		String state,
		String pincode,
		BigDecimal latitude,
		BigDecimal longitude,
		boolean isDefault) {

	public static AddressResponse from(Address address) {
		return new AddressResponse(address.getId(), address.getFullName(), address.getPhone(),
				address.getLine1(), address.getLine2(), address.getLandmark(), address.getCity(),
				address.getState(), address.getPincode(), address.getLatitude(), address.getLongitude(),
				address.isDefaultAddress());
	}

	/** Null for an order placed before delivery addresses existed. */
	public static AddressResponse from(ShippingAddress shipping) {
		if (shipping == null || shipping.getLine1() == null) {
			return null;
		}
		return new AddressResponse(null, shipping.getName(), shipping.getPhone(), shipping.getLine1(),
				shipping.getLine2(), shipping.getLandmark(), shipping.getCity(), shipping.getState(),
				shipping.getPincode(), null, null, false);
	}
}
