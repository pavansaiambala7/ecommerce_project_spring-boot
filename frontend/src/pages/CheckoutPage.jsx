import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import { formatPrice } from '../utils/format';
import { loadRazorpay } from '../utils/razorpay';

/** A fresh key per attempt, so a retry after a failure is genuinely a new attempt. */
function newIdempotencyKey() {
  return `checkout-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export default function CheckoutPage() {
  const { cart, checkout } = useCart();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [method, setMethod] = useState('COD');
  const [online, setOnline] = useState({ enabled: false, keyId: '' });
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState(null);
  const [placed, setPlaced] = useState(null);

  useEffect(() => {
    api.get('/api/payments/razorpay/config', { auth: false })
      .then(setOnline)
      .catch(() => setOnline({ enabled: false, keyId: '' }));
  }, []);

  async function payWithRazorpay(order) {
    const ready = await loadRazorpay();
    if (!ready) {
      throw new Error('Could not reach the payment provider. Try cash on delivery.');
    }

    const session = await api.post(`/api/payments/razorpay/orders/${order.id}`);

    // Razorpay's widget is callback-based; wrapping it in a promise keeps the
    // caller's flow linear and makes dismissal a normal rejection rather than
    // a state the page has to detect separately.
    return new Promise((resolve, reject) => {
      const checkoutWidget = new window.Razorpay({
        key: session.keyId,
        amount: session.amountMinor,
        currency: session.currency,
        order_id: session.razorpayOrderId,
        name: 'ShopKart',
        description: `Order #${order.id}`,
        prefill: { name: user?.username ?? '' },
        theme: { color: '#232f3e' },
        handler: async (response) => {
          try {
            // Nothing here is trusted by the server: it re-derives the
            // signature with a secret this page never sees. The page only
            // relays what the widget returned.
            const payment = await api.post('/api/payments/razorpay/confirm', {
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
            });
            resolve(payment);
          } catch (err) {
            reject(err);
          }
        },
        modal: {
          ondismiss: () => reject(new Error('Payment cancelled.')),
        },
      });

      checkoutWidget.on('payment.failed', (event) => {
        reject(new Error(event?.error?.description ?? 'Payment failed.'));
      });

      checkoutWidget.open();
    });
  }

  async function placeOrder() {
    setPlacing(true);
    setError(null);
    try {
      // One key for this attempt. A double-click or a retried request reaches
      // the server with the same key and produces the same single order.
      const order = await checkout(newIdempotencyKey());

      const payment = method === 'COD'
        ? await api.post('/api/payments', { orderId: order.id, method }, {
            headers: { 'Idempotency-Key': newIdempotencyKey() },
          })
        : await payWithRazorpay(order);

      setPlaced({ order, payment });
    } catch (err) {
      // The order exists even when payment fails; the customer can pay it from
      // their orders page rather than losing the basket.
      setError(err);
    } finally {
      setPlacing(false);
    }
  }

  if (placed) {
    return (
      <div className="panel">
        <h1 className="section-title">Order placed</h1>
        <p>
          Order <strong>#{placed.order.id}</strong> for{' '}
          <strong>{formatPrice(placed.order.totalAmount)}</strong> is{' '}
          <span className="badge">{placed.order.status}</span>
        </p>
        <p className="meta-row">
          Payment {placed.payment.method} — {placed.payment.status}
          {placed.payment.transactionId && ` (${placed.payment.transactionId})`}
        </p>
        <div style={{ display: 'flex', gap: 10, marginTop: 14 }}>
          <Link to="/orders" className="btn">View your orders</Link>
          <Link to="/" className="btn-plain">Keep shopping</Link>
        </div>
      </div>
    );
  }

  if (!cart || cart.items.length === 0) {
    return (
      <div className="panel">
        <h1 className="section-title">Nothing to check out</h1>
        <button type="button" className="btn" onClick={() => navigate('/')}>Browse products</button>
      </div>
    );
  }

  return (
    <div className="cart-layout">
      <section className="panel">
        <h1 className="section-title">Checkout</h1>
        {error && <div className="error">{error.message}</div>}

        <h2 style={{ fontSize: 16 }}>Payment method</h2>

        <label style={{ display: 'block', padding: '7px 0' }}>
          <input type="radio" name="method" value="COD" checked={method === 'COD'}
            onChange={(e) => setMethod(e.target.value)} />{' '}
          Cash on delivery
        </label>

        {online.enabled ? (
          <label style={{ display: 'block', padding: '7px 0' }}>
            <input type="radio" name="method" value="ONLINE" checked={method === 'ONLINE'}
              onChange={(e) => setMethod(e.target.value)} />{' '}
            Card, UPI or netbanking <span style={{ color: 'var(--muted)' }}>— secured by Razorpay</span>
          </label>
        ) : (
          <p className="meta-row">Online payment is currently unavailable.</p>
        )}

        <h2 style={{ fontSize: 16, marginTop: 22 }}>Items</h2>
        {cart.items.map((item) => (
          <div key={item.productId} className="meta-row">
            {item.quantity} × {item.productName} — {formatPrice(item.lineTotal)}
          </div>
        ))}
      </section>

      <aside className="summary">
        <div className="summary-total">
          <span>Order total</span>
          <span>{formatPrice(cart.total)}</span>
        </div>
        <button type="button" className="btn btn-block" disabled={placing} onClick={placeOrder}>
          {placing ? 'Placing order…' : method === 'COD' ? 'Place your order' : 'Pay now'}
        </button>
      </aside>
    </div>
  );
}
