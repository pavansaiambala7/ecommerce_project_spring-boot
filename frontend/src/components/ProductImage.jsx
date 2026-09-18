import { useEffect, useState } from 'react';

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
export default function ProductImage({ src, alt, className = '', loading = 'lazy' }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);

  const isPlaceholder = !src || src.includes('placehold.co');
  if (isPlaceholder || failed) {
    const { colour, label } = isPlaceholder && src ? parsePlaceholder(src) : { colour: '#565959', label: '' };
    return (
      <div className={`img-fallback ${className}`} style={{ '--tile': colour }} role="img" aria-label={alt}>
        <svg viewBox="0 0 48 48" width="44" height="44" aria-hidden="true">
          <path
            d="M10 16h28l-2.5 24h-23L10 16Z M18 16v-3a6 6 0 0 1 12 0v3"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.6"
            strokeLinejoin="round"
          />
        </svg>
        <span>{label || alt}</span>
      </div>
    );
  }

  return <img src={src} alt={alt} loading={loading} className={className} onError={() => setFailed(true)} />;
}
