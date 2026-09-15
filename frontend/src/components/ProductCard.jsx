import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import { formatPrice } from '../utils/format';

export default function ProductCard({ product }) {
  const { addItem, busy } = useCart();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  // The cart endpoints are authenticated, so without this a signed-out shopper
  // would just get a rejected 401 promise and no feedback at all.
  function add() {
    if (!isAuthenticated) {
      navigate('/login');
      return;
    }
    addItem(product.id, 1);
  }

  return (
    <article className="product-card">
      <Link to={`/product/${product.id}`}>
        <img src={product.image} alt={product.name} loading="lazy" />
      </Link>

      <Link to={`/product/${product.id}`} className="product-name">
        {product.name}
      </Link>

      {product.brand && <div style={{ color: 'var(--muted)', fontSize: 13 }}>{product.brand}</div>}

      {product.rating != null && (
        <div className="rating">
          {'★'.repeat(Math.round(product.rating))}
          {'☆'.repeat(5 - Math.round(product.rating))}
          <span>{product.ratingCount.toLocaleString()}</span>
        </div>
      )}

      <div className="price">{formatPrice(product.price)}</div>

      {product.inStock ? (
        <span className="stock-ok">In stock</span>
      ) : (
        <span className="stock-out">Currently unavailable</span>
      )}

      <button
        type="button"
        className="btn btn-block"
        disabled={!product.inStock || busy}
        onClick={add}
        title={isAuthenticated ? undefined : 'You will be asked to sign in'}
      >
        Add to Cart
      </button>
    </article>
  );
}
