export function formatPrice(value) {
  const amount = Number(value ?? 0);
  return `$${amount.toFixed(2)}`;
}

export function formatDate(value) {
  if (!value) return '';
  // The API sends ISO-8601 local date-times with no zone, which browsers parse
  // as local time. That is fine for display purposes here.
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? ''
    : date.toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' });
}
