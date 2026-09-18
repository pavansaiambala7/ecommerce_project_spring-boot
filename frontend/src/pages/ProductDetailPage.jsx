import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import { useAddresses } from '../context/AddressContext';
import Price from '../components/Price';
import ProductImage from '../components/ProductImage';
import { Stars } from '../components/ProductCard';
import { browseLink, findInTree, useCategoryTree } from '../hooks/useCatalog';
import { formatPrice } from '../utils/format';

export default function ProductDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { addItem, busy } = useCart();
  const { isAuthenticated } = useAuth();
  const { defaultAddress } = useAddresses();
  const { tree } = useCategoryTree();
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

  const { parent } = product.category ? findInTree(tree, product.category.id) : { parent: null };
  const savings = product.mrp && Number(product.mrp) > Number(product.price)
    ? Number(product.mrp) - Number(product.price)
    : 0;

  return (
    <>
      {product.category && (
        <nav className="breadcrumb" aria-label="Breadcrumb">
          {parent && (
            <>
              <Link to={browseLink({ categoryId: parent.id })}>{parent.name}</Link>
              <span aria-hidden="true">›</span>
            </>
          )}
          <Link to={browseLink({ categoryId: product.category.id })}>{product.category.name}</Link>
        </nav>
      )}
    <div className="detail">
      <div className="detail-image">
        <ProductImage src={product.image} alt={product.name} loading="eager" />
      </div>

      <div>
        <h1>{product.name}</h1>
        {product.brand && (
          <Link to={browseLink({ q: product.brand })} className="detail-brand">
            Visit the {product.brand} Store
          </Link>
        )}
        <Stars rating={product.rating} count={product.ratingCount} />
        <hr className="detail-rule" />
        <Price product={product} size="lg" />
        <div className="meta-row">Inclusive of all taxes</div>
        <hr className="detail-rule" />
        <h2 className="detail-subhead">About this item</h2>
        <p style={{ lineHeight: 1.6 }}>{product.description}</p>
        <div className="meta-row">Item weight: {product.weight} g</div>
      </div>

      <aside className="buy-box">
        <Price product={product} />
        {savings > 0 && <div className="savings">You save {formatPrice(savings)}</div>}
        {defaultAddress && (
          <Link to="/account/addresses" className="deliver-line">
            ⌖ Deliver to {defaultAddress.fullName.split(' ')[0]} - {defaultAddress.city} {defaultAddress.pincode}
          </Link>
        )}
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
    </>
  );
}
