import { useEffect, useState } from 'react';
import { useCategories } from '../hooks/useCatalog';

/**
 * Category, price and availability filters.
 *
 * <p>Everything here maps to a URL parameter rather than component state, so a
 * filtered view can be linked, bookmarked and navigated back to.
 */
export default function FilterSidebar({ params, onChange }) {
  const { categories } = useCategories();
  const [minPrice, setMinPrice] = useState(params.minPrice ?? '');
  const [maxPrice, setMaxPrice] = useState(params.maxPrice ?? '');

  // Keep the inputs in step when the URL changes from elsewhere, such as the
  // back button or a category click in the top nav.
  useEffect(() => {
    setMinPrice(params.minPrice ?? '');
    setMaxPrice(params.maxPrice ?? '');
  }, [params.minPrice, params.maxPrice]);

  function applyPrice(event) {
    event.preventDefault();
    onChange({ minPrice: minPrice || null, maxPrice: maxPrice || null, page: 0 });
  }

  return (
    <aside className="filters">
      <section className="filter-block">
        <h3>Department</h3>
        <ul>
          <li>
            <button
              type="button"
              className={!params.categoryId ? 'filter-active' : ''}
              onClick={() => onChange({ categoryId: null, page: 0 })}
            >
              All departments
            </button>
          </li>
          {categories.map((category) => (
            <li key={category.id}>
              <button
                type="button"
                className={String(params.categoryId) === String(category.id) ? 'filter-active' : ''}
                onClick={() => onChange({ categoryId: category.id, page: 0 })}
              >
                {category.name}
              </button>
            </li>
          ))}
        </ul>
      </section>

      <section className="filter-block">
        <h3>Price</h3>
        <form className="price-range" onSubmit={applyPrice}>
          <input
            type="number"
            min="0"
            step="0.01"
            placeholder="Min"
            value={minPrice}
            onChange={(event) => setMinPrice(event.target.value)}
            aria-label="Minimum price"
          />
          <span>to</span>
          <input
            type="number"
            min="0"
            step="0.01"
            placeholder="Max"
            value={maxPrice}
            onChange={(event) => setMaxPrice(event.target.value)}
            aria-label="Maximum price"
          />
          <button type="submit" className="btn-plain">
            Go
          </button>
        </form>
      </section>

      <section className="filter-block">
        <h3>Availability</h3>
        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={Boolean(params.inStockOnly)}
            onChange={(event) => onChange({ inStockOnly: event.target.checked || null, page: 0 })}
          />
          In stock only
        </label>
      </section>
    </aside>
  );
}
