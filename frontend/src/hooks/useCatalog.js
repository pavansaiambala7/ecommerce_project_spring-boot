import { useEffect, useState } from 'react';
import { api } from '../api/client';

// The backend exposes no /api/categories endpoint - categories only exist
// nested inside each product - so the category list is derived here from the
// full catalogue. One request serves both the grid and the nav, cached at
// module level so mounting several consumers doesn't refetch.
let catalogPromise = null;

function loadCatalog() {
  if (!catalogPromise) {
    catalogPromise = api.get('/api/products', { auth: false }).catch((error) => {
      catalogPromise = null; // let a later mount retry instead of caching the failure
      throw error;
    });
  }
  return catalogPromise;
}

export function useCatalog() {
  const [products, setProducts] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let active = true;
    loadCatalog()
      .then((data) => active && setProducts(data))
      .catch((err) => active && setError(err));
    return () => {
      active = false;
    };
  }, []);

  const categories = products
    ? [...new Map(products.filter((p) => p.category).map((p) => [p.category.id, p.category])).values()].sort(
        (a, b) => a.name.localeCompare(b.name),
      )
    : [];

  return { products, categories, error, loading: !products && !error };
}
