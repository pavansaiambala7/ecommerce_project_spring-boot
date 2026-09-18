import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { browseLink, useCategoryTree } from '../hooks/useCatalog';
import DepartmentDrawer from './DepartmentDrawer';

/**
 * The bar under the search box: the full department menu, today's deals, and
 * the departments marked as featured. Everything else is one click away in the
 * menu, so the bar stays a single line however large the catalogue grows.
 */
export default function CategoryNav() {
  const { tree } = useCategoryTree();
  const [searchParams] = useSearchParams();
  const [menuOpen, setMenuOpen] = useState(false);
  const active = searchParams.get('categoryId');
  const onDeals = searchParams.get('sort') === 'discount' && !active;

  return (
    <>
      <nav className="category-nav" aria-label="Departments">
        <button type="button" className="nav-all" onClick={() => setMenuOpen(true)}>
          <span aria-hidden="true">☰</span> All
        </button>
        <Link to={browseLink({ minDiscount: 10, sort: 'discount' })} data-active={onDeals}>
          Today&apos;s Deals
        </Link>
        {tree
          .filter((department) => department.featured)
          .map((department) => (
            <Link
              key={department.id}
              to={browseLink({ categoryId: department.id })}
              data-active={active === String(department.id)}
            >
              {department.name}
            </Link>
          ))}
      </nav>
      <DepartmentDrawer open={menuOpen} onClose={() => setMenuOpen(false)} tree={tree} />
    </>
  );
}
