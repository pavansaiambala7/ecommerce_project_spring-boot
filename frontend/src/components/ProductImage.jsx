import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import ShoppingBagOutlinedIcon from '@mui/icons-material/ShoppingBagOutlined';

/**
 * Reads the department colour and product type out of a placeholder URL such
 * as https://placehold.co/500x500/00897b/ffffff?text=Pressure+Cooker.
 */
function parsePlaceholder(src) {
  try {
    const url = new URL(src);
    const [, , background] = url.pathname.split('/');
    return {
      colour: /^[0-9a-f]{6}$/i.test(background ?? '') ? `#${background}` : '#565959',
      label: url.searchParams.get('text')?.replace(/\+/g, ' ') ?? '',
    };
  } catch {
    return { colour: '#565959', label: '' };
  }
}

/**
 * A product photo, or a clean tile when there is no photo to show.
 *
 * <p>Products without a verified photograph carry a placeholder URL. Rendering
 * that as an image put flat red text boxes in the grid; this draws a tinted
 * tile naming the product type instead. It also covers a real photo that fails
 * to load, so a dead image link never leaves a broken-image icon.
 */
export default function ProductImage({ src, alt, loading = 'lazy', sx }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);

  const isPlaceholder = !src || src.includes('placehold.co');
  if (isPlaceholder || failed) {
    const { colour, label } = isPlaceholder && src ? parsePlaceholder(src) : { colour: '#565959', label: '' };
    return (
      <Box
        role="img"
        aria-label={alt}
        sx={{
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 1,
          p: 2,
          textAlign: 'center',
          color: colour,
          bgcolor: `color-mix(in srgb, ${colour} 8%, #ffffff)`,
          ...sx,
        }}
      >
        <ShoppingBagOutlinedIcon sx={{ fontSize: 44, opacity: 0.7 }} />
        <Typography variant="caption" className="clamp-2" sx={{ fontWeight: 600, lineHeight: 1.3 }}>
          {label || alt}
        </Typography>
      </Box>
    );
  }

  return (
    <Box
      component="img"
      src={src}
      alt={alt}
      loading={loading}
      className="product-photo"
      onError={() => setFailed(true)}
      sx={{ width: '100%', height: '100%', objectFit: 'contain', ...sx }}
    />
  );
}
