import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';

export default function LoginPage() {
  const { login } = useAuth();
  const { refresh } = useCart();
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', password: '' });
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(form.username, form.password);
      await refresh();
      navigate('/');
    } catch (err) {
      // Never retried automatically: the auth endpoints allow 5 requests a
      // minute per IP, so a retry would spend the user's remaining attempts.
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="form-card" onSubmit={submit}>
      <h1>Sign in</h1>
      {error && <div className="error">{error.message}</div>}

      <div className="field">
        <label htmlFor="username">Username</label>
        <input
          id="username"
          value={form.username}
          autoComplete="username"
          required
          onChange={(event) => setForm({ ...form, username: event.target.value })}
        />
      </div>

      <div className="field">
        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          value={form.password}
          autoComplete="current-password"
          required
          onChange={(event) => setForm({ ...form, password: event.target.value })}
        />
      </div>

      <button type="submit" className="btn btn-block" disabled={submitting}>
        {submitting ? 'Signing in…' : 'Sign in'}
      </button>

      <div className="form-foot">
        New to ShopKart? <Link to="/register">Create an account</Link>
      </div>
    </form>
  );
}
