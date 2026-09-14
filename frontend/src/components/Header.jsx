import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';

export default function Header() {
  const [term, setTerm] = useState('');
  const { user, isAuthenticated, logout } = useAuth();
  const { itemCount } = useCart();
  const navigate = useNavigate();

  function submitSearch(event) {
    event.preventDefault();
    const query = term.trim();
    if (query) navigate(`/search?q=${encodeURIComponent(query)}`);
  }

  return (
    <header className="header">
      <Link to="/" className="header-logo">
        Shop<span>Kart</span>
      </Link>

      <form className="search" onSubmit={submitSearch}>
        <input
          type="search"
          value={term}
          onChange={(event) => setTerm(event.target.value)}
          placeholder="Search ShopKart"
          aria-label="Search products"
        />
        <button type="submit">Search</button>
      </form>

      <div className="header-actions">
        {isAuthenticated ? (
          <>
            <Link to="/orders" className="header-link">
              <small>Hello, {user.username}</small>
              <strong>Orders</strong>
            </Link>
            <button type="button" className="header-link" onClick={logout}>
              <small>Not you?</small>
              <strong>Sign out</strong>
            </button>
          </>
        ) : (
          <Link to="/login" className="header-link">
            <small>Hello, sign in</small>
            <strong>Account</strong>
          </Link>
        )}

        <Link to="/cart" className="header-link cart-link">
          <span className="cart-count">{itemCount}</span>
          <span>Cart</span>
        </Link>
      </div>
    </header>
  );
}
