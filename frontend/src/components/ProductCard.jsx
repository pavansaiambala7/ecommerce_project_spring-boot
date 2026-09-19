import { Link as RouterLink, useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Link from '@mui/material/Link';
import Rating from '@mui/material/Rating';
import Typography from '@mui/material/Typography';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import Price from './Price';
import ProductImage from './ProductImage';

/** Half-star rating plus the number of ratings, as every marketplace shows it. */
export function Stars({ rating, count, size = 'small' }) {
  if (rating == null) return null;
  const value = Number(rating);
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
      <Rating
        value={value}
        precision={0.5}
        size={size}
        readOnly
        sx={{ color: 'secondary.main' }}
        title={`${value.toFixed(1)} out of 5 stars`}
      />
      {count != null && (
        <Typography variant="caption" color="text.secondary">
          {new Intl.NumberFormat('en-IN').format(count)}
        </Typography>
      )}
    </Box>
  );
}

export default function ProductCard({ product }) {
  const { addItem, busy } = useCart();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  // The cart endpoints are authenticated, so without this a signed-out shopper
  // would just get a rejected 401 promise and no feedback at all.
  function add() {
    if (!isAuthenticated) {
      navigate('/login');
      return;
    }
    addItem(product.id, 1);
  }

  return (
    <Card sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Box
        component={RouterLink}
        to={`/product/${product.id}`}
        sx={{ display: 'block', height: 200, p: 2, bgcolor: 'common.white' }}
      >
        <ProductImage src={product.image} alt={product.name} />
      </Box>

      <CardContent sx={{ display: 'flex', flexDirection: 'column', gap: 0.75, flex: 1, pt: 1.5 }}>
        {product.brand && (
          <Typography variant="caption" color="text.secondary" noWrap>
            {product.brand}
          </Typography>
        )}
        <Link
          component={RouterLink}
          to={`/product/${product.id}`}
          underline="hover"
          color="text.primary"
          className="clamp-2"
          sx={{ fontSize: 14, fontWeight: 500, minHeight: 40 }}
        >
          {product.name}
        </Link>

        <Stars rating={product.rating} count={product.ratingCount} />

        <Price product={product} size="sm" />

        <Typography variant="caption" sx={{ color: product.inStock ? 'success.main' : 'error.main' }}>
          {product.inStock ? 'In stock' : 'Currently unavailable'}
        </Typography>

        <Button
          variant="contained"
          color="secondary"
          fullWidth
          disabled={!product.inStock || busy}
          onClick={add}
          title={isAuthenticated ? undefined : 'You will be asked to sign in'}
          sx={{ mt: 'auto' }}
        >
          Add to cart
        </Button>
      </CardContent>
    </Card>
  );
}
