import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import { formatPrice } from '../utils/format';

export default function ProductDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { addItem, busy } = useCart();
  const { isAuthenticated } = useAuth();
  const [product, setProduct] = useState(null);
  const [error, setError] = useState(null);
  const [quantity, setQuantity] = useState(1);

  useEffect(() => {
    let active = true;
    setProduct(null);
    setError(null);
    api
      .get(`/api/products/${id}`, { auth: false })
      .then((data) => active && setProduct(data))
      .catch((err) => active && setError(err));
    return () => {
      active = false;
    };
  }, [id]);

  if (error) return <div className="page-status">{error.message}</div>;
  if (!product) return <div className="page-status">Loading…</div>;

  async function addToCart(goToCart) {
    // The cart API is authenticated; send the shopper to sign in rather than
    // letting the call fail with an unhandled 401.
    if (!isAuthenticated) {
      navigate('/login');
      return;
    }
    await addItem(product.id, quantity);
    if (goToCart) navigate('/cart');
  }

  return (
    <div className="detail">
      <div>
        <img src={product.image} alt={product.name} />
      </div>

      <div>
        <h1>{product.name}</h1>
        {product.category && (
          <div className="meta-row">
            Category:{' '}
            <Link to={`/?category=${product.category.id}`} style={{ color: 'var(--link)' }}>
              {product.category.name}
            </Link>
          </div>
        )}
        <div className="price" style={{ margin: '12px 0' }}>
          {formatPrice(product.price)}
        </div>
        <p style={{ lineHeight: 1.6 }}>{product.description}</p>
        <div className="meta-row">Weight: {product.weight} g</div>
        <div className="meta-row">Units available: {product.quantity}</div>
      </div>

      <aside className="buy-box">
        <div className="price">{formatPrice(product.price)}</div>
        {product.inStock ? (
          <span className="stock-ok">In stock</span>
        ) : (
          <span className="stock-out">Currently unavailable</span>
        )}

        <label>
          Quantity:{' '}
          <select
            value={quantity}
            onChange={(event) => setQuantity(Number(event.target.value))}
            disabled={!product.inStock}
          >
            {Array.from({ length: Math.min(10, Math.max(product.quantity, 1)) }, (_, index) => (
              <option key={index + 1} value={index + 1}>
                {index + 1}
              </option>
            ))}
          </select>
        </label>

        <button
          type="button"
          className="btn btn-block"
          disabled={!product.inStock || busy}
          onClick={() => addToCart(false)}
        >
          Add to Cart
        </button>
        <button
          type="button"
          className="btn-plain btn-block"
          disabled={!product.inStock || busy}
          onClick={() => addToCart(true)}
        >
          Buy Now
        </button>
      </aside>
    </div>
  );
}
