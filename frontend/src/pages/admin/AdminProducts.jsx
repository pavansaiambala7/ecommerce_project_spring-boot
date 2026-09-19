import { useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Pagination from '@mui/material/Pagination';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import { api } from '../../api/client';
import { formatPrice } from '../../utils/format';

const EMPTY = {
  name: '',
  description: '',
  image: '',
  price: '',
  quantity: '',
  weight: '',
  categoryId: '',
};

export default function AdminProducts() {
  const [page, setPage] = useState(null);
  const [categories, setCategories] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [pageNo, setPageNo] = useState(0);

  const load = useCallback(() => {
    api.get(`/api/products/search?size=20&sort=name&page=${pageNo}`).then(setPage).catch(setError);
  }, [pageNo]);

  useEffect(load, [load]);
  useEffect(() => {
    api.get('/api/categories/all').then(setCategories).catch(() => setCategories([]));
  }, []);

  function edit(product) {
    setEditingId(product.id);
    setForm({
      name: product.name ?? '',
      description: product.description ?? '',
      image: product.image ?? '',
      price: product.price ?? '',
      quantity: product.quantity ?? '',
      weight: product.weight ?? '',
      categoryId: product.category?.id ?? '',
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function reset() {
    setEditingId(null);
    setForm(EMPTY);
    setError(null);
  }

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    const body = {
      ...form,
      price: Number(form.price),
      quantity: Number(form.quantity || 0),
      weight: Number(form.weight || 0),
      categoryId: Number(form.categoryId),
    };
    try {
      if (editingId) await api.put(`/api/products/${editingId}`, body);
      else await api.post('/api/products', body);
      reset();
      load();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function remove(product) {
    // Deleting a product is not reversible from this screen, and the row may
    // already sit in someone's cart or order history.
    if (!window.confirm(`Delete “${product.name}”? This cannot be undone.`)) return;
    setBusy(true);
    setError(null);
    try {
      await api.del(`/api/products/${product.id}`);
      load();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  const set = (field) => (event) => setForm({ ...form, [field]: event.target.value });
  const fieldError = (name) => error?.errors?.[name];

  return (
    <>
      <Typography variant="h1" sx={{ mb: 2 }}>
        {editingId ? 'Edit product' : 'Add a product'}
      </Typography>

      <Paper component="form" onSubmit={submit} sx={{ p: { xs: 2, sm: 3 }, mb: 3 }}>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error.message}
          </Alert>
        )}

        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)' },
            gap: 2,
          }}
        >
          <TextField
            label="Name"
            required
            value={form.name}
            onChange={set('name')}
            inputProps={{ maxLength: 255 }}
            error={Boolean(fieldError('name'))}
            helperText={fieldError('name')}
          />
          <TextField
            select
            label="Department"
            required
            value={form.categoryId}
            onChange={set('categoryId')}
            error={Boolean(fieldError('categoryId'))}
            helperText={fieldError('categoryId')}
          >
            <MenuItem value="">Choose…</MenuItem>
            {categories.map((c) => (
              <MenuItem key={c.id} value={c.id}>
                {c.name}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Price"
            type="number"
            required
            value={form.price}
            onChange={set('price')}
            inputProps={{ min: 0, step: 0.01 }}
            error={Boolean(fieldError('price'))}
            helperText={fieldError('price')}
          />
          <TextField
            label="Stock"
            type="number"
            value={form.quantity}
            onChange={set('quantity')}
            inputProps={{ min: 0 }}
            error={Boolean(fieldError('quantity'))}
            helperText={fieldError('quantity')}
          />
          <TextField
            label="Weight (g)"
            type="number"
            value={form.weight}
            onChange={set('weight')}
            inputProps={{ min: 0 }}
            error={Boolean(fieldError('weight'))}
            helperText={fieldError('weight')}
          />
          <TextField
            label="Image URL"
            value={form.image}
            onChange={set('image')}
            inputProps={{ maxLength: 255 }}
            error={Boolean(fieldError('image'))}
            helperText={fieldError('image')}
          />
        </Box>

        <TextField
          label="Description"
          fullWidth
          multiline
          minRows={2}
          sx={{ mt: 2 }}
          value={form.description}
          onChange={set('description')}
          inputProps={{ maxLength: 255 }}
          error={Boolean(fieldError('description'))}
          helperText={fieldError('description')}
        />

        <Box sx={{ display: 'flex', gap: 1.5, mt: 2 }}>
          <Button type="submit" variant="contained" disabled={busy}>
            {editingId ? 'Save changes' : 'Add product'}
          </Button>
          {editingId && (
            <Button variant="text" onClick={reset}>
              Cancel
            </Button>
          )}
        </Box>
      </Paper>

      <Typography variant="h2" sx={{ mb: 1.5 }}>
        Catalogue
      </Typography>
      <Paper>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Product</TableCell>
                <TableCell>Department</TableCell>
                <TableCell align="right">Price</TableCell>
                <TableCell align="right">Stock</TableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {page?.items.map((product) => (
                <TableRow key={product.id} hover>
                  <TableCell>{product.name}</TableCell>
                  <TableCell>{product.category?.name ?? '—'}</TableCell>
                  <TableCell align="right">{formatPrice(product.price)}</TableCell>
                  <TableCell align="right">{product.quantity}</TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <Tooltip title="Edit">
                      <IconButton size="small" onClick={() => edit(product)}>
                        <EditOutlinedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <span>
                        <IconButton size="small" color="error" disabled={busy} onClick={() => remove(product)}>
                          <DeleteOutlineIcon fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      {page && page.totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
          <Pagination
            count={page.totalPages}
            page={pageNo + 1}
            onChange={(_event, value) => setPageNo(value - 1)}
            color="primary"
            shape="rounded"
          />
        </Box>
      )}
    </>
  );
}
