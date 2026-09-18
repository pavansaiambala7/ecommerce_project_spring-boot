import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import AddressForm from '../components/AddressForm';
import ProductImage from '../components/ProductImage';
import { useAddresses } from '../context/AddressContext';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import { formatPrice } from '../utils/format';
import { loadRazorpay } from '../utils/razorpay';

/** A fresh key per attempt, so a retry after a failure is genuinely a new attempt. */
function newIdempotencyKey() {
  return `checkout-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function oneLine(address) {
  return [address.line1, address.line2, address.city, address.state, address.pincode].filter(Boolean).join(', ');
}

export default function CheckoutPage() {
  const { cart, checkout } = useCart();
  const { user } = useAuth();
  const { addresses, loaded, defaultAddress } = useAddresses();
  const navigate = useNavigate();

  const [addressId, setAddressId] = useState(null);
  const [choosingAddress, setChoosingAddress] = useState(true);
  const [addingAddress, setAddingAddress] = useState(false);
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

  // Preselect the default address once the address book has loaded, and go
  // straight to payment when there is one - as a returning Amazon shopper sees.
  useEffect(() => {
    if (loaded && addressId === null && defaultAddress) {
      setAddressId(defaultAddress.id);
      setChoosingAddress(false);
    }
  }, [loaded, defaultAddress, addressId]);

  const selected = addresses.find((a) => a.id === addressId) ?? null;

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
        prefill: { name: selected?.fullName ?? user?.username ?? '', contact: selected?.phone ?? '' },
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
    if (!selected) {
      setError(new Error('Choose a delivery address first.'));
      setChoosingAddress(true);
      return;
    }
    setPlacing(true);
    setError(null);
    try {
      // One key for this attempt. A double-click or a retried request reaches
      // the server with the same key and produces the same single order.
      const order = await checkout(newIdempotencyKey(), selected.id);

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
    const ship = placed.order.shippingAddress;
    return (
      <div className="panel narrow-panel">
        <h1 className="section-title order-success">✓ Order placed, thank you!</h1>
        <p>
          Order <strong>#{placed.order.id}</strong> for <strong>{formatPrice(placed.order.totalAmount)}</strong> is{' '}
          <span className="badge">{placed.order.status}</span>
        </p>
        {ship && (
          <p className="meta-row">
            Delivering to <strong>{ship.fullName}</strong>, {oneLine(ship)}
          </p>
        )}
        <p className="meta-row">
          Payment {placed.payment.method} — {placed.payment.status}
          {placed.payment.transactionId && ` (${placed.payment.transactionId})`}
        </p>
        <div className="form-actions">
          <Link to="/orders" className="btn">View your orders</Link>
          <Link to="/" className="btn-plain">Continue shopping</Link>
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
    <div className="checkout">
      <h1 className="checkout-title">Checkout</h1>
      <div className="cart-layout">
        <div className="checkout-steps">
          {error && <div className="error">{error.message}</div>}

          <section className="checkout-step">
            <div className="step-head">
              <span className="step-number">1</span>
              <h2>Delivery address</h2>
              {!choosingAddress && selected && (
                <button type="button" className="link-btn step-change" onClick={() => setChoosingAddress(true)}>
                  Change
                </button>
              )}
            </div>

            {!loaded && <div className="meta-row">Loading your addresses…</div>}

            {loaded && !choosingAddress && selected && (
              <div className="step-summary">
                <strong>{selected.fullName}</strong>, {oneLine(selected)}
              </div>
            )}

            {loaded && (choosingAddress || !selected) && (
              <div className="step-body">
                {addresses.length > 0 && !addingAddress && (
                  <>
                    <div className="address-choices" role="radiogroup" aria-label="Delivery address">
                      {addresses.map((address) => (
                        <label key={address.id} className="address-choice" data-selected={address.id === addressId}>
                          <input
                            type="radio"
                            name="address"
                            checked={address.id === addressId}
                            onChange={() => setAddressId(address.id)}
                          />
                          <span>
                            <strong>{address.fullName}</strong> {oneLine(address)}
                            {address.isDefault && <em className="address-default-tag"> Default</em>}
                          </span>
                        </label>
                      ))}
                    </div>
                    <button type="button" className="link-btn add-address-link" onClick={() => setAddingAddress(true)}>
                      + Add a new address
                    </button>
                    <div className="form-actions">
                      <button
                        type="button"
                        className="btn"
                        disabled={!addressId}
                        onClick={() => setChoosingAddress(false)}
                      >
                        Use this address
                      </button>
                    </div>
                  </>
                )}

                {(addresses.length === 0 || addingAddress) && (
                  <AddressForm
                    submitLabel="Use this address"
                    onSaved={(saved) => {
                      setAddressId(saved.id);
                      setAddingAddress(false);
                      setChoosingAddress(false);
                    }}
                    onCancel={addresses.length > 0 ? () => setAddingAddress(false) : undefined}
                  />
                )}
              </div>
            )}
          </section>

          <section className="checkout-step" data-disabled={!selected}>
            <div className="step-head">
              <span className="step-number">2</span>
              <h2>Payment method</h2>
            </div>
            {selected && !choosingAddress && (
              <div className="step-body">
                <label className="pay-option" data-selected={method === 'COD'}>
                  <input type="radio" name="method" value="COD" checked={method === 'COD'}
                    onChange={(e) => setMethod(e.target.value)} />
                  <span>
                    <strong>Cash on Delivery</strong>
                    <small>Pay when your order arrives</small>
                  </span>
                </label>
                {online.enabled ? (
                  <label className="pay-option" data-selected={method === 'ONLINE'}>
                    <input type="radio" name="method" value="ONLINE" checked={method === 'ONLINE'}
                      onChange={(e) => setMethod(e.target.value)} />
                    <span>
                      <strong>Credit or debit card, UPI, net banking</strong>
                      <small>Secured by Razorpay</small>
                    </span>
                  </label>
                ) : (
                  <p className="meta-row">Online payment is currently unavailable.</p>
                )}
              </div>
            )}
          </section>

          <section className="checkout-step" data-disabled={!selected}>
            <div className="step-head">
              <span className="step-number">3</span>
              <h2>Review items</h2>
            </div>
            <div className="step-body">
              {cart.items.map((item) => (
                <div key={item.productId} className="review-row">
                  <ProductImage src={item.image} alt={item.productName} />
                  <div>
                    <Link to={`/product/${item.productId}`} className="product-name">{item.productName}</Link>
                    <div className="meta-row">Qty: {item.quantity}</div>
                  </div>
                  <strong>{formatPrice(item.lineTotal)}</strong>
                </div>
              ))}
            </div>
          </section>
        </div>

        <aside className="summary">
          <button
            type="button"
            className="btn btn-block"
            disabled={placing || !selected || choosingAddress}
            onClick={placeOrder}
          >
            {placing ? 'Placing order…' : method === 'COD' ? 'Place your order' : 'Pay now'}
          </button>
          {(!selected || choosingAddress) && (
            <p className="summary-hint">Choose a delivery address to continue.</p>
          )}
          <h3 className="summary-heading">Order summary</h3>
          <div className="summary-line">
            <span>Items ({cart.itemCount}):</span>
            <span>{formatPrice(cart.total)}</span>
          </div>
          <div className="summary-line">
            <span>Delivery:</span>
            <span>Free</span>
          </div>
          <div className="summary-total">
            <span>Order total:</span>
            <span>{formatPrice(cart.total)}</span>
          </div>
        </aside>
      </div>
    </div>
  );
}
