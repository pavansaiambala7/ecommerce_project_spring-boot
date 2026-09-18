import { discountOf, formatPrice, priceParts } from '../utils/format';

/**
 * A price the way Indian marketplaces print it: the discount in red, the
 * selling price with a small rupee sign, and the MRP struck through beneath.
 * The MRP is only shown when there is a real discount from it.
 */
export default function Price({ product, size = 'md' }) {
  const discount = discountOf(product);
  const { whole, paise } = priceParts(product.price);

  return (
    <div className={`price-block price-${size}`}>
      <div className="price-line">
        {discount > 0 && <span className="price-discount">-{discount}%</span>}
        <span className="price-amount">
          <sup>₹</sup>
          {whole}
          {paise && <sup>{paise}</sup>}
        </span>
      </div>
      {discount > 0 && (
        <div className="price-mrp">
          M.R.P.: <s>{formatPrice(product.mrp)}</s>
        </div>
      )}
    </div>
  );
}
