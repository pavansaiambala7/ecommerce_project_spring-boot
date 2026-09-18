import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import Price from './Price';
import ProductImage from './ProductImage';

export function Stars({ rating, count }) {
  if (rating == null) return null;
  const value = Number(rating);
  // Rounded to the nearest half star, which is as precise as the icons can show.
  const halves = Math.round(value * 2);
  return (
    <div className="rating" title={`${value.toFixed(1)} out of 5 stars`}>
      <span className="stars" aria-label={`${value.toFixed(1)} out of 5 stars`}>
        {Array.from({ length: 5 }, (_, i) => {
          const filled = halves - i * 2;
          return (
            <span key={i} className={filled >= 2 ? 'star full' : filled === 1 ? 'star half' : 'star'}>
              ★
            </span>
          );
        })}
      </span>
      {count != null && <span className="rating-count">{new Intl.NumberFormat('en-IN').format(count)}</span>}
    </div>
  );
}

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
      <Link to={`/product/${product.id}`} className="product-image">
        <ProductImage src={product.image} alt={product.name} />
      </Link>

      <div className="product-info">
        {product.brand && <div className="product-brand">{product.brand}</div>}
        <Link to={`/product/${product.id}`} className="product-name">
          {product.name}
        </Link>

        <Stars rating={product.rating} count={product.ratingCount} />

        <Price product={product} />

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
          Add to cart
        </button>
      </div>
    </article>
  );
}
