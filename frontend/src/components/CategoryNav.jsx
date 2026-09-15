import { useNavigate, useSearchParams } from 'react-router-dom';
import { useCategories } from '../hooks/useCatalog';

export default function CategoryNav() {
  const { categories } = useCategories();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const active = searchParams.get('categoryId');

  function select(categoryId) {
    // Selecting a department clears the search text and paging: a shopper
    // moving to Electronics does not expect their last query still applied.
    navigate(categoryId ? `/?categoryId=${categoryId}` : '/');
  }

  if (categories.length === 0) return null;

  return (
    <nav className="category-nav" aria-label="Departments">
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
