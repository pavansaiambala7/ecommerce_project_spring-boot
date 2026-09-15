package com.jtspringproject.JtSpringProject.payment;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jtspringproject.JtSpringProject.dao.OrderDao;
import com.jtspringproject.JtSpringProject.dao.PaymentDao;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.models.Payment;

/**
 * Online payment through Razorpay.
 *
 * <p>Split into two steps because the customer pays in between them:
 * {@link #startPayment} creates a gateway order, the browser completes payment
 * in Razorpay's widget, then {@link #confirmPayment} verifies what the browser
 * reports.
 *
 * <p>The design rule throughout is that the browser is an untrusted witness. It
 * reports a payment id and a signature; only the signature, computed with a
 * secret the browser never sees, decides whether that report is believed. The
 * amount is likewise taken from our own order row, never from the request, so
 * a tampered client cannot pay ten rupees for a ten-thousand rupee basket.
 */
@Service
public class RazorpayPaymentService {

	private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentService.class);

	private final PaymentDao paymentDao;
	private final OrderDao orderDao;
	private final RazorpayGateway gateway;

	public RazorpayPaymentService(PaymentDao paymentDao, OrderDao orderDao, RazorpayGateway gateway) {
		this.paymentDao = paymentDao;
		this.orderDao = orderDao;
		this.gateway = gateway;
	}

	/** What the browser needs to open Razorpay's checkout widget. */
	public record CheckoutSession(String razorpayOrderId, String keyId, long amountMinor,
			String currency, int orderId) {
	}

	@Transactional
	public CheckoutSession startPayment(int orderId, int callerId, boolean callerIsAdmin) {
		Order order = orderDao.findWithItemsById(orderId)
				.orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
		assertVisible(order, callerId, callerIsAdmin);

		if (order.getStatus() != Order.OrderStatus.CREATED) {
			throw new BusinessRuleException("Order is not in a payable state: " + order.getStatus() + ".");
		}

		Payment existing = paymentDao.findByOrderId(orderId).orElse(null);
		if (existing != null && existing.getStatus() == Payment.PaymentStatus.SUCCESS) {
			throw new BusinessRuleException("Order " + orderId + " has already been paid.");
		}

		// Reuse a pending attempt rather than creating a second gateway order.
		// A customer who closes the widget and comes back should resume, not
		// leave a trail of abandoned orders for reconciliation to puzzle over.
		if (existing != null && existing.getRazorpayOrderId() != null) {
			return new CheckoutSession(existing.getRazorpayOrderId(), gateway.getPublicKeyId(),
					gateway.toMinorUnits(existing.getAmount()), "INR", orderId);
		}

		// The amount comes from the order row. Accepting it from the request
		// would let anyone choose their own price.
		BigDecimal amount = order.getTotalAmount();
		String razorpayOrderId = gateway.createOrder(orderId, amount);

		Payment payment = existing != null ? existing : new Payment();
		payment.setOrder(order);
		payment.setAmount(amount);
		payment.setMethod(Payment.PaymentMethod.CARD);
		payment.setStatus(Payment.PaymentStatus.PENDING);
		payment.setRazorpayOrderId(razorpayOrderId);
		payment.setTransactionId(razorpayOrderId);
		paymentDao.save(payment);

		log.info("Started Razorpay payment for order {} ({})", orderId, razorpayOrderId);
		return new CheckoutSession(razorpayOrderId, gateway.getPublicKeyId(),
				gateway.toMinorUnits(amount), "INR", orderId);
	}

	/**
	 * Confirms a payment the browser reports as complete.
	 *
	 * <p>Success here is decided by the signature, not by the browser saying so.
	 * A failed check is recorded rather than ignored: repeated invalid
	 * signatures on one order is what an attempt to forge a payment looks like.
	 */
	@Transactional
	public Payment confirmPayment(String razorpayOrderId, String razorpayPaymentId, String signature,
			int callerId, boolean callerIsAdmin) {

		Payment payment = paymentDao.findByRazorpayOrderId(razorpayOrderId)
				.orElseThrow(() -> new ResourceNotFoundException("Unknown payment: " + razorpayOrderId));
		assertVisible(payment.getOrder(), callerId, callerIsAdmin);

		// The webhook may have confirmed this already. Returning the settled
		// payment is correct; re-running would double-mark the order.
		if (payment.getStatus() == Payment.PaymentStatus.SUCCESS) {
			return payment;
		}

		if (!gateway.verifyPaymentSignature(razorpayOrderId, razorpayPaymentId, signature)) {
			payment.setStatus(Payment.PaymentStatus.FAILED);
			payment.setFailureReason("Signature verification failed");
			paymentDao.save(payment);
			log.warn("Signature verification FAILED for {} (order {})", razorpayOrderId,
					payment.getOrder().getId());
			throw new BusinessRuleException("Payment could not be verified.");
		}

		return settle(payment, razorpayPaymentId, signature);
	}

	/**
	 * Marks a payment successful. Shared by the browser callback and the
	 * webhook, which race each other - whichever arrives first wins, and the
	 * other finds the payment already settled.
	 */
	@Transactional
	public Payment settle(Payment payment, String razorpayPaymentId, String signature) {
		if (payment.getStatus() == Payment.PaymentStatus.SUCCESS) {
			return payment;
		}
		payment.setRazorpayPaymentId(razorpayPaymentId);
		payment.setRazorpaySignature(signature);
		payment.setTransactionId(razorpayPaymentId);
		payment.setStatus(Payment.PaymentStatus.SUCCESS);
		payment.setFailureReason(null);

		Order order = payment.getOrder();
		order.setStatus(Order.OrderStatus.PAID);
		orderDao.save(order);

		log.info("Payment {} confirmed for order {}", razorpayPaymentId, order.getId());
		return paymentDao.save(payment);
	}

	@Transactional
	public void markFailed(String razorpayOrderId, String reason) {
		paymentDao.findByRazorpayOrderId(razorpayOrderId).ifPresent(payment -> {
			if (payment.getStatus() != Payment.PaymentStatus.SUCCESS) {
				payment.setStatus(Payment.PaymentStatus.FAILED);
				payment.setFailureReason(reason);
				paymentDao.save(payment);
			}
		});
	}

	public boolean isConfigured() {
		return gateway.isConfigured();
	}

	private void assertVisible(Order order, int callerId, boolean callerIsAdmin) {
		if (!callerIsAdmin && order.getCustomer().getId() != callerId) {
			throw new AccessDeniedException("You do not have access to this order.");
		}
	}
}
