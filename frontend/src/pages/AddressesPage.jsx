import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import Card from '@mui/material/Card';
import CardActions from '@mui/material/CardActions';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Container from '@mui/material/Container';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import AddIcon from '@mui/icons-material/Add';
import AddressForm from '../components/AddressForm';
import { useAddresses } from '../context/AddressContext';

export function AddressLines({ address }) {
  return (
    <>
      <Typography variant="body2" sx={{ fontWeight: 700 }}>
        {address.fullName}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {address.line1}
      </Typography>
      {address.line2 && (
        <Typography variant="body2" color="text.secondary">
          {address.line2}
        </Typography>
      )}
      {address.landmark && (
        <Typography variant="body2" color="text.secondary">
          Near {address.landmark}
        </Typography>
      )}
      <Typography variant="body2" color="text.secondary">
        {address.city}, {address.state} {address.pincode}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        India
      </Typography>
      <Typography variant="body2" color="text.secondary">
        Phone: {address.phone}
      </Typography>
    </>
  );
}

export default function AddressesPage() {
  const { addresses, loaded, remove, makeDefault } = useAddresses();
  const [editing, setEditing] = useState(null); // null, 'new', or an address
  const [error, setError] = useState(null);
  const [working, setWorking] = useState(null);

  async function run(id, action) {
    setWorking(id);
    setError(null);
    try {
      await action();
    } catch (err) {
      setError(err.message);
    } finally {
      setWorking(null);
    }
  }

  if (!loaded) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', py: 10 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (editing) {
    return (
      <Container maxWidth="sm" sx={{ py: 3 }}>
        <Paper sx={{ p: { xs: 2, sm: 3 } }}>
          <Typography variant="h2" sx={{ mb: 2 }}>
            {editing === 'new' ? 'Add a new address' : 'Edit your address'}
          </Typography>
          <AddressForm
            initial={editing === 'new' ? null : editing}
            onSaved={() => setEditing(null)}
            onCancel={() => setEditing(null)}
            submitLabel={editing === 'new' ? 'Add address' : 'Update address'}
          />
        </Paper>
      </Container>
    );
  }

  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Typography variant="h1" sx={{ mb: 2 }}>
        Your Addresses
      </Typography>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)' },
          gap: 2,
        }}
      >
        <ButtonBase
          onClick={() => setEditing('new')}
          sx={{
            minHeight: 210,
            borderRadius: 2,
            border: '2px dashed',
            borderColor: 'divider',
            flexDirection: 'column',
            gap: 1,
            color: 'text.secondary',
            '&:hover': { borderColor: 'primary.main', color: 'primary.main' },
          }}
        >
          <AddIcon sx={{ fontSize: 40 }} />
          <Typography variant="subtitle1">Add address</Typography>
        </ButtonBase>

        {addresses.map((address) => (
          <Card key={address.id} sx={{ display: 'flex', flexDirection: 'column' }}>
            <CardContent sx={{ flex: 1 }}>
              {address.isDefault && <Chip label="Default" size="small" color="primary" sx={{ mb: 1 }} />}
              <AddressLines address={address} />
            </CardContent>
            <CardActions sx={{ px: 2, pb: 2, pt: 0, flexWrap: 'wrap' }}>
              <Button size="small" onClick={() => setEditing(address)}>
                Edit
              </Button>
              <Button
                size="small"
                color="error"
                disabled={working === address.id}
                onClick={() => run(address.id, () => remove(address.id))}
              >
                Remove
              </Button>
              {!address.isDefault && (
                <Button
                  size="small"
                  disabled={working === address.id}
                  onClick={() => run(address.id, () => makeDefault(address.id))}
                >
                  Set as default
                </Button>
              )}
            </CardActions>
          </Card>
        ))}
      </Box>
    </Container>
  );
}
