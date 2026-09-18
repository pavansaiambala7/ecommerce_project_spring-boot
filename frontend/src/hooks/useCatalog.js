import { useEffect, useState } from 'react';
import { api } from '../api/client';

/**
 * Loads once per page load and shares the promise, so the header, the
 * department menu and the filter sidebar all mounting together cost one request.
 * A failure is not cached: the next component to mount tries again.
 */
function sharedLoader(path) {
  let promise = null;
  return () => {
    if (!promise) {
      promise = api.get(path, { auth: false }).catch((error) => {
        promise = null;
        throw error;
      });
    }
    return promise;
  };
}

const loadCategoryTree = sharedLoader('/api/categories/tree');

function useShared(loader, initial) {
  const [data, setData] = useState(initial);
  const [error, setError] = useState(null);

  useEffect(() => {
    let active = true;
    loader()
      .then((result) => active && setData(result ?? initial))
      .catch((err) => active && setError(err));
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loader]);

  return { data, error };
}

/** Departments nested under their parents, in navigation order, with product counts. */
export function useCategoryTree() {
  const { data, error } = useShared(loadCategoryTree, []);
  return { tree: data, error };
}

/** Finds a department and its parent anywhere in the tree. */
export function findInTree(tree, id) {
  const wanted = Number(id);
  for (const top of tree) {
    if (top.id === wanted) return { node: top, parent: null };
    const child = top.children.find((c) => c.id === wanted);
    if (child) return { node: child, parent: top };
  }
  return { node: null, parent: null };
}

/** The landing page's cards and deals. */
export function useStorefrontHome() {
  const [home, setHome] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let active = true;
    api
      .get('/api/storefront/home', { auth: false })
      .then((data) => active && setHome(data))
      .catch((err) => active && setError(err));
    return () => {
      active = false;
    };
  }, []);

  return { home, error };
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

/** Builds a browse link from filter values, dropping empty ones. */
export function browseLink(params) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') query.set(key, value);
  });
  const text = query.toString();
  return text ? `/s?${text}` : '/s';
}
