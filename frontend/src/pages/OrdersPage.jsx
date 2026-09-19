import { useCallback, useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Container from '@mui/material/Container';
import Divider from '@mui/material/Divider';
import Paper from '@mui/material/Paper';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { api } from '../api/client';
import { formatDate, formatPrice } from '../utils/format';

const CANCELLABLE = ['CREATED', 'PAID'];

/** Status drives the chip colour, so an order's state reads at a glance. */
const STATUS_COLOUR = {
  CREATED: 'default',
  PAID: 'success',
  SHIPPED: 'info',
  DELIVERED: 'success',
  CANCELLED: 'error',
};

/** One labelled figure in an order's header strip. */
function Fact({ label, value, title }) {
  return (
    <Tooltip title={title ?? ''} disableHoverListener={!title}>
      <Box>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', textTransform: 'uppercase' }}>
          {label}
        </Typography>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          {value}
        </Typography>
      </Box>
    </Tooltip>
  );
}

export default function OrdersPage() {
  const [orders, setOrders] = useState(null);
  const [error, setError] = useState(null);
  const [workingOn, setWorkingOn] = useState(null);

  const load = useCallback(() => {
    api.get('/api/orders/me').then(setOrders).catch(setError);
  }, []);

  useEffect(load, [load]);

  async function cancel(orderId) {
    setWorkingOn(orderId);
    setError(null);
    try {
      await api.post(`/api/orders/${orderId}/cancel`);
      load();
    } catch (err) {
      setError(err);
    } finally {
      setWorkingOn(null);
    }
  }

  if (error && !orders) {
    return (
      <Container maxWidth="md" sx={{ py: 4 }}>
        <Alert severity="error">{error.message}</Alert>
      </Container>
    );
  }

  if (!orders) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', py: 10 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (orders.length === 0) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Paper sx={{ p: 5, textAlign: 'center' }}>
          <Typography variant="h2" gutterBottom>
            No orders yet
          </Typography>
          <Button component={RouterLink} to="/" variant="contained" sx={{ mt: 2 }}>
            Start shopping
          </Button>
        </Paper>
      </Container>
    );
  }

  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Typography variant="h1" sx={{ mb: 2 }}>
        Your Orders
      </Typography>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error.message}
        </Alert>
      )}

      {orders.map((order) => (
        <Paper key={order.id} sx={{ mb: 2, overflow: 'hidden' }}>
          <Box
            sx={{
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'center',
              gap: 4,
              px: 2.5,
              py: 1.5,
              bgcolor: 'background.default',
            }}
          >
            <Fact label="Order placed" value={formatDate(order.createdAt)} />
            <Fact label="Total" value={formatPrice(order.totalAmount)} />
            {order.shippingAddress && (
              <Fact
                label="Ship to"
                value={order.shippingAddress.fullName}
                title={`${order.shippingAddress.line1}, ${order.shippingAddress.city} ${order.shippingAddress.pincode}`}
              />
            )}
            <Fact label="Order #" value={order.id} />
            <Box sx={{ ml: 'auto' }}>
              <Chip label={order.status} size="small" color={STATUS_COLOUR[order.status] ?? 'default'} />
            </Box>
          </Box>
          <Divider />

          <Box sx={{ p: 2.5 }}>
            {order.items.map((item) => (
              <Typography key={item.id} variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
                {item.quantity} × {item.productName} — {formatPrice(item.lineTotal)}
              </Typography>
            ))}

            {CANCELLABLE.includes(order.status) && (
              <Button
                variant="outlined"
                color="error"
                size="small"
                sx={{ mt: 1.5 }}
                disabled={workingOn === order.id}
                onClick={() => cancel(order.id)}
              >
                {workingOn === order.id ? 'Cancelling…' : 'Cancel order'}
              </Button>
            )}
          </Box>
        </Paper>
      ))}
    </Container>
  );
}
