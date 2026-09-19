import { NavLink, Navigate, Outlet } from 'react-router-dom';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Container from '@mui/material/Container';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import CategoryOutlinedIcon from '@mui/icons-material/CategoryOutlined';
import CloudUploadOutlinedIcon from '@mui/icons-material/CloudUploadOutlined';
import DashboardOutlinedIcon from '@mui/icons-material/DashboardOutlined';
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined';
import PeopleOutlineIcon from '@mui/icons-material/PeopleOutline';
import { useAuth } from '../../context/AuthContext';

const LINKS = [
  { to: '/admin', end: true, label: 'Overview', icon: <DashboardOutlinedIcon /> },
  { to: '/admin/products', label: 'Products', icon: <Inventory2OutlinedIcon /> },
  { to: '/admin/catalogue', label: 'Catalogue import', icon: <CloudUploadOutlinedIcon /> },
  { to: '/admin/categories', label: 'Departments', icon: <CategoryOutlinedIcon /> },
  { to: '/admin/customers', label: 'Customers', icon: <PeopleOutlineIcon /> },
];

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

  if (loading) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', py: 10 }}>
        <CircularProgress />
      </Box>
    );
  }
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (!isAdmin) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <Typography variant="h2" gutterBottom>
            Not available
          </Typography>
          <Typography variant="body2" color="text.secondary">
            This area is for store administrators.
          </Typography>
        </Paper>
      </Container>
    );
  }

  return (
    <Container maxWidth="xl" sx={{ py: 3 }}>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '220px 1fr' }, gap: 3 }}>
        <Paper sx={{ alignSelf: 'start', overflow: 'hidden' }}>
          <List disablePadding>
            {LINKS.map((link) => (
              <ListItemButton
                key={link.to}
                component={NavLink}
                to={link.to}
                end={link.end}
                sx={{
                  '&.active': {
                    bgcolor: 'action.selected',
                    borderLeft: 3,
                    borderColor: 'primary.main',
                    '& .MuiListItemText-primary': { fontWeight: 700 },
                  },
                }}
              >
                <ListItemIcon sx={{ minWidth: 38 }}>{link.icon}</ListItemIcon>
                <ListItemText primary={link.label} primaryTypographyProps={{ variant: 'body2' }} />
              </ListItemButton>
            ))}
          </List>
        </Paper>

        <Box sx={{ minWidth: 0 }}>
          <Outlet />
        </Box>
      </Box>
    </Container>
  );
}
