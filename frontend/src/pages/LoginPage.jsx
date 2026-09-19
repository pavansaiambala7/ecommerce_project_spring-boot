import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Container from '@mui/material/Container';
import Divider from '@mui/material/Divider';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import StorefrontIcon from '@mui/icons-material/Storefront';
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
    <Container maxWidth="xs" sx={{ py: 6 }}>
      <Box sx={{ display: 'flex', justifyContent: 'center', mb: 2 }}>
        <StorefrontIcon sx={{ fontSize: 44, color: 'primary.main' }} />
      </Box>

      <Paper component="form" onSubmit={submit} sx={{ p: 3, display: 'grid', gap: 2 }}>
        <Typography variant="h2">Sign in</Typography>
        {error && <Alert severity="error">{error.message}</Alert>}

        <TextField
          id="username"
          label="Username"
          value={form.username}
          autoComplete="username"
          required
          fullWidth
          onChange={(event) => setForm({ ...form, username: event.target.value })}
        />

        <TextField
          id="password"
          label="Password"
          type="password"
          value={form.password}
          autoComplete="current-password"
          required
          fullWidth
          onChange={(event) => setForm({ ...form, password: event.target.value })}
        />

        <Button type="submit" variant="contained" size="large" fullWidth disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </Button>

        <Divider />

        <Typography variant="body2" align="center" color="text.secondary">
          New to ShopKart?{' '}
          <Link component={RouterLink} to="/register" underline="hover">
            Create an account
          </Link>
        </Typography>
      </Paper>
    </Container>
  );
}
