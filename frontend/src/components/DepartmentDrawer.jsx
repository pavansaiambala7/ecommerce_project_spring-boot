import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import ListSubheader from '@mui/material/ListSubheader';
import Typography from '@mui/material/Typography';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import CloseIcon from '@mui/icons-material/Close';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import { useAuth } from '../context/AuthContext';
import { browseLink } from '../hooks/useCatalog';

const formatCount = (n) => new Intl.NumberFormat('en-IN').format(n);

/**
 * The slide-in "All" menu. A department with sub-departments opens a second
 * panel rather than expanding in place, which keeps a long menu readable on a
 * phone.
 */
export default function DepartmentDrawer({ open, onClose, tree }) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [drilled, setDrilled] = useState(null);

  useEffect(() => {
    if (!open) setDrilled(null);
  }, [open]);

  function browse(params) {
    onClose();
    navigate(browseLink(params));
  }

  const count = (node) => (
    <Typography variant="caption" color="text.secondary">
      {formatCount(node.productCount)}
    </Typography>
  );

  return (
    <Drawer open={open} onClose={onClose} PaperProps={{ sx: { width: { xs: 300, sm: 360 } } }}>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          px: 2,
          py: 1.75,
          bgcolor: 'primary.dark',
          color: 'common.white',
        }}
      >
        <PersonOutlineIcon />
        <Typography variant="h6" sx={{ flex: 1 }}>
          Hello, {user ? user.username : 'sign in'}
        </Typography>
        <IconButton onClick={onClose} aria-label="Close menu" sx={{ color: 'inherit' }}>
          <CloseIcon />
        </IconButton>
      </Box>

      {drilled ? (
        <List disablePadding>
          <Button onClick={() => setDrilled(null)} startIcon={<ArrowBackIcon />} sx={{ m: 1.5 }}>
            Main menu
          </Button>
          <Divider />
          <ListSubheader>{drilled.name}</ListSubheader>
          <ListItemButton onClick={() => browse({ categoryId: drilled.id })}>
            <ListItemText primary={`All ${drilled.name}`} />
            {count(drilled)}
          </ListItemButton>
          {drilled.children.map((child) => (
            <ListItemButton key={child.id} onClick={() => browse({ categoryId: child.id })}>
              <ListItemText primary={child.name} />
              {count(child)}
            </ListItemButton>
          ))}
        </List>
      ) : (
        <List disablePadding>
          <ListSubheader>Trending</ListSubheader>
          <ListItemButton onClick={() => browse({ minDiscount: 10, sort: 'discount' })}>
            <ListItemText primary="Today's Deals" />
          </ListItemButton>
          <ListItemButton onClick={() => browse({ sort: 'rating' })}>
            <ListItemText primary="Top rated" />
          </ListItemButton>
          <Divider sx={{ my: 1 }} />

          <ListSubheader>Shop by Department</ListSubheader>
          {tree.map((department) =>
            department.children.length > 0 ? (
              <ListItemButton key={department.id} onClick={() => setDrilled(department)}>
                <ListItemText primary={department.name} />
                <ChevronRightIcon fontSize="small" sx={{ color: 'text.disabled' }} />
              </ListItemButton>
            ) : (
              <ListItemButton key={department.id} onClick={() => browse({ categoryId: department.id })}>
                <ListItemText primary={department.name} />
                {count(department)}
              </ListItemButton>
            ),
          )}
        </List>
      )}
    </Drawer>
  );
}
