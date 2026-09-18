import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api } from '../api/client';
import { useAuth } from './AuthContext';

const AddressContext = createContext(null);

/**
 * The signed-in shopper's address book, shared by the header's "Deliver to",
 * checkout and the addresses page, so a change in one shows everywhere without
 * each refetching.
 */
export function AddressProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const [addresses, setAddresses] = useState([]);
  const [loaded, setLoaded] = useState(false);

  const refresh = useCallback(async () => {
    if (!isAuthenticated) {
      setAddresses([]);
      setLoaded(true);
      return [];
    }
    try {
      const list = await api.get('/api/addresses');
      setAddresses(list ?? []);
      return list ?? [];
    } catch {
      setAddresses([]);
      return [];
    } finally {
      setLoaded(true);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    setLoaded(false);
    refresh();
  }, [refresh]);

  const save = useCallback(
    async (address, id) => {
      const saved = id ? await api.put(`/api/addresses/${id}`, address) : await api.post('/api/addresses', address);
      await refresh();
      return saved;
    },
    [refresh],
  );

  const remove = useCallback(
    async (id) => {
      await api.del(`/api/addresses/${id}`);
      await refresh();
    },
    [refresh],
  );

  const makeDefault = useCallback(
    async (id) => {
      await api.post(`/api/addresses/${id}/default`);
      await refresh();
    },
    [refresh],
  );

  const value = {
    addresses,
    loaded,
    defaultAddress: addresses.find((a) => a.isDefault) ?? addresses[0] ?? null,
    refresh,
    save,
    remove,
    makeDefault,
  };

  return <AddressContext.Provider value={value}>{children}</AddressContext.Provider>;
}

export function useAddresses() {
  const context = useContext(AddressContext);
  if (!context) throw new Error('useAddresses must be used inside AddressProvider');
  return context;
}
