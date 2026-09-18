package com.jtspringproject.JtSpringProject.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The address an order was shipped to, frozen at checkout.
 *
 * <p>A copy rather than a reference to {@link Address}: the address book is
 * editable, and an order's destination must not change after it is placed.
 */
@Embeddable
public class ShippingAddress {

	@Column(name = "ship_name", length = 100)
	private String name;

	@Column(name = "ship_phone", length = 15)
	private String phone;

	@Column(name = "ship_line1", length = 200)
	private String line1;

	@Column(name = "ship_line2", length = 200)
	private String line2;

	@Column(name = "ship_landmark", length = 120)
	private String landmark;

	@Column(name = "ship_city", length = 100)
	private String city;

	@Column(name = "ship_state", length = 100)
	private String state;

	@Column(name = "ship_pincode", length = 6)
	private String pincode;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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
}
