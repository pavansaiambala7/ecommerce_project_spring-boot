import { useNavigate, useSearchParams } from 'react-router-dom';
import { useCatalog } from '../hooks/useCatalog';

export default function CategoryNav() {
  const { categories } = useCatalog();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const active = searchParams.get('category');

  function select(categoryId) {
    // Category lives in the URL so the view is shareable and the back button
    // behaves, rather than being hidden in component state.
    navigate(categoryId ? `/?category=${categoryId}` : '/');
  }

  if (categories.length === 0) return null;

  return (
    <nav className="category-nav" aria-label="Product categories">
      <button type="button" data-active={!active} onClick={() => select(null)}>
        All
      </button>
      {categories.map((category) => (
        <button
          key={category.id}
          type="button"
          data-active={active === String(category.id)}
          onClick={() => select(category.id)}
        >
          {category.name}
        </button>
      ))}
    </nav>
  );
}
