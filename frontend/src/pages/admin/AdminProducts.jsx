import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { formatPrice } from '../../utils/format';

const EMPTY = {
  name: '', description: '', image: '', price: '', quantity: '', weight: '', categoryId: '',
};

export default function AdminProducts() {
  const [page, setPage] = useState(null);
  const [categories, setCategories] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [pageNo, setPageNo] = useState(0);

  const load = useCallback(() => {
    api.get(`/api/products/search?size=20&sort=name&page=${pageNo}`)
      .then(setPage)
      .catch(setError);
  }, [pageNo]);

  useEffect(load, [load]);
  useEffect(() => {
    api.get('/api/categories/all').then(setCategories).catch(() => setCategories([]));
  }, []);

  function edit(product) {
    setEditingId(product.id);
    setForm({
      name: product.name ?? '',
      description: product.description ?? '',
      image: product.image ?? '',
      price: product.price ?? '',
      quantity: product.quantity ?? '',
      weight: product.weight ?? '',
      categoryId: product.category?.id ?? '',
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function reset() {
    setEditingId(null);
    setForm(EMPTY);
    setError(null);
  }

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    const body = {
      ...form,
      price: Number(form.price),
      quantity: Number(form.quantity || 0),
      weight: Number(form.weight || 0),
      categoryId: Number(form.categoryId),
    };
    try {
      if (editingId) await api.put(`/api/products/${editingId}`, body);
      else await api.post('/api/products', body);
      reset();
      load();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function remove(product) {
    // Deleting a product is not reversible from this screen, and the row may
    // already sit in someone's cart or order history.
    if (!window.confirm(`Delete “${product.name}”? This cannot be undone.`)) return;
    setBusy(true);
    setError(null);
    try {
      await api.del(`/api/products/${product.id}`);
      load();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <h1 className="section-title">{editingId ? 'Edit product' : 'Add a product'}</h1>

      <form className="panel admin-form" onSubmit={submit}>
        {error && (
          <div className="error">
            {error.message}
            {error.errors && Object.entries(error.errors).map(([field, message]) => (
              <div key={field}>{field}: {message}</div>
            ))}
          </div>
        )}

        <div className="admin-form-grid">
          <label>Name
            <input required maxLength={255} value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </label>
          <label>Department
            <select required value={form.categoryId}
              onChange={(e) => setForm({ ...form, categoryId: e.target.value })}>
              <option value="">Choose…</option>
              {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </label>
          <label>Price
            <input required type="number" min="0" step="0.01" value={form.price}
              onChange={(e) => setForm({ ...form, price: e.target.value })} />
          </label>
          <label>Stock
            <input type="number" min="0" value={form.quantity}
              onChange={(e) => setForm({ ...form, quantity: e.target.value })} />
          </label>
          <label>Weight (g)
            <input type="number" min="0" value={form.weight}
              onChange={(e) => setForm({ ...form, weight: e.target.value })} />
          </label>
          <label>Image URL
            <input maxLength={255} value={form.image}
              onChange={(e) => setForm({ ...form, image: e.target.value })} />
          </label>
        </div>

        <label>Description
          <input maxLength={255} value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </label>

        <div className="admin-actions">
          <button type="submit" className="btn" disabled={busy}>
            {editingId ? 'Save changes' : 'Add product'}
          </button>
          {editingId && (
            <button type="button" className="btn-plain" onClick={reset}>Cancel</button>
          )}
        </div>
      </form>

      <h2 className="section-title">Catalogue</h2>
      <div className="panel table-scroll">
        <table className="admin-table">
          <thead>
            <tr><th>Product</th><th>Department</th><th>Price</th><th>Stock</th><th></th></tr>
          </thead>
          <tbody>
            {page?.items.map((product) => (
              <tr key={product.id}>
                <td>{product.name}</td>
                <td>{product.category?.name ?? '—'}</td>
                <td>{formatPrice(product.price)}</td>
                <td>{product.quantity}</td>
                <td className="admin-row-actions">
                  <button type="button" className="btn-plain" onClick={() => edit(product)}>Edit</button>
                  <button type="button" className="btn-plain danger" disabled={busy}
                    onClick={() => remove(product)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {page && page.totalPages > 1 && (
        <nav className="pagination">
          <button type="button" className="btn-plain" disabled={pageNo <= 0}
            onClick={() => setPageNo(pageNo - 1)}>Previous</button>
          <span>Page {pageNo + 1} of {page.totalPages}</span>
          <button type="button" className="btn-plain" disabled={pageNo + 1 >= page.totalPages}
            onClick={() => setPageNo(pageNo + 1)}>Next</button>
        </nav>
      )}
    </>
  );
}
