package com.jtspringproject.JtSpringProject.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What Razorpay's checkout widget hands back to the browser after payment. */
public class RazorpayConfirmRequest {

	@NotBlank
	@Size(max = 64)
	private String razorpayOrderId;

	@NotBlank
	@Size(max = 64)
	private String razorpayPaymentId;

	/** HMAC of "order_id|payment_id"; the only reason to believe the rest. */
	@NotBlank
	@Size(max = 255)
	private String razorpaySignature;

	public String getRazorpayOrderId() {
		return razorpayOrderId;
	}

	public void setRazorpayOrderId(String razorpayOrderId) {
		this.razorpayOrderId = razorpayOrderId;
	}

	public String getRazorpayPaymentId() {
		return razorpayPaymentId;
	}

	public void setRazorpayPaymentId(String razorpayPaymentId) {
		this.razorpayPaymentId = razorpayPaymentId;
	}

	public String getRazorpaySignature() {
		return razorpaySignature;
	}

	public void setRazorpaySignature(String razorpaySignature) {
		this.razorpaySignature = razorpaySignature;
	}
}
