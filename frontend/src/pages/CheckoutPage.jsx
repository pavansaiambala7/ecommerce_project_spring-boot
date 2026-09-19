import { useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Container from '@mui/material/Container';
import Divider from '@mui/material/Divider';
import FormControlLabel from '@mui/material/FormControlLabel';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import Typography from '@mui/material/Typography';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import { api } from '../api/client';
import AddressForm from '../components/AddressForm';
import ProductImage from '../components/ProductImage';
import { useAddresses } from '../context/AddressContext';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import { formatPrice } from '../utils/format';
import { loadRazorpay } from '../utils/razorpay';

/** A fresh key per attempt, so a retry after a failure is genuinely a new attempt. */
function newIdempotencyKey() {
  return `checkout-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function oneLine(address) {
  return [address.line1, address.line2, address.city, address.state, address.pincode].filter(Boolean).join(', ');
}

/** One numbered step of the checkout, greyed out until it can be acted on. */
function Step({ number, title, action, disabled, children }) {
  return (
    <Paper sx={{ p: { xs: 2, sm: 3 }, mb: 2, opacity: disabled ? 0.55 : 1 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: children ? 2 : 0 }}>
        <Avatar sx={{ bgcolor: 'primary.main', width: 28, height: 28, fontSize: 14 }}>{number}</Avatar>
        <Typography variant="h3" sx={{ flex: 1 }}>
          {title}
        </Typography>
        {action}
      </Box>
      {children}
    </Paper>
  );
}

/** A selectable card, used for both addresses and payment methods. */
function ChoiceCard({ selected, value, primary, secondary, name }) {
  return (
    <Paper
      variant="outlined"
      sx={{
        p: 1.5,
        mb: 1.25,
        borderColor: selected ? 'primary.main' : 'divider',
        borderWidth: selected ? 2 : 1,
        bgcolor: selected ? 'action.hover' : 'background.paper',
      }}
    >
      <FormControlLabel
        value={value}
        name={name}
        control={<Radio size="small" />}
        sx={{ alignItems: 'flex-start', m: 0, width: '100%' }}
        label={
          <Box sx={{ pt: 0.25 }}>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {primary}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {secondary}
            </Typography>
          </Box>
        }
      />
    </Paper>
  );
}

export default function CheckoutPage() {
  const { cart, checkout } = useCart();
  const { user } = useAuth();
  const { addresses, loaded, defaultAddress } = useAddresses();
  const navigate = useNavigate();

  const [addressId, setAddressId] = useState(null);
  const [choosingAddress, setChoosingAddress] = useState(true);
  const [addingAddress, setAddingAddress] = useState(false);
  const [method, setMethod] = useState('COD');
  const [online, setOnline] = useState({ enabled: false, keyId: '' });
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState(null);
  const [placed, setPlaced] = useState(null);

  useEffect(() => {
    api
      .get('/api/payments/razorpay/config', { auth: false })
      .then(setOnline)
      .catch(() => setOnline({ enabled: false, keyId: '' }));
  }, []);

  // Preselect the default address once the address book has loaded, and go
  // straight to payment when there is one, as a returning shopper expects.
  useEffect(() => {
    if (loaded && addressId === null && defaultAddress) {
      setAddressId(defaultAddress.id);
      setChoosingAddress(false);
    }
  }, [loaded, defaultAddress, addressId]);

  const selected = addresses.find((a) => a.id === addressId) ?? null;

  async function payWithRazorpay(order) {
    const ready = await loadRazorpay();
    if (!ready) {
      throw new Error('Could not reach the payment provider. Try cash on delivery.');
    }

    const session = await api.post(`/api/payments/razorpay/orders/${order.id}`);

    // Razorpay's widget is callback-based; wrapping it in a promise keeps the
    // caller's flow linear and makes dismissal a normal rejection rather than
    // a state the page has to detect separately.
    return new Promise((resolve, reject) => {
      const checkoutWidget = new window.Razorpay({
        key: session.keyId,
        amount: session.amountMinor,
        currency: session.currency,
        order_id: session.razorpayOrderId,
        name: 'ShopKart',
        description: `Order #${order.id}`,
        prefill: { name: selected?.fullName ?? user?.username ?? '', contact: selected?.phone ?? '' },
        theme: { color: '#00695c' },
        handler: async (response) => {
          try {
            // Nothing here is trusted by the server: it re-derives the
            // signature with a secret this page never sees. The page only
            // relays what the widget returned.
            const payment = await api.post('/api/payments/razorpay/confirm', {
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
            });
            resolve(payment);
          } catch (err) {
            reject(err);
          }
        },
        modal: {
          ondismiss: () => reject(new Error('Payment cancelled.')),
        },
      });

      checkoutWidget.on('payment.failed', (event) => {
        reject(new Error(event?.error?.description ?? 'Payment failed.'));
      });

      checkoutWidget.open();
    });
  }

  async function placeOrder() {
    if (!selected) {
      setError(new Error('Choose a delivery address first.'));
      setChoosingAddress(true);
      return;
    }
    setPlacing(true);
    setError(null);
    try {
      // One key for this attempt. A double-click or a retried request reaches
      // the server with the same key and produces the same single order.
      const order = await checkout(newIdempotencyKey(), selected.id);

      const payment =
        method === 'COD'
          ? await api.post(
              '/api/payments',
              { orderId: order.id, method },
              { headers: { 'Idempotency-Key': newIdempotencyKey() } },
            )
          : await payWithRazorpay(order);

      setPlaced({ order, payment });
    } catch (err) {
      // The order exists even when payment fails; the customer can pay it from
      // their orders page rather than losing the basket.
      setError(err);
    } finally {
      setPlacing(false);
    }
  }

  if (placed) {
    const ship = placed.order.shippingAddress;
    return (
      <Container maxWidth="sm" sx={{ py: 5 }}>
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <CheckCircleOutlineIcon sx={{ fontSize: 64, color: 'success.main' }} />
          <Typography variant="h2" sx={{ mt: 1, mb: 2 }}>
            Order placed, thank you!
          </Typography>
          <Typography variant="body1" gutterBottom>
            Order <strong>#{placed.order.id}</strong> for <strong>{formatPrice(placed.order.totalAmount)}</strong> is{' '}
            <Chip label={placed.order.status} size="small" color="primary" />
          </Typography>
          {ship && (
            <Typography variant="body2" color="text.secondary">
              Delivering to <strong>{ship.fullName}</strong>, {oneLine(ship)}
            </Typography>
          )}
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Payment {placed.payment.method} — {placed.payment.status}
            {placed.payment.transactionId && ` (${placed.payment.transactionId})`}
          </Typography>
          <Box sx={{ display: 'flex', gap: 1.5, justifyContent: 'center', mt: 3 }}>
            <Button component={RouterLink} to="/orders" variant="contained">
              View your orders
            </Button>
            <Button component={RouterLink} to="/" variant="outlined">
              Continue shopping
            </Button>
          </Box>
        </Paper>
      </Container>
    );
  }

  if (!cart || cart.items.length === 0) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Paper sx={{ p: 5, textAlign: 'center' }}>
          <Typography variant="h2" gutterBottom>
            Nothing to check out
          </Typography>
          <Button variant="contained" onClick={() => navigate('/')} sx={{ mt: 2 }}>
            Browse products
          </Button>
        </Paper>
      </Container>
    );
  }

  return (
    <Container maxWidth="xl" sx={{ py: 3 }}>
      <Typography variant="h1" sx={{ mb: 2 }}>
        Checkout
      </Typography>

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 300px' }, gap: 3 }}>
        <Box>
          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error.message}
            </Alert>
          )}

          <Step
            number={1}
            title="Delivery address"
            action={
              !choosingAddress && selected ? (
                <Button size="small" onClick={() => setChoosingAddress(true)}>
                  Change
                </Button>
              ) : null
            }
          >
            {!loaded && <CircularProgress size={22} />}

            {loaded && !choosingAddress && selected && (
              <Typography variant="body2">
                <strong>{selected.fullName}</strong>, {oneLine(selected)}
              </Typography>
            )}

            {loaded && (choosingAddress || !selected) && (
              <>
                {addresses.length > 0 && !addingAddress && (
                  <>
                    <RadioGroup
                      aria-label="Delivery address"
                      value={addressId ?? ''}
                      onChange={(event) => setAddressId(Number(event.target.value))}
                    >
                      {addresses.map((address) => (
                        <ChoiceCard
                          key={address.id}
                          name="address"
                          value={address.id}
                          selected={address.id === addressId}
                          primary={
                            <>
                              {address.fullName}
                              {address.isDefault && (
                                <Chip label="Default" size="small" sx={{ ml: 1, height: 18, fontSize: 10 }} />
                              )}
                            </>
                          }
                          secondary={oneLine(address)}
                        />
                      ))}
                    </RadioGroup>
                    <Button size="small" onClick={() => setAddingAddress(true)} sx={{ mb: 1 }}>
                      + Add a new address
                    </Button>
                    <Box>
                      <Button variant="contained" disabled={!addressId} onClick={() => setChoosingAddress(false)}>
                        Use this address
                      </Button>
                    </Box>
                  </>
                )}

                {(addresses.length === 0 || addingAddress) && (
                  <AddressForm
                    submitLabel="Use this address"
                    onSaved={(saved) => {
                      setAddressId(saved.id);
                      setAddingAddress(false);
                      setChoosingAddress(false);
                    }}
                    onCancel={addresses.length > 0 ? () => setAddingAddress(false) : undefined}
                  />
                )}
              </>
            )}
          </Step>

          <Step number={2} title="Payment method" disabled={!selected || choosingAddress}>
            {selected && !choosingAddress && (
              <>
                <RadioGroup value={method} onChange={(event) => setMethod(event.target.value)}>
                  <ChoiceCard
                    name="method"
                    value="COD"
                    selected={method === 'COD'}
                    primary="Cash on Delivery"
                    secondary="Pay when your order arrives"
                  />
                  {online.enabled && (
                    <ChoiceCard
                      name="method"
                      value="ONLINE"
                      selected={method === 'ONLINE'}
                      primary="Credit or debit card, UPI, net banking"
                      secondary="Secured by Razorpay"
                    />
                  )}
                </RadioGroup>
                {!online.enabled && (
                  <Typography variant="caption" color="text.secondary">
                    Online payment is currently unavailable.
                  </Typography>
                )}
              </>
            )}
          </Step>

          <Step number={3} title="Review items">
            {cart.items.map((item) => (
              <Box
                key={item.productId}
                sx={{ display: 'grid', gridTemplateColumns: '64px 1fr auto', gap: 2, alignItems: 'center', py: 1 }}
              >
                <Box sx={{ height: 60, bgcolor: 'common.white', borderRadius: 1 }}>
                  <ProductImage src={item.image} alt={item.productName} />
                </Box>
                <Box>
                  <Link
                    component={RouterLink}
                    to={`/product/${item.productId}`}
                    underline="hover"
                    color="text.primary"
                    variant="body2"
                  >
                    {item.productName}
                  </Link>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    Qty: {item.quantity}
                  </Typography>
                </Box>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {formatPrice(item.lineTotal)}
                </Typography>
              </Box>
            ))}
          </Step>
        </Box>

        <Paper sx={{ p: 2.5, alignSelf: 'start', position: { md: 'sticky' }, top: 80 }}>
          <Button
            variant="contained"
            color="secondary"
            fullWidth
            size="large"
            disabled={placing || !selected || choosingAddress}
            onClick={placeOrder}
            startIcon={placing ? <CircularProgress size={18} color="inherit" /> : null}
          >
            {placing ? 'Placing order…' : method === 'COD' ? 'Place your order' : 'Pay now'}
          </Button>
          {(!selected || choosingAddress) && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
              Choose a delivery address to continue.
            </Typography>
          )}

          <Divider sx={{ my: 2 }} />

          <Typography variant="h3" gutterBottom>
            Order summary
          </Typography>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
            <Typography variant="body2">Items ({cart.itemCount}):</Typography>
            <Typography variant="body2">{formatPrice(cart.total)}</Typography>
          </Box>
          <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
            <Typography variant="body2">Delivery:</Typography>
            <Typography variant="body2">Free</Typography>
          </Box>
          <Divider sx={{ my: 1.5 }} />
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
            <Typography variant="body1" sx={{ fontWeight: 700 }}>
              Order total:
            </Typography>
            <Typography variant="h6" color="error.main">
              {formatPrice(cart.total)}
            </Typography>
          </Box>
        </Paper>
      </Box>
    </Container>
  );
}
