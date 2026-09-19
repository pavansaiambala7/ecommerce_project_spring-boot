import { useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
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
import DriveFileRenameOutlineIcon from '@mui/icons-material/DriveFileRenameOutline';
import { api } from '../../api/client';

export default function AdminCategories() {
  const [categories, setCategories] = useState([]);
  const [name, setName] = useState('');
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api.get('/api/categories/all').then(setCategories).catch(setError);
  }, []);

  useEffect(load, [load]);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (editingId) await api.put(`/api/categories/${editingId}`, { name });
      else await api.post('/api/categories', { name });
      setName('');
      setEditingId(null);
      load();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function remove(category) {
    setBusy(true);
    setError(null);
    try {
      await api.del(`/api/categories/${category.id}`);
      load();
    } catch (err) {
      // The server refuses to delete a department that still has products,
      // rather than orphaning them. Show that reason rather than a generic
      // failure.
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <Typography variant="h1" sx={{ mb: 2 }}>
        Departments
      </Typography>

      <Paper component="form" onSubmit={submit} sx={{ p: { xs: 2, sm: 3 }, mb: 3 }}>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error.message}
          </Alert>
        )}
        <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <TextField
            required
            label="Department name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            inputProps={{ maxLength: 255 }}
            sx={{ flex: 1, minWidth: 220 }}
          />
          <Button type="submit" variant="contained" disabled={busy}>
            {editingId ? 'Rename' : 'Add'}
          </Button>
          {editingId && (
            <Button
              variant="text"
              onClick={() => {
                setEditingId(null);
                setName('');
              }}
            >
              Cancel
            </Button>
          )}
        </Box>
      </Paper>

      <Paper>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell align="right">ID</TableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {categories.map((category) => (
                <TableRow key={category.id} hover>
                  <TableCell>{category.name}</TableCell>
                  <TableCell align="right">{category.id}</TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <Tooltip title="Rename">
                      <IconButton
                        size="small"
                        onClick={() => {
                          setEditingId(category.id);
                          setName(category.name);
                        }}
                      >
                        <DriveFileRenameOutlineIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <span>
                        <IconButton size="small" color="error" disabled={busy} onClick={() => remove(category)}>
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
    </>
  );
}
