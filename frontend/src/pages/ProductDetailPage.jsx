import { useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Breadcrumbs from '@mui/material/Breadcrumbs';
import Button from '@mui/material/Button';
import Container from '@mui/material/Container';
import Divider from '@mui/material/Divider';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import LocationOnOutlinedIcon from '@mui/icons-material/LocationOnOutlined';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
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
    setQuantity(1);
    api
      .get(`/api/products/${id}`, { auth: false })
      .then((data) => active && setProduct(data))
      .catch((err) => active && setError(err));
    return () => {
      active = false;
    };
  }, [id]);

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

  if (error) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Alert severity="error">{error.message}</Alert>
      </Container>
    );
  }

  if (!product) {
    return (
      <Container maxWidth="xl" sx={{ py: 3 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr 280px' }, gap: 3 }}>
          <Skeleton variant="rounded" height={420} />
          <Box>
            <Skeleton height={44} />
            <Skeleton height={28} width="60%" />
            <Skeleton variant="rounded" height={180} sx={{ mt: 2 }} />
          </Box>
          <Skeleton variant="rounded" height={320} />
        </Box>
      </Container>
    );
  }

  const { parent } = product.category ? findInTree(tree, product.category.id) : { parent: null };
  const savings =
    product.mrp && Number(product.mrp) > Number(product.price) ? Number(product.mrp) - Number(product.price) : 0;

  return (
    <Container maxWidth="xl" sx={{ py: 2 }}>
      {product.category && (
        <Breadcrumbs separator={<NavigateNextIcon fontSize="small" />} sx={{ mb: 2 }}>
          {parent && (
            <Link component={RouterLink} to={browseLink({ categoryId: parent.id })} underline="hover" variant="body2">
              {parent.name}
            </Link>
          )}
          <Link
            component={RouterLink}
            to={browseLink({ categoryId: product.category.id })}
            underline="hover"
            variant="body2"
          >
            {product.category.name}
          </Link>
        </Breadcrumbs>
      )}

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr', lg: '420px 1fr 300px' }, gap: 3 }}>
        <Paper sx={{ p: 3, height: { xs: 320, md: 440 }, bgcolor: 'common.white' }}>
          <ProductImage src={product.image} alt={product.name} loading="eager" />
        </Paper>

        <Box>
          <Typography variant="h1" gutterBottom>
            {product.name}
          </Typography>
          {product.brand && (
            <Link component={RouterLink} to={browseLink({ q: product.brand })} underline="hover" variant="body2">
              Visit the {product.brand} Store
            </Link>
          )}
          <Box sx={{ mt: 1 }}>
            <Stars rating={product.rating} count={product.ratingCount} size="medium" />
          </Box>

          <Divider sx={{ my: 2 }} />

          <Price product={product} size="lg" />
          <Typography variant="caption" color="text.secondary">
            Inclusive of all taxes
          </Typography>

          <Divider sx={{ my: 2 }} />

          <Typography variant="h3" gutterBottom>
            About this item
          </Typography>
          <Typography variant="body2" sx={{ lineHeight: 1.7 }}>
            {product.description}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
            Item weight: {product.weight} g
          </Typography>
        </Box>

        <Paper sx={{ p: 2, alignSelf: 'start', display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          <Price product={product} />
          {savings > 0 && (
            <Typography variant="body2" color="success.main">
              You save {formatPrice(savings)}
            </Typography>
          )}
          {defaultAddress && (
            <Link
              component={RouterLink}
              to="/account/addresses"
              underline="hover"
              variant="body2"
              sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}
            >
              <LocationOnOutlinedIcon fontSize="small" />
              Deliver to {defaultAddress.fullName.split(' ')[0]} - {defaultAddress.city} {defaultAddress.pincode}
            </Link>
          )}
          <Typography variant="body2" sx={{ color: product.inStock ? 'success.main' : 'error.main', fontWeight: 600 }}>
            {product.inStock ? 'In stock' : 'Currently unavailable'}
          </Typography>

          <TextField
            select
            label="Quantity"
            value={quantity}
            onChange={(event) => setQuantity(Number(event.target.value))}
            disabled={!product.inStock}
          >
            {Array.from({ length: Math.min(10, Math.max(product.quantity, 1)) }, (_, index) => (
              <MenuItem key={index + 1} value={index + 1}>
                {index + 1}
              </MenuItem>
            ))}
          </TextField>

          <Button
            variant="contained"
            color="secondary"
            fullWidth
            disabled={!product.inStock || busy}
            onClick={() => addToCart(false)}
          >
            Add to Cart
          </Button>
          <Button
            variant="outlined"
            fullWidth
            disabled={!product.inStock || busy}
            onClick={() => addToCart(true)}
          >
            Buy Now
          </Button>
        </Paper>
      </Box>
    </Container>
  );
}
