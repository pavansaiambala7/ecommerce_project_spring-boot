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

  // Bean validation failures come back keyed by field name, so each one is
  // shown under the field it belongs to rather than in the summary.
  const fieldError = (name) => error?.errors?.[name];

  return (
    <Container maxWidth="xs" sx={{ py: 6 }}>
      <Box sx={{ display: 'flex', justifyContent: 'center', mb: 2 }}>
        <StorefrontIcon sx={{ fontSize: 44, color: 'primary.main' }} />
      </Box>

      <Paper component="form" onSubmit={submit} sx={{ p: 3, display: 'grid', gap: 2 }}>
        <Typography variant="h2">Create account</Typography>
        {error && <Alert severity="error">{error.message}</Alert>}

        <TextField
          id="username"
          label="Username"
          value={form.username}
          required
          fullWidth
          inputProps={{ minLength: 3, maxLength: 50 }}
          error={Boolean(fieldError('username'))}
          helperText={fieldError('username')}
          onChange={update('username')}
        />

        <TextField
          id="email"
          label="Email"
          type="email"
          value={form.email}
          required
          fullWidth
          error={Boolean(fieldError('email'))}
          helperText={fieldError('email')}
          onChange={update('email')}
        />

        <TextField
          id="password"
          label="Password"
          type="password"
          value={form.password}
          required
          fullWidth
          autoComplete="new-password"
          inputProps={{ minLength: 8, maxLength: 100 }}
          error={Boolean(fieldError('password'))}
          helperText={fieldError('password') ?? 'At least 8 characters.'}
          onChange={update('password')}
        />

        <TextField
          id="address"
          label="Address (optional)"
          value={form.address}
          fullWidth
          inputProps={{ maxLength: 255 }}
          error={Boolean(fieldError('address'))}
          helperText={fieldError('address')}
          onChange={update('address')}
        />

        <Button type="submit" variant="contained" size="large" fullWidth disabled={submitting}>
          {submitting ? 'Creating…' : 'Create your account'}
        </Button>

        <Divider />

        <Typography variant="body2" align="center" color="text.secondary">
          Already have an account?{' '}
          <Link component={RouterLink} to="/login" underline="hover">
            Sign in
          </Link>
        </Typography>
      </Paper>
    </Container>
  );
}
