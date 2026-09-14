import { useSearchParams } from 'react-router-dom';
import ProductCard from '../components/ProductCard';
import { useCatalog } from '../hooks/useCatalog';

export default function HomePage() {
  const { products, categories, loading, error } = useCatalog();
  const [searchParams] = useSearchParams();
  const categoryId = searchParams.get('category');

  if (loading) return <div className="page-status">Loading products…</div>;
  if (error) return <div className="page-status">Could not load products: {error.message}</div>;

  const visible = categoryId
    ? products.filter((product) => String(product.category?.id) === categoryId)
    : products;

  const heading = categoryId
    ? categories.find((category) => String(category.id) === categoryId)?.name ?? 'Products'
    : 'All products';

  return (
    <>
      <h1 className="section-title">
        {heading} <span style={{ color: 'var(--muted)', fontSize: 14 }}>({visible.length})</span>
      </h1>
      {visible.length === 0 ? (
        <div className="panel">No products in this category yet.</div>
      ) : (
        <div className="product-grid">
          {visible.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      )}
    </>
  );
}
