import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Container from '@mui/material/Container';
import Divider from '@mui/material/Divider';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
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
  if (loading) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', py: 10 }}>
        <CircularProgress />
      </Box>
    );
  }
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return children;
}

function Footer() {
  return (
    <Box component="footer" sx={{ mt: 6, bgcolor: 'primary.dark', color: 'common.white' }}>
      <Container maxWidth="xl" sx={{ py: 4, textAlign: 'center' }}>
        <Typography variant="h6" gutterBottom>
          ShopKart
        </Typography>
        <Divider sx={{ borderColor: 'rgba(255,255,255,0.2)', my: 2 }} />
        <Typography variant="body2" sx={{ opacity: 0.85 }}>
          A demonstration store. Product photos from{' '}
          <Link href="https://dummyjson.com" target="_blank" rel="noreferrer" color="inherit" underline="always">
            DummyJSON
          </Link>{' '}
          and{' '}
          <Link href="https://stocksnap.io" target="_blank" rel="noreferrer" color="inherit" underline="always">
            StockSnap
          </Link>{' '}
          (CC0).
        </Typography>
      </Container>
    </Box>
  );
}

export default function App() {
  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
      <Header />
      <CategoryNav />

      <Box component="main" sx={{ flex: 1 }}>
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
      </Box>

      <Footer />
      <ChatWidget />
    </Box>
  );
}
