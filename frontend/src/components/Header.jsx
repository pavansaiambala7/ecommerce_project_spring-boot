import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import AppBar from '@mui/material/AppBar';
import Badge from '@mui/material/Badge';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import IconButton from '@mui/material/IconButton';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import ListItemIcon from '@mui/material/ListItemIcon';
import Divider from '@mui/material/Divider';
import AdminPanelSettingsOutlinedIcon from '@mui/icons-material/AdminPanelSettingsOutlined';
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined';
import LocationOnOutlinedIcon from '@mui/icons-material/LocationOnOutlined';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import ShoppingCartOutlinedIcon from '@mui/icons-material/ShoppingCartOutlined';
import StorefrontIcon from '@mui/icons-material/Storefront';
import { useAddresses } from '../context/AddressContext';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import SearchBox from './SearchBox';

/** Two stacked lines, the shape every header action in a storefront uses. */
function HeaderAction({ to, onClick, caption, label, icon, ...rest }) {
  return (
    <ButtonBase
      component={to ? RouterLink : 'button'}
      to={to}
      onClick={onClick}
      sx={{
        px: 1,
        py: 0.5,
        borderRadius: 1,
        color: 'inherit',
        textAlign: 'left',
        gap: 1,
        '&:hover': { bgcolor: 'rgba(255,255,255,0.12)' },
      }}
      {...rest}
    >
      {icon}
      <Box sx={{ display: { xs: 'none', md: 'block' }, lineHeight: 1.1 }}>
        <Typography variant="caption" sx={{ display: 'block', opacity: 0.8, fontSize: 11 }}>
          {caption}
        </Typography>
        <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
          {label}
        </Typography>
      </Box>
    </ButtonBase>
  );
}

function DeliverTo() {
  const { isAuthenticated, user } = useAuth();
  const { defaultAddress } = useAddresses();

  if (!isAuthenticated) {
    return (
      <HeaderAction
        to="/login"
        caption="Hello"
        label="Select your address"
        icon={<LocationOnOutlinedIcon fontSize="small" />}
      />
    );
  }

  return (
    <HeaderAction
      to="/account/addresses"
      caption={`Deliver to ${defaultAddress?.fullName?.split(' ')[0] ?? user.username}`}
      label={defaultAddress ? `${defaultAddress.city} ${defaultAddress.pincode}` : 'Add an address'}
      icon={<LocationOnOutlinedIcon fontSize="small" />}
    />
  );
}

export default function Header() {
  const { user, isAuthenticated, isAdmin, logout } = useAuth();
  const { itemCount } = useCart();
  const [anchor, setAnchor] = useState(null);

  return (
    <AppBar position="sticky" color="primary" enableColorOnDark>
      <Toolbar sx={{ gap: { xs: 1, md: 2 }, py: 1, minHeight: { xs: 60, md: 64 } }}>
        <Box
          component={RouterLink}
          to="/"
          aria-label="ShopKart home"
          sx={{ display: 'flex', alignItems: 'center', gap: 0.75, color: 'inherit', textDecoration: 'none' }}
        >
          <StorefrontIcon />
          <Typography variant="h6" sx={{ display: { xs: 'none', sm: 'block' }, letterSpacing: '-0.5px' }}>
            Shop
            <Box component="span" sx={{ color: 'secondary.main' }}>
              Kart
            </Box>
          </Typography>
        </Box>

        <Box sx={{ display: { xs: 'none', lg: 'block' } }}>
          <DeliverTo />
        </Box>

        <SearchBox />

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, ml: 'auto' }}>
          {isAuthenticated ? (
            <>
              {isAdmin && (
                <HeaderAction
                  to="/admin"
                  caption="Store"
                  label="Admin"
                  icon={<AdminPanelSettingsOutlinedIcon fontSize="small" />}
                  sx={{ display: { xs: 'none', md: 'flex' } }}
                />
              )}
              <HeaderAction
                to="/orders"
                caption="Returns"
                label="& Orders"
                icon={<LocalShippingOutlinedIcon fontSize="small" />}
                sx={{ display: { xs: 'none', md: 'flex' } }}
              />
              <HeaderAction
                onClick={(event) => setAnchor(event.currentTarget)}
                caption={`Hello, ${user.username}`}
                label="Account"
                icon={<PersonOutlineIcon fontSize="small" />}
                aria-haspopup="true"
              />
              <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
                <MenuItem component={RouterLink} to="/account/addresses" onClick={() => setAnchor(null)}>
                  <ListItemIcon>
                    <LocationOnOutlinedIcon fontSize="small" />
                  </ListItemIcon>
                  Your addresses
                </MenuItem>
                <MenuItem component={RouterLink} to="/orders" onClick={() => setAnchor(null)}>
                  <ListItemIcon>
                    <LocalShippingOutlinedIcon fontSize="small" />
                  </ListItemIcon>
                  Your orders
                </MenuItem>
                {isAdmin && (
                  <MenuItem component={RouterLink} to="/admin" onClick={() => setAnchor(null)}>
                    <ListItemIcon>
                      <AdminPanelSettingsOutlinedIcon fontSize="small" />
                    </ListItemIcon>
                    Store admin
                  </MenuItem>
                )}
                <Divider />
                <MenuItem
                  onClick={() => {
                    setAnchor(null);
                    logout();
                  }}
                >
                  <ListItemIcon>
                    <LogoutOutlinedIcon fontSize="small" />
                  </ListItemIcon>
                  Sign out
                </MenuItem>
              </Menu>
            </>
          ) : (
            <Button component={RouterLink} to="/login" color="inherit" startIcon={<PersonOutlineIcon />}>
              Sign in
            </Button>
          )}

          <IconButton
            component={RouterLink}
            to="/cart"
            color="inherit"
            aria-label={`Cart, ${itemCount} items`}
            sx={{ ml: 0.5 }}
          >
            <Badge badgeContent={itemCount} color="secondary" showZero max={99}>
              <ShoppingCartOutlinedIcon />
            </Badge>
          </IconButton>
        </Box>
      </Toolbar>
    </AppBar>
  );
}
