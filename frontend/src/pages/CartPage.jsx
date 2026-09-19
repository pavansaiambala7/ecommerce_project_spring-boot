import { Link as RouterLink, useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Container from '@mui/material/Container';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import RemoveShoppingCartOutlinedIcon from '@mui/icons-material/RemoveShoppingCartOutlined';
import ProductImage from '../components/ProductImage';
import { useCart } from '../context/CartContext';
import { formatPrice } from '../utils/format';

export default function CartPage() {
  const { cart, busy, updateQuantity, removeItem, clear } = useCart();
  const navigate = useNavigate();

  if (!cart || cart.items.length === 0) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Paper sx={{ p: 5, textAlign: 'center' }}>
          <RemoveShoppingCartOutlinedIcon sx={{ fontSize: 56, color: 'text.disabled', mb: 1 }} />
          <Typography variant="h2" gutterBottom>
            Your cart is empty
          </Typography>
          <Button component={RouterLink} to="/" variant="contained" sx={{ mt: 2 }}>
            Continue shopping
          </Button>
        </Paper>
      </Container>
    );
  }

  return (
    <Container maxWidth="xl" sx={{ py: 3 }}>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 300px' }, gap: 3 }}>
        <Paper sx={{ p: { xs: 2, sm: 3 } }}>
          <Typography variant="h2" gutterBottom>
            Shopping Cart
          </Typography>
          <Divider />

          {cart.items.map((item) => (
            <Box key={item.productId}>
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: { xs: '90px 1fr', sm: '120px 1fr auto' },
                  gap: 2,
                  py: 2,
                  alignItems: 'start',
                }}
              >
                <Box
                  component={RouterLink}
                  to={`/product/${item.productId}`}
                  sx={{ height: 110, bgcolor: 'common.white', borderRadius: 1, p: 0.5 }}
                >
                  <ProductImage src={item.image} alt={item.productName} />
                </Box>

                <Box>
                  <Link
                    component={RouterLink}
                    to={`/product/${item.productId}`}
                    underline="hover"
                    color="text.primary"
                    sx={{ fontWeight: 500 }}
                  >
                    {item.productName}
                  </Link>
                  <Typography
                    variant="caption"
                    sx={{ display: 'block', mt: 0.5, color: item.inStock ? 'success.main' : 'error.main' }}
                  >
                    {item.inStock ? 'In stock' : 'Not enough stock for this quantity'}
                  </Typography>

                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mt: 1.5 }}>
                    <TextField
                      id={`qty-${item.productId}`}
                      label="Qty"
                      type="number"
                      value={item.quantity}
                      disabled={busy}
                      inputProps={{ min: 1 }}
                      onChange={(event) => {
                        const next = Number(event.target.value);
                        if (next >= 1) updateQuantity(item.productId, next);
                      }}
                      sx={{ width: 90 }}
                    />
                    <Tooltip title="Remove from cart">
                      <span>
                        <IconButton
                          color="error"
                          disabled={busy}
                          aria-label={`Delete ${item.productName}`}
                          onClick={() => removeItem(item.productId)}
                        >
                          <DeleteOutlineIcon />
                        </IconButton>
                      </span>
                    </Tooltip>
                  </Box>

                  <Typography variant="h6" sx={{ display: { sm: 'none' }, mt: 1 }}>
                    {formatPrice(item.lineTotal)}
                  </Typography>
                </Box>

                <Typography variant="h6" sx={{ display: { xs: 'none', sm: 'block' } }}>
                  {formatPrice(item.lineTotal)}
                </Typography>
              </Box>
              <Divider />
            </Box>
          ))}

          <Button color="inherit" disabled={busy} onClick={clear} sx={{ mt: 2 }}>
            Clear cart
          </Button>
        </Paper>

        <Paper sx={{ p: 2.5, alignSelf: 'start', position: { md: 'sticky' }, top: 80 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 2 }}>
            <Typography variant="body1">Subtotal ({cart.itemCount} items)</Typography>
            <Typography variant="h6">{formatPrice(cart.total)}</Typography>
          </Box>
          <Button
            variant="contained"
            color="secondary"
            fullWidth
            size="large"
            disabled={busy}
            onClick={() => navigate('/checkout')}
          >
            Proceed to checkout
          </Button>
        </Paper>
      </Box>
    </Container>
  );
}
