import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useCart } from '../context/CartContext';
import { formatPrice } from '../utils/format';

const METHODS = [
  { value: 'COD', label: 'Cash on delivery' },
  { value: 'CARD', label: 'Credit or debit card' },
  { value: 'UPI', label: 'UPI' },
];

export default function CheckoutPage() {
  const { cart, checkout } = useCart();
  const navigate = useNavigate();
  const [method, setMethod] = useState('COD');
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState(null);
  const [placed, setPlaced] = useState(null);

  async function placeOrder() {
    setPlacing(true);
    setError(null);
    try {
      // Checkout converts the cart into an order; payment is a separate call
      // against that order id.
      const order = await checkout();
      const payment = await api.post('/api/payments', { orderId: order.id, method });
      setPlaced({ order, payment });
    } catch (err) {
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
          Payment {placed.payment.method} — {placed.payment.status} (txn{' '}
          {placed.payment.transactionId})
        </p>
        <div style={{ display: 'flex', gap: 10, marginTop: 14 }}>
          <Link to="/orders" className="btn">
            View your orders
          </Link>
          <Link to="/" className="btn-plain">
            Keep shopping
          </Link>
        </div>
      </div>
    );
  }

  if (!cart || cart.items.length === 0) {
    return (
      <div className="panel">
        <h1 className="section-title">Nothing to check out</h1>
        <button type="button" className="btn" onClick={() => navigate('/')}>
          Browse products
        </button>
      </div>
    );
  }

  return (
    <div className="cart-layout">
      <section className="panel">
        <h1 className="section-title">Checkout</h1>
        {error && <div className="error">{error.message}</div>}

        <h2 style={{ fontSize: 16 }}>Payment method</h2>
        {METHODS.map((option) => (
          <label key={option.value} style={{ display: 'block', padding: '7px 0' }}>
            <input
              type="radio"
              name="method"
              value={option.value}
              checked={method === option.value}
              onChange={(event) => setMethod(event.target.value)}
            />{' '}
            {option.label}
          </label>
        ))}

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
          {placing ? 'Placing order…' : 'Place your order'}
        </button>
      </aside>
    </div>
  );
}
