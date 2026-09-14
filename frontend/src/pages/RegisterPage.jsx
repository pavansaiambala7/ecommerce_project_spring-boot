import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', email: '', password: '', address: '' });
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  function update(field) {
    return (event) => setForm({ ...form, [field]: event.target.value });
  }

  async function submit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await register(form);
      navigate('/');
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="form-card" onSubmit={submit}>
      <h1>Create account</h1>
      {error && (
        <div className="error">
          {error.message}
          {/* Bean validation failures come back keyed by field name. */}
          {error.errors &&
            Object.entries(error.errors).map(([field, message]) => (
              <div key={field}>
                {field}: {message}
              </div>
            ))}
        </div>
      )}

      <div className="field">
        <label htmlFor="username">Username</label>
        <input id="username" value={form.username} required minLength={3} maxLength={50} onChange={update('username')} />
      </div>

      <div className="field">
        <label htmlFor="email">Email</label>
        <input id="email" type="email" value={form.email} required onChange={update('email')} />
      </div>

      <div className="field">
        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          value={form.password}
          required
          minLength={8}
          maxLength={100}
          autoComplete="new-password"
          onChange={update('password')}
        />
        <small style={{ color: 'var(--muted)' }}>At least 8 characters.</small>
      </div>

      <div className="field">
        <label htmlFor="address">Address (optional)</label>
        <input id="address" value={form.address} maxLength={255} onChange={update('address')} />
      </div>

      <button type="submit" className="btn btn-block" disabled={submitting}>
        {submitting ? 'Creating…' : 'Create your account'}
      </button>

      <div className="form-foot">
        Already have an account? <Link to="/login">Sign in</Link>
      </div>
    </form>
  );
}
