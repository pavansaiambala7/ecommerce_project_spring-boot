import { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { api } from '../../api/client';
import { browseLink } from '../../hooks/useCatalog';
import { formatPrice } from '../../utils/format';

function Stat({ label, value }) {
  return (
    <Card>
      <CardContent>
        <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase' }}>
          {label}
        </Typography>
        <Typography variant="h2" sx={{ mt: 0.5 }}>
          {value}
        </Typography>
      </CardContent>
    </Card>
  );
}

export default function AdminOverview() {
  const [facets, setFacets] = useState(null);
  const [total, setTotal] = useState(null);
  const [outOfStock, setOutOfStock] = useState(null);

  useEffect(() => {
    api.get('/api/products/facets', { auth: false }).then(setFacets).catch(() => {});
    api
      .get('/api/products/search?size=1', { auth: false })
      .then((page) => setTotal(page.totalItems))
      .catch(() => {});
    // Everything in stock, subtracted from the total, gives what is not.
    api
      .get('/api/products/search?size=1&inStockOnly=true', { auth: false })
      .then((page) => setOutOfStock(page.totalItems))
      .catch(() => {});
  }, []);

  const unavailable = total != null && outOfStock != null ? total - outOfStock : null;
  const number = (n) => (n == null ? '—' : new Intl.NumberFormat('en-IN').format(n));

  return (
    <>
      <Typography variant="h1" sx={{ mb: 2 }}>
        Store overview
      </Typography>

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' },
          gap: 2,
          mb: 3,
        }}
      >
        <Stat label="Products" value={number(total)} />
        <Stat label="Departments" value={number(facets?.categories.length)} />
        <Stat label="Out of stock" value={number(unavailable)} />
        <Stat
          label="Price range"
          value={
            <Typography variant="h3" sx={{ mt: 1 }}>
              {facets ? `${formatPrice(facets.minPrice)} – ${formatPrice(facets.maxPrice)}` : '—'}
            </Typography>
          }
        />
      </Box>

      <Paper sx={{ p: 2 }}>
        <Typography variant="h3" sx={{ mb: 1.5 }}>
          Products per department
        </Typography>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Department</TableCell>
                <TableCell align="right">Products</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {facets?.categories.map((category) => (
                <TableRow key={category.id} hover>
                  <TableCell>
                    <Link
                      component={RouterLink}
                      to={browseLink({ categoryId: category.id })}
                      underline="hover"
                    >
                      {category.name}
                    </Link>
                  </TableCell>
                  <TableCell align="right">{number(category.count)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    </>
  );
}
