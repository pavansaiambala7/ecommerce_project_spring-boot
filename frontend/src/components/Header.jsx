import { Link } from 'react-router-dom';
import { useAddresses } from '../context/AddressContext';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import SearchBox from './SearchBox';

function DeliverTo() {
  const { isAuthenticated, user } = useAuth();
  const { defaultAddress } = useAddresses();

  if (!isAuthenticated) {
    return (
      <Link to="/login" className="header-link deliver-to">
        <span className="pin" aria-hidden="true">⌖</span>
        <span>
          <small>Hello</small>
          <strong>Select your address</strong>
        </span>
      </Link>
    );
  }

  return (
    <Link to="/account/addresses" className="header-link deliver-to">
      <span className="pin" aria-hidden="true">⌖</span>
      <span>
        <small>Deliver to {defaultAddress?.fullName?.split(' ')[0] ?? user.username}</small>
        <strong>
          {defaultAddress ? `${defaultAddress.city} ${defaultAddress.pincode}` : 'Add an address'}
        </strong>
      </span>
    </Link>
  );
}

export default function Header() {
  const { user, isAuthenticated, isAdmin, logout } = useAuth();
  const { itemCount } = useCart();

  return (
    <header className="header">
      <Link to="/" className="header-logo" aria-label="ShopKart home">
        Shop<span>Kart</span>
        <i>.in</i>
      </Link>

      <DeliverTo />

      <SearchBox />

      <div className="header-actions">
        {isAuthenticated ? (
          <>
            {isAdmin && (
              <Link to="/admin" className="header-link">
                <small>Store</small>
                <strong>Admin</strong>
              </Link>
            )}
            <Link to="/account/addresses" className="header-link">
              <small>Hello, {user.username}</small>
              <strong>Account</strong>
            </Link>
            <Link to="/orders" className="header-link">
              <small>Returns</small>
              <strong>&amp; Orders</strong>
            </Link>
            <button type="button" className="header-link header-signout" onClick={logout}>
              <small>Not you?</small>
              <strong>Sign out</strong>
            </button>
          </>
        ) : (
          <Link to="/login" className="header-link">
            <small>Hello, sign in</small>
            <strong>Account &amp; Lists</strong>
          </Link>
        )}

        <Link to="/cart" className="header-link cart-link" aria-label={`Cart, ${itemCount} items`}>
          <span className="cart-icon">
            <svg width="38" height="30" viewBox="0 0 38 30" aria-hidden="true">
              <path
                d="M2 4h5l4.5 16h19L35 8H10"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.4"
                strokeLinejoin="round"
                strokeLinecap="round"
              />
              <circle cx="14" cy="26" r="2.2" fill="currentColor" />
              <circle cx="28" cy="26" r="2.2" fill="currentColor" />
            </svg>
            <span className="cart-count">{itemCount}</span>
          </span>
          <strong>Cart</strong>
        </Link>
      </div>
    </header>
  );
}
