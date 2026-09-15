import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import { formatPrice } from '../../utils/format';

export default function AdminOverview() {
  const [facets, setFacets] = useState(null);
  const [total, setTotal] = useState(null);
  const [outOfStock, setOutOfStock] = useState(null);

  useEffect(() => {
    api.get('/api/products/facets', { auth: false }).then(setFacets).catch(() => {});
    api.get('/api/products/search?size=1', { auth: false })
      .then((page) => setTotal(page.totalItems)).catch(() => {});
    // Everything in stock, subtracted from the total, gives what is not.
    api.get('/api/products/search?size=1&inStockOnly=true', { auth: false })
      .then((page) => setOutOfStock(page.totalItems)).catch(() => {});
  }, []);

  const unavailable = total != null && outOfStock != null ? total - outOfStock : null;

  return (
    <>
      <h1 className="section-title">Store overview</h1>

      <div className="stat-row">
        <div className="stat">
          <span>Products</span>
          <strong>{total ?? '—'}</strong>
        </div>
        <div className="stat">
          <span>Departments</span>
          <strong>{facets?.categories.length ?? '—'}</strong>
        </div>
        <div className="stat">
          <span>Out of stock</span>
          <strong>{unavailable ?? '—'}</strong>
        </div>
        <div className="stat">
          <span>Price range</span>
          <strong>
            {facets ? `${formatPrice(facets.minPrice)} – ${formatPrice(facets.maxPrice)}` : '—'}
          </strong>
        </div>
      </div>

      <div className="panel">
        <h2 style={{ fontSize: 16, marginTop: 0 }}>Products per department</h2>
        <table className="admin-table">
          <thead><tr><th>Department</th><th>Products</th></tr></thead>
          <tbody>
            {facets?.categories.map((category) => (
              <tr key={category.id}>
                <td><Link to={`/?categoryId=${category.id}`} style={{ color: 'var(--link)' }}>{category.name}</Link></td>
                <td>{category.count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
