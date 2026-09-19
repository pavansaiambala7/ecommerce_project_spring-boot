import { useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import MenuIcon from '@mui/icons-material/Menu';
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

  const linkSx = (selected) => ({
    color: 'common.white',
    flexShrink: 0,
    fontWeight: selected ? 700 : 400,
    borderRadius: 1,
    px: 1.5,
    bgcolor: selected ? 'rgba(255,255,255,0.16)' : 'transparent',
    '&:hover': { bgcolor: 'rgba(255,255,255,0.12)' },
  });

  return (
    <>
      <Box
        component="nav"
        aria-label="Departments"
        className="no-scrollbar"
        sx={{
          bgcolor: 'primary.light',
          display: 'flex',
          alignItems: 'center',
          gap: 0.5,
          px: { xs: 1, md: 2 },
          py: 0.5,
          overflowX: 'auto',
        }}
      >
        <Button
          onClick={() => setMenuOpen(true)}
          startIcon={<MenuIcon />}
          sx={{ ...linkSx(false), fontWeight: 600 }}
        >
          All
        </Button>
        <Divider orientation="vertical" flexItem sx={{ borderColor: 'rgba(255,255,255,0.3)', my: 0.75 }} />
        <Button
          component={RouterLink}
          to={browseLink({ minDiscount: 10, sort: 'discount' })}
          sx={linkSx(onDeals)}
        >
          Today&apos;s Deals
        </Button>
        {tree
          .filter((department) => department.featured)
          .map((department) => (
            <Button
              key={department.id}
              component={RouterLink}
              to={browseLink({ categoryId: department.id })}
              sx={linkSx(active === String(department.id))}
            >
              {department.name}
            </Button>
          ))}
      </Box>
      <DepartmentDrawer open={menuOpen} onClose={() => setMenuOpen(false)} tree={tree} />
    </>
  );
}
