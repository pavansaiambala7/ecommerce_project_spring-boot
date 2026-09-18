import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import Header from './components/Header';
import CategoryNav from './components/CategoryNav';
import ChatWidget from './components/ChatWidget';
import HomeLanding from './pages/HomeLanding';
import SearchPage from './pages/SearchPage';
import ProductDetailPage from './pages/ProductDetailPage';
import CartPage from './pages/CartPage';
import CheckoutPage from './pages/CheckoutPage';
import OrdersPage from './pages/OrdersPage';
import AddressesPage from './pages/AddressesPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import AdminLayout from './pages/admin/AdminLayout';
import AdminOverview from './pages/admin/AdminOverview';
import AdminProducts from './pages/admin/AdminProducts';
import AdminCatalogue from './pages/admin/AdminCatalogue';
import AdminCategories from './pages/admin/AdminCategories';
import AdminCustomers from './pages/admin/AdminCustomers';
import { useAuth } from './context/AuthContext';

/** Older browse URLs; keep bookmarks working. */
function ToResults() {
  const { search } = useLocation();
  return <Navigate to={`/s${search}`} replace />;
}

function ProtectedRoute({ children }) {
  const { isAuthenticated, loading } = useAuth();
  if (loading) return <div className="page-status">Loading…</div>;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return children;
}

export default function App() {
  return (
    <>
      <Header />
      <CategoryNav />
      <main className="app-main">
        <Routes>
          <Route path="/" element={<HomeLanding />} />
          <Route path="/s" element={<SearchPage />} />
          <Route path="/products" element={<ToResults />} />
          <Route path="/search" element={<ToResults />} />
          <Route path="/product/:id" element={<ProductDetailPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/cart" element={<ProtectedRoute><CartPage /></ProtectedRoute>} />
          <Route path="/checkout" element={<ProtectedRoute><CheckoutPage /></ProtectedRoute>} />
          <Route path="/orders" element={<ProtectedRoute><OrdersPage /></ProtectedRoute>} />
          <Route path="/account/addresses" element={<ProtectedRoute><AddressesPage /></ProtectedRoute>} />
          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<AdminOverview />} />
            <Route path="products" element={<AdminProducts />} />
            <Route path="catalogue" element={<AdminCatalogue />} />
            <Route path="categories" element={<AdminCategories />} />
            <Route path="customers" element={<AdminCustomers />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      <footer className="site-footer">
        <button type="button" className="back-to-top" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}>
          Back to top
        </button>
        <div className="footer-note">
          ShopKart is a demonstration store. Product photos:{' '}
          <a href="https://dummyjson.com" target="_blank" rel="noreferrer">DummyJSON</a> and{' '}
          <a href="https://stocksnap.io" target="_blank" rel="noreferrer">StockSnap</a> (CC0).
        </div>
      </footer>
      <ChatWidget />
    </>
  );
}
