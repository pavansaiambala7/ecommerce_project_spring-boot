package com.jtspringproject.JtSpringProject.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A delivery address as entered at checkout.
 *
 * <p>The formats mirror the CHECK constraints on the address table, so a bad
 * value is reported against the field that caused it instead of surfacing as a
 * generic data integrity error.
 */
public class AddressRequest {

	@NotBlank(message = "Full name is required")
	@Size(max = 100)
	private String fullName;

	@NotBlank(message = "Mobile number is required")
	@Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a 10-digit mobile number")
	private String phone;

	@NotBlank(message = "Flat, house no. or building is required")
	@Size(max = 200)
	private String line1;

	@Size(max = 200)
	private String line2;

	@Size(max = 120)
	private String landmark;

	@NotBlank(message = "Town or city is required")
	@Size(max = 100)
	private String city;

	@NotBlank(message = "State is required")
	@Size(max = 100)
	private String state;

	@NotBlank(message = "PIN code is required")
	@Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a 6-digit PIN code")
	private String pincode;

	@DecimalMin("-90.0")
	@DecimalMax("90.0")
	private BigDecimal latitude;

	@DecimalMin("-180.0")
	@DecimalMax("180.0")
	private BigDecimal longitude;

	private boolean makeDefault;

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getLine1() {
		return line1;
	}

	public void setLine1(String line1) {
		this.line1 = line1;
	}

	public String getLine2() {
		return line2;
	}

	public void setLine2(String line2) {
		this.line2 = line2;
	}

	public String getLandmark() {
		return landmark;
	}

	public void setLandmark(String landmark) {
		this.landmark = landmark;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getPincode() {
		return pincode;
	}

	public void setPincode(String pincode) {
		this.pincode = pincode;
	}

	public BigDecimal getLatitude() {
		return latitude;
	}

	public void setLatitude(BigDecimal latitude) {
		this.latitude = latitude;
	}

	public BigDecimal getLongitude() {
		return longitude;
	}

	public void setLongitude(BigDecimal longitude) {
		this.longitude = longitude;
	}

	public boolean isMakeDefault() {
		return makeDefault;
	}

	public void setMakeDefault(boolean makeDefault) {
		this.makeDefault = makeDefault;
	}
}
