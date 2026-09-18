package com.jtspringproject.JtSpringProject.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A delivery address in a customer's address book.
 *
 * <p>Orders never point at this row. They copy it into a
 * {@link ShippingAddress} at checkout, so editing or deleting an address later
 * cannot rewrite where an earlier parcel was sent.
 */
@Entity
@Table(name = "address")
public class Address {

	@Id
	@Column(name = "address_id")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id", nullable = false)
	private User customer;

	@Column(name = "full_name", nullable = false, length = 100)
	private String fullName;

	@Column(nullable = false, length = 15)
	private String phone;

	/** Flat, house number, building. */
	@Column(nullable = false, length = 200)
	private String line1;

	/** Area, street, sector, village. */
	@Column(length = 200)
	private String line2;

	@Column(length = 120)
	private String landmark;

	@Column(nullable = false, length = 100)
	private String city;

	@Column(nullable = false, length = 100)
	private String state;

	@Column(nullable = false, length = 6)
	private String pincode;

	/** Where the pin was dropped, when the address came from device location. */
	@Column(precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(precision = 9, scale = 6)
	private BigDecimal longitude;

	@Column(name = "is_default", nullable = false)
	private boolean defaultAddress;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}

	public ShippingAddress toShippingAddress() {
		ShippingAddress shipping = new ShippingAddress();
		shipping.setName(fullName);
		shipping.setPhone(phone);
		shipping.setLine1(line1);
		shipping.setLine2(line2);
		shipping.setLandmark(landmark);
		shipping.setCity(city);
		shipping.setState(state);
		shipping.setPincode(pincode);
		return shipping;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public User getCustomer() {
		return customer;
	}

	public void setCustomer(User customer) {
		this.customer = customer;
	}

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

	public boolean isDefaultAddress() {
		return defaultAddress;
	}

	public void setDefaultAddress(boolean defaultAddress) {
		this.defaultAddress = defaultAddress;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
