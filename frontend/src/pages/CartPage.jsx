import { Link, useNavigate } from 'react-router-dom';
import ProductImage from '../components/ProductImage';
import { useCart } from '../context/CartContext';
import { formatPrice } from '../utils/format';

export default function CartPage() {
  const { cart, busy, updateQuantity, removeItem, clear } = useCart();
  const navigate = useNavigate();

  if (!cart || cart.items.length === 0) {
    return (
      <div className="panel">
        <h1 className="section-title">Your cart is empty</h1>
        <Link to="/" className="btn">
          Continue shopping
        </Link>
      </div>
    );
  }

  return (
    <div className="cart-layout">
      <section className="panel">
        <h1 className="section-title">Shopping Cart</h1>

        {cart.items.map((item) => (
          <div className="cart-row" key={item.productId}>
            <Link to={`/product/${item.productId}`}>
              <ProductImage src={item.image} alt={item.productName} />
            </Link>

            <div>
              <Link to={`/product/${item.productId}`} className="product-name">
                {item.productName}
              </Link>
              {item.inStock ? (
                <div className="stock-ok">In stock</div>
              ) : (
                <div className="stock-out">Not enough stock for this quantity</div>
              )}

              <div className="qty">
                <label htmlFor={`qty-${item.productId}`}>Qty:</label>
                <input
                  id={`qty-${item.productId}`}
                  type="number"
                  min="1"
                  value={item.quantity}
                  disabled={busy}
                  onChange={(event) => {
                    const next = Number(event.target.value);
                    if (next >= 1) updateQuantity(item.productId, next);
                  }}
                />
                <button
                  type="button"
                  className="btn-plain"
                  disabled={busy}
                  onClick={() => removeItem(item.productId)}
                >
                  Delete
                </button>
              </div>
            </div>

            <div className="price">{formatPrice(item.lineTotal)}</div>
          </div>
        ))}

        <div style={{ marginTop: 16 }}>
          <button type="button" className="btn-plain" disabled={busy} onClick={clear}>
            Clear cart
          </button>
        </div>
      </section>

      <aside className="summary">
        <div className="summary-total">
          <span>Subtotal ({cart.itemCount} items)</span>
          <span>{formatPrice(cart.total)}</span>
        </div>
        <button
          type="button"
          className="btn btn-block"
          disabled={busy}
          onClick={() => navigate('/checkout')}
        >
          Proceed to checkout
        </button>
      </aside>
    </div>
  );
}
