import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Container from '@mui/material/Container';
import Drawer from '@mui/material/Drawer';
import MenuItem from '@mui/material/MenuItem';
import Pagination from '@mui/material/Pagination';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import TuneIcon from '@mui/icons-material/Tune';
import FilterSidebar from '../components/FilterSidebar';
import ProductCard from '../components/ProductCard';
import { findInTree, useCategoryTree, useProductSearch } from '../hooks/useCatalog';

const SORTS = [
  { value: 'relevance', label: 'Featured' },
  { value: 'price_asc', label: 'Price: Low to High' },
  { value: 'price_desc', label: 'Price: High to Low' },
  { value: 'rating', label: 'Avg. Customer Review' },
  { value: 'discount', label: 'Discount: High to Low' },
  { value: 'name', label: 'Name: A to Z' },
];

const GRID = {
  display: 'grid',
  gridTemplateColumns: {
    xs: 'repeat(2, 1fr)',
    sm: 'repeat(3, 1fr)',
    lg: 'repeat(4, 1fr)',
    xl: 'repeat(5, 1fr)',
  },
  gap: 2,
};

export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { tree } = useCategoryTree();
  const [filtersOpen, setFiltersOpen] = useState(false);

  const params = {
    q: searchParams.get('q') ?? '',
    categoryId: searchParams.get('categoryId'),
    minPrice: searchParams.get('minPrice'),
    maxPrice: searchParams.get('maxPrice'),
    minDiscount: searchParams.get('minDiscount'),
    inStockOnly: searchParams.get('inStockOnly') === 'true',
    sort: searchParams.get('sort') ?? 'relevance',
    page: Number(searchParams.get('page') ?? 0),
    size: 24,
  };

  const { page, loading, error } = useProductSearch(params);
  const { node: department } = findInTree(tree, params.categoryId);

  // Filters live in the URL, so every change is a navigation: shareable,
  // bookmarkable, and the back button behaves the way shoppers expect.
  function update(changes) {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => {
      if (value === null || value === undefined || value === '' || value === false) {
        next.delete(key);
      } else {
        next.set(key, value);
      }
    });
    setSearchParams(next);
    setFiltersOpen(false);
    window.scrollTo({ top: 0 });
  }

  const total = page?.totalItems ?? 0;
  const from = total === 0 ? 0 : params.page * params.size + 1;
  const to = Math.min(total, (params.page + 1) * params.size);

  let heading = 'Results';
  if (params.q) heading = `“${params.q}”`;
  else if (department) heading = department.name;
  else if (params.sort === 'discount') heading = "Today's Deals";

  const filters = <FilterSidebar params={params} onChange={update} />;

  return (
    <Container maxWidth="xl" sx={{ py: 2 }}>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '250px 1fr' }, gap: 2 }}>
        <Paper sx={{ p: 2, alignSelf: 'start', display: { xs: 'none', md: 'block' } }}>{filters}</Paper>

        <Drawer open={filtersOpen} onClose={() => setFiltersOpen(false)} PaperProps={{ sx: { width: 290, p: 2 } }}>
          {filters}
        </Drawer>

        <Box>
          <Paper
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 2,
              flexWrap: 'wrap',
              px: 2,
              py: 1.25,
              mb: 2,
            }}
          >
            <Button
              startIcon={<TuneIcon />}
              onClick={() => setFiltersOpen(true)}
              sx={{ display: { xs: 'inline-flex', md: 'none' } }}
            >
              Filters
            </Button>
            <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
              {loading
                ? 'Loading…'
                : total === 0
                  ? 'No results'
                  : `${from}-${to} of ${new Intl.NumberFormat('en-IN').format(total)} results`}
              {params.q && (
                <>
                  {' for '}
                  <Box component="strong" sx={{ color: 'error.main' }}>
                    {`“${params.q}”`}
                  </Box>
                </>
              )}
            </Typography>

            <TextField
              select
              label="Sort by"
              value={params.sort}
              onChange={(event) => update({ sort: event.target.value, page: 0 })}
              sx={{ minWidth: 210 }}
            >
              {SORTS.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </TextField>
          </Paper>

          <Typography variant="h1" sx={{ mb: 2 }}>
            {heading}
          </Typography>

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              Could not load products: {error.message}
            </Alert>
          )}

          {!loading && page?.items.length === 0 && (
            <Paper sx={{ p: 3 }}>
              <Typography variant="h3" gutterBottom>
                No products match these filters
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Try removing a filter, checking the spelling, or searching for something more general.
              </Typography>
            </Paper>
          )}

          {loading && !page ? (
            <Box sx={GRID}>
              {Array.from({ length: 12 }, (_, i) => (
                <Skeleton key={i} variant="rounded" height={380} />
              ))}
            </Box>
          ) : (
            <Box sx={{ ...GRID, opacity: loading ? 0.5 : 1, transition: 'opacity 150ms' }}>
              {page?.items.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </Box>
          )}

          {page && page.totalPages > 1 && (
            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
              <Pagination
                count={page.totalPages}
                page={params.page + 1}
                onChange={(_event, value) => update({ page: value - 1 })}
                color="primary"
                shape="rounded"
                siblingCount={1}
              />
            </Box>
          )}
        </Box>
      </Box>
    </Container>
  );
}
