import { useSearchParams } from 'react-router-dom';
import FilterSidebar from '../components/FilterSidebar';
import ProductCard from '../components/ProductCard';
import { findInTree, useCategoryTree, useProductSearch } from '../hooks/useCatalog';

const SORTS = [
  { value: 'relevance', label: 'Featured' },
  { value: 'price_asc', label: 'Price: Low to High' },
  { value: 'price_desc', label: 'Price: High to Low' },
  { value: 'rating', label: 'Avg. Customer Review' },
  { value: 'discount', label: 'Discount: High to Low' },
  { value: 'name', label: 'Name: A to Z' },
];

export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { tree } = useCategoryTree();

  const params = {
    q: searchParams.get('q') ?? '',
    categoryId: searchParams.get('categoryId'),
    minPrice: searchParams.get('minPrice'),
    maxPrice: searchParams.get('maxPrice'),
    minDiscount: searchParams.get('minDiscount'),
    inStockOnly: searchParams.get('inStockOnly') === 'true',
    sort: searchParams.get('sort') ?? 'relevance',
    page: Number(searchParams.get('page') ?? 0),
    size: 24,
  };

  const { page, loading, error } = useProductSearch(params);
  const { node: department } = findInTree(tree, params.categoryId);

  // Filters live in the URL, so every change is a navigation: shareable,
  // bookmarkable, and the back button behaves the way shoppers expect.
  function update(changes) {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => {
      if (value === null || value === undefined || value === '' || value === false) {
        next.delete(key);
      } else {
        next.set(key, value);
      }
    });
    setSearchParams(next);
    window.scrollTo({ top: 0 });
  }

  const total = page?.totalItems ?? 0;
  const from = total === 0 ? 0 : params.page * params.size + 1;
  const to = Math.min(total, (params.page + 1) * params.size);

  let heading = 'Results';
  if (params.q) heading = `“${params.q}”`;
  else if (department) heading = department.name;
  else if (params.sort === 'discount') heading = "Today's Deals";

  return (
    <div className="browse-layout">
      <FilterSidebar params={params} onChange={update} />

      <section>
        <div className="results-bar">
          <span>
            {loading
              ? 'Loading…'
              : total === 0
                ? 'No results'
                : `${from}-${to} of ${new Intl.NumberFormat('en-IN').format(total)} results`}
            {params.q && (
              <>
                {' '}
                for <strong className="results-term">“{params.q}”</strong>
              </>
            )}
          </span>

          <label>
            Sort by:{' '}
            <select value={params.sort} onChange={(event) => update({ sort: event.target.value, page: 0 })}>
              {SORTS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
        </div>

        <h1 className="results-heading">{heading}</h1>

        {error && <div className="error">Could not load products: {error.message}</div>}

        {!loading && page?.items.length === 0 && (
          <div className="panel">
            <h2 className="section-title">No products match these filters</h2>
            <p>Try removing a filter, checking the spelling, or searching for something more general.</p>
          </div>
        )}

        <div className="product-grid">
          {page?.items.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>

        {page && page.totalPages > 1 && (
          <nav className="pagination" aria-label="Pages">
            <button
              type="button"
              className="btn-plain"
              disabled={params.page <= 0}
              onClick={() => update({ page: params.page - 1 })}
            >
              ‹ Previous
            </button>
            <span>
              Page {params.page + 1} of {page.totalPages}
            </span>
            <button
              type="button"
              className="btn-plain"
              disabled={params.page + 1 >= page.totalPages}
              onClick={() => update({ page: params.page + 1 })}
            >
              Next ›
            </button>
          </nav>
        )}
      </section>
    </div>
  );
}
