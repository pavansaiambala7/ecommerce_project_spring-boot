import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';

export default function AdminCategories() {
  const [categories, setCategories] = useState([]);
  const [name, setName] = useState('');
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api.get('/api/categories/all').then(setCategories).catch(setError);
  }, []);

  useEffect(load, [load]);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (editingId) await api.put(`/api/categories/${editingId}`, { name });
      else await api.post('/api/categories', { name });
      setName('');
      setEditingId(null);
      load();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function remove(category) {
    setBusy(true);
    setError(null);
    try {
      await api.del(`/api/categories/${category.id}`);
      load();
    } catch (err) {
      // The server refuses to delete a department that still has products,
      // rather than orphaning them. Show that reason rather than a generic
      // failure.
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <h1 className="section-title">Departments</h1>

      <form className="panel admin-form" onSubmit={submit}>
        {error && <div className="error">{error.message}</div>}
        <div className="admin-actions">
          <input
            required
            maxLength={255}
            placeholder="Department name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            style={{ flex: 1, minWidth: 0, padding: 8, border: '1px solid var(--border)', borderRadius: 4 }}
          />
          <button type="submit" className="btn" disabled={busy}>
            {editingId ? 'Rename' : 'Add'}
          </button>
          {editingId && (
            <button type="button" className="btn-plain" onClick={() => { setEditingId(null); setName(''); }}>
              Cancel
            </button>
          )}
        </div>
      </form>

      <div className="panel table-scroll">
        <table className="admin-table">
          <thead><tr><th>Name</th><th>ID</th><th></th></tr></thead>
          <tbody>
            {categories.map((category) => (
              <tr key={category.id}>
                <td>{category.name}</td>
                <td>{category.id}</td>
                <td className="admin-row-actions">
                  <button type="button" className="btn-plain"
                    onClick={() => { setEditingId(category.id); setName(category.name); }}>
                    Rename
                  </button>
                  <button type="button" className="btn-plain danger" disabled={busy}
                    onClick={() => remove(category)}>
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
