import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api } from '../api/client';
import { useAuth } from './AuthContext';

const CartContext = createContext(null);

export function CartProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const [cart, setCart] = useState(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    if (!isAuthenticated) {
      setCart(null);
      return;
    }
    try {
      setCart(await api.get('/api/cart'));
    } catch {
      setCart(null);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  // Every cart mutation returns the full refreshed cart, so each of these
  // stores the response directly instead of refetching.
  const mutate = useCallback(async (operation) => {
    setBusy(true);
    try {
      setCart(await operation());
    } finally {
      setBusy(false);
    }
  }, []);

  const value = {
    cart,
    busy,
    itemCount: cart?.itemCount ?? 0,
    refresh,
    addItem: (productId, quantity = 1) =>
      mutate(() => api.post('/api/cart/items', { productId, quantity })),
    updateQuantity: (productId, quantity) =>
      mutate(() => api.put(`/api/cart/items/${productId}`, { quantity })),
    removeItem: (productId) => mutate(() => api.del(`/api/cart/items/${productId}`)),
    clear: () => mutate(async () => {
      await api.del('/api/cart');
      return null;
    }),
    checkout: async (idempotencyKey) => {
      // The key makes a double-click or a retried request produce one order
      // rather than two.
      const order = await api.post('/api/cart/checkout', undefined,
        idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : undefined);
      setCart(null);
      return order;
    },
  };

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) throw new Error('useCart must be used inside CartProvider');
  return context;
}
