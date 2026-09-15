import { NavLink, Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

/**
 * Shell for the administration area.
 *
 * <p>Gating here is a convenience so an ordinary shopper never sees a broken
 * screen; it is not the security boundary. Every admin action is a separate
 * call to /api/**, where the server checks ROLE_ADMIN. Editing this component
 * in the browser grants nothing.
 */
export default function AdminLayout() {
  const { isAuthenticated, isAdmin, loading } = useAuth();

  if (loading) return <div className="page-status">Loading…</div>;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (!isAdmin) {
    return (
      <div className="panel">
        <h1 className="section-title">Not available</h1>
        <p>This area is for store administrators.</p>
      </div>
    );
  }

  return (
    <div className="admin-layout">
      <nav className="admin-nav">
        <NavLink to="/admin" end className={({ isActive }) => (isActive ? 'admin-active' : '')}>
          Overview
        </NavLink>
        <NavLink to="/admin/products" className={({ isActive }) => (isActive ? 'admin-active' : '')}>
          Products
        </NavLink>
        <NavLink to="/admin/categories" className={({ isActive }) => (isActive ? 'admin-active' : '')}>
          Departments
        </NavLink>
        <NavLink to="/admin/customers" className={({ isActive }) => (isActive ? 'admin-active' : '')}>
          Customers
        </NavLink>
      </nav>
      <section>
        <Outlet />
      </section>
    </div>
  );
}
