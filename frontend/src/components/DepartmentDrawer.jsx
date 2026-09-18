import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { browseLink } from '../hooks/useCatalog';

/**
 * Amazon's slide-in "All" menu. A department with sub-departments opens a
 * second panel rather than expanding in place, which keeps a long menu readable
 * on a phone.
 */
export default function DepartmentDrawer({ open, onClose, tree }) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [drilled, setDrilled] = useState(null);

  useEffect(() => {
    if (!open) {
      setDrilled(null);
      return undefined;
    }
    const onKey = (event) => event.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  function browse(params) {
    onClose();
    navigate(browseLink(params));
  }

  if (!open) return null;

  const formatCount = (n) => new Intl.NumberFormat('en-IN').format(n);

  return (
    <div className="drawer-backdrop" onClick={onClose}>
      <aside
        className="drawer"
        role="dialog"
        aria-label="Shop by department"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="drawer-head">
          <span aria-hidden="true">◉</span> Hello, {user ? user.username : 'sign in'}
        </header>
        <button type="button" className="drawer-close" onClick={onClose} aria-label="Close menu">
          ×
        </button>

        {drilled ? (
          <div className="drawer-body">
            <button type="button" className="drawer-back" onClick={() => setDrilled(null)}>
              ← Main menu
            </button>
            <h2>{drilled.name}</h2>
            <button type="button" className="drawer-item" onClick={() => browse({ categoryId: drilled.id })}>
              All {drilled.name} <span>{formatCount(drilled.productCount)}</span>
            </button>
            {drilled.children.map((child) => (
              <button
                key={child.id}
                type="button"
                className="drawer-item"
                onClick={() => browse({ categoryId: child.id })}
              >
                {child.name} <span>{formatCount(child.productCount)}</span>
              </button>
            ))}
          </div>
        ) : (
          <div className="drawer-body">
            <h2>Trending</h2>
            <button type="button" className="drawer-item" onClick={() => browse({ minDiscount: 10, sort: 'discount' })}>
              Today&apos;s Deals
            </button>
            <button type="button" className="drawer-item" onClick={() => browse({ sort: 'rating' })}>
              Top rated
            </button>

            <h2>Shop by Department</h2>
            {tree.map((department) =>
              department.children.length > 0 ? (
                <button
                  key={department.id}
                  type="button"
                  className="drawer-item"
                  onClick={() => setDrilled(department)}
                >
                  {department.name} <span aria-hidden="true">›</span>
                </button>
              ) : (
                <button
                  key={department.id}
                  type="button"
                  className="drawer-item"
                  onClick={() => browse({ categoryId: department.id })}
                >
                  {department.name} <span>{formatCount(department.productCount)}</span>
                </button>
              ),
            )}
          </div>
        )}
      </aside>
    </div>
  );
}
