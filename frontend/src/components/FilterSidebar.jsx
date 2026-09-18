import { useEffect, useState } from 'react';
import { findInTree, useCategoryTree } from '../hooks/useCatalog';

const PRICE_RANGES = [
  { label: 'Under ₹1,000', min: null, max: 1000 },
  { label: '₹1,000 – ₹5,000', min: 1000, max: 5000 },
  { label: '₹5,000 – ₹10,000', min: 5000, max: 10000 },
  { label: '₹10,000 – ₹20,000', min: 10000, max: 20000 },
  { label: 'Over ₹20,000', min: 20000, max: null },
];

const DISCOUNTS = [10, 25, 50, 70];

/**
 * Department, price, discount and availability filters.
 *
 * <p>Everything maps to a URL parameter rather than component state, so a
 * filtered view can be linked, bookmarked and navigated back to.
 */
export default function FilterSidebar({ params, onChange }) {
  const { tree } = useCategoryTree();
  const [minPrice, setMinPrice] = useState(params.minPrice ?? '');
  const [maxPrice, setMaxPrice] = useState(params.maxPrice ?? '');

  // Keep the inputs in step when the URL changes from elsewhere, such as the
  // back button or a department link in the top bar.
  useEffect(() => {
    setMinPrice(params.minPrice ?? '');
    setMaxPrice(params.maxPrice ?? '');
  }, [params.minPrice, params.maxPrice]);

  function applyPrice(event) {
    event.preventDefault();
    onChange({ minPrice: minPrice || null, maxPrice: maxPrice || null, page: 0 });
  }

  const { node, parent } = findInTree(tree, params.categoryId);
  // Inside a department, show where you are and what is below it, as Amazon
  // does, rather than the full list of thirty departments.
  const top = parent ?? node;

  const isRange = (range) =>
    String(params.minPrice ?? '') === String(range.min ?? '') &&
    String(params.maxPrice ?? '') === String(range.max ?? '');

  return (
    <aside className="filters">
      <section className="filter-block">
        <h3>Department</h3>
        <ul>
          {top ? (
            <>
              <li>
                <button type="button" onClick={() => onChange({ categoryId: null, page: 0 })}>
                  ‹ Any Department
                </button>
              </li>
              <li>
                <button
                  type="button"
                  className={node === top ? 'filter-active' : ''}
                  onClick={() => onChange({ categoryId: top.id, page: 0 })}
                >
                  {top.name}
                </button>
              </li>
              {top.children.map((child) => (
                <li key={child.id} className="filter-child">
                  <button
                    type="button"
                    className={node?.id === child.id ? 'filter-active' : ''}
                    onClick={() => onChange({ categoryId: child.id, page: 0 })}
                  >
                    {child.name}
                  </button>
                </li>
              ))}
            </>
          ) : (
            tree.map((department) => (
              <li key={department.id}>
                <button type="button" onClick={() => onChange({ categoryId: department.id, page: 0 })}>
                  {department.name}
                </button>
              </li>
            ))
          )}
        </ul>
      </section>

      <section className="filter-block">
        <h3>Price</h3>
        <ul className="plain-list">
          {PRICE_RANGES.map((range) => (
            <li key={range.label}>
              <button
                type="button"
                className={isRange(range) ? 'filter-active' : ''}
                onClick={() => onChange({ minPrice: range.min, maxPrice: range.max, page: 0 })}
              >
                {range.label}
              </button>
            </li>
          ))}
        </ul>
        <form className="price-range" onSubmit={applyPrice}>
          <input
            type="number"
            min="0"
            placeholder="₹ Min"
            value={minPrice}
            onChange={(event) => setMinPrice(event.target.value)}
            aria-label="Minimum price"
          />
          <input
            type="number"
            min="0"
            placeholder="₹ Max"
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
        <h3>Discount</h3>
        <ul className="plain-list">
          {DISCOUNTS.map((discount) => (
            <li key={discount}>
              <button
                type="button"
                className={String(params.minDiscount) === String(discount) ? 'filter-active' : ''}
                onClick={() =>
                  onChange({
                    minDiscount: String(params.minDiscount) === String(discount) ? null : discount,
                    page: 0,
                  })
                }
              >
                {discount}% off or more
              </button>
            </li>
          ))}
        </ul>
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
