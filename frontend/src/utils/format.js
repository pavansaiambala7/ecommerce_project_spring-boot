// Indian digit grouping, not the Western one: ₹1,04,900 rather than ₹104,900.
// Intl handles this correctly for the en-IN locale, and getting it wrong is
// the kind of detail an Indian shopper notices immediately.
const RUPEES = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  // Indian retail quotes whole rupees. Paise appear on invoices, not price tags.
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
});

const RUPEES_WITH_PAISE = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatPrice(value) {
  const amount = Number(value ?? 0);
  // Cart and order totals can carry paise once quantities multiply, and
  // hiding them would make a total look like it disagrees with its lines.
  return Number.isInteger(amount) ? RUPEES.format(amount) : RUPEES_WITH_PAISE.format(amount);
}

/**
 * The pieces of a price tag, for rendering the rupee sign and paise smaller
 * than the amount the way Indian marketplaces do: <sup>₹</sup>1,299.
 */
export function priceParts(value) {
  const amount = Number(value ?? 0);
  const whole = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 }).format(Math.trunc(amount));
  const paise = Math.round((amount - Math.trunc(amount)) * 100);
  return { whole, paise: paise > 0 ? String(paise).padStart(2, '0') : null };
}

/** Whole-number percentage off the MRP, or 0 when not discounted. */
export function discountOf(product) {
  if (product?.discountPercent != null) return product.discountPercent;
  const mrp = Number(product?.mrp);
  const price = Number(product?.price);
  return mrp > price && mrp > 0 ? Math.round(((mrp - price) * 100) / mrp) : 0;
}

export function formatDate(value) {
  if (!value) return '';
  // The API sends ISO-8601 local date-times with no zone, which browsers parse
  // as local time. That is fine for display purposes here.
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? ''
    : date.toLocaleDateString('en-IN', { year: 'numeric', month: 'long', day: 'numeric' });
}
