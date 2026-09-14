import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import ProductCard from '../components/ProductCard';

export default function SearchPage() {
  const [searchParams] = useSearchParams();
  const query = searchParams.get('q') ?? '';
  const [results, setResults] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!query) {
      setResults([]);
      return undefined;
    }
    let active = true;
    setResults(null);
    setError(null);
    // Semantic search over Gemini embeddings stored in pgvector, so results are
    // ranked by meaning rather than keyword match. Public endpoint, 30 req/min.
    api
      .get(`/api/search?q=${encodeURIComponent(query)}&limit=20`, { auth: false })
      .then((data) => active && setResults(data))
      .catch((err) => active && setError(err));
    return () => {
      active = false;
    };
  }, [query]);

  if (!query) return <div className="page-status">Type something in the search bar above.</div>;
  if (error) return <div className="page-status">Search failed: {error.message}</div>;
  if (!results) return <div className="page-status">Searching…</div>;

  return (
    <>
      <h1 className="section-title">
        Results for “{query}”{' '}
        <span style={{ color: 'var(--muted)', fontSize: 14 }}>({results.length})</span>
      </h1>

      {results.length === 0 ? (
        <div className="panel">No matches. Try different words.</div>
      ) : (
        <div className="product-grid">
          {results.map((result) => (
            <div key={result.product.id}>
              <ProductCard product={result.product} />
              <div style={{ color: 'var(--muted)', fontSize: 12, padding: '4px 14px' }}>
                Relevance {(result.relevanceScore * 100).toFixed(0)}%
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
