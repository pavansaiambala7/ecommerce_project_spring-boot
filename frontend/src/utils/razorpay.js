/**
 * Loads Razorpay's checkout script on demand.
 *
 * <p>Not in index.html: most visitors never reach checkout, and a payment
 * provider's script on every page is both a needless third-party request and a
 * wider surface than the page needs. Resolves false rather than throwing so the
 * caller can fall back to cash on delivery.
 */
let loading = null;

export function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve(true);
  if (loading) return loading;

  loading = new Promise((resolve) => {
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.onload = () => resolve(true);
    script.onerror = () => {
      loading = null; // let a later attempt retry
      resolve(false);
    };
    document.body.appendChild(script);
  });

  return loading;
}
