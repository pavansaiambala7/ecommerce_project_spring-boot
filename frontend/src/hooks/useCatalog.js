import { useEffect, useState } from 'react';
import { api } from '../api/client';

// Categories come from their own endpoint now. This used to fetch the entire
// product list and derive the category names in JavaScript, which quietly
// became a multi-megabyte download as the catalogue grew.
let categoriesPromise = null;

function loadCategories() {
  if (!categoriesPromise) {
    categoriesPromise = api.get('/api/categories', { auth: false }).catch((error) => {
      categoriesPromise = null; // let a later mount retry instead of caching the failure
      throw error;
    });
  }
  return categoriesPromise;
}

export function useCategories() {
  const [categories, setCategories] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    let active = true;
    loadCategories()
      .then((data) => active && setCategories(data))
      .catch((err) => active && setError(err));
    return () => {
      active = false;
    };
  }, []);

  return { categories, error };
}

/**
 * One page of products for the current filters. Filtering, sorting and paging
 * all happen in the database - the browser only ever holds one page.
 */
export function useProductSearch(params) {
  const [page, setPage] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '' && value !== false) {
      query.set(key, value);
    }
  });
  const queryString = query.toString();

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    api
      .get(`/api/products/search?${queryString}`, { auth: false })
      .then((data) => active && setPage(data))
      .catch((err) => active && setError(err))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [queryString]);

  return { page, error, loading };
}
