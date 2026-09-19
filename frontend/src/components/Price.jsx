import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { discountOf, formatPrice, priceParts } from '../utils/format';

const SIZES = {
  sm: { amount: 18, sup: 11 },
  md: { amount: 22, sup: 13 },
  lg: { amount: 32, sup: 18 },
};

/**
 * A price the way Indian marketplaces print it: the discount in red, the
 * selling price with a small rupee sign, and the MRP struck through beneath.
 * The MRP is only shown when there is a real discount from it.
 */
export default function Price({ product, size = 'md' }) {
  const discount = discountOf(product);
  const { whole, paise } = priceParts(product.price);
  const scale = SIZES[size] ?? SIZES.md;
  const sup = { fontSize: scale.sup, top: '-0.6em', position: 'relative' };

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.75, flexWrap: 'wrap' }}>
        {discount > 0 && (
          <Typography component="span" sx={{ color: 'error.main', fontSize: scale.sup + 2, fontWeight: 500 }}>
            -{discount}%
          </Typography>
        )}
        <Typography component="span" sx={{ fontSize: scale.amount, fontWeight: 500, lineHeight: 1.1 }}>
          <Box component="sup" sx={sup}>
            &#8377;
          </Box>
          {whole}
          {paise && (
            <Box component="sup" sx={sup}>
              {paise}
            </Box>
          )}
        </Typography>
      </Box>
      {discount > 0 && (
        <Typography variant="caption" color="text.secondary">
          M.R.P.: <s>{formatPrice(product.mrp)}</s>
        </Typography>
      )}
    </Box>
  );
}
