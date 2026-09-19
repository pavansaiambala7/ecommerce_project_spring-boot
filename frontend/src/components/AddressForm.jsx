import { useEffect, useRef, useState } from 'react';
import Alert from '@mui/material/Alert';
import Autocomplete from '@mui/material/Autocomplete';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import CircularProgress from '@mui/material/CircularProgress';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import MyLocationIcon from '@mui/icons-material/MyLocation';
import { api } from '../api/client';
import { useAddresses } from '../context/AddressContext';
import { INDIAN_STATES, matchState } from '../utils/indianStates';

const EMPTY = {
  fullName: '',
  phone: '',
  pincode: '',
  line1: '',
  line2: '',
  landmark: '',
  city: '',
  state: '',
  latitude: null,
  longitude: null,
  makeDefault: false,
};

const LOCATION_ERRORS = {
  1: 'Location permission was denied. Allow location for this site in your browser settings, or enter the address below.',
  2: 'Your device could not determine its location. Enter the address below.',
  3: 'Finding your location took too long. Try again, or enter the address below.',
};

/**
 * Add or edit a delivery address.
 *
 * <p>Two ways to avoid typing it all: "Use my current location" asks the
 * browser for coordinates and turns them into street, city, state and PIN code;
 * typing a six-digit PIN code fills city and state. Both only fill fields - the
 * shopper always sees and can correct what was filled before saving.
 */
export default function AddressForm({ initial, onSaved, onCancel, submitLabel = 'Save address' }) {
  const { save } = useAddresses();
  const [form, setForm] = useState(() => ({ ...EMPTY, ...(initial ?? {}), makeDefault: false }));
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [locating, setLocating] = useState(false);
  const [note, setNote] = useState(null);
  const [areas, setAreas] = useState([]);
  const lookedUpPin = useRef(initial?.pincode ?? '');

  const set = (field) => (event) => {
    const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  // Fill city and state from the PIN code as soon as all six digits are in.
  useEffect(() => {
    const pin = form.pincode;
    if (!/^[1-9][0-9]{5}$/.test(pin) || pin === lookedUpPin.current) return undefined;
    lookedUpPin.current = pin;
    let active = true;
    api
      .get(`/api/geo/pincode/${pin}`)
      .then((info) => {
        if (!active || !info) return;
        setAreas(info.areas ?? []);
        setForm((current) => ({
          ...current,
          city: current.city || info.city || '',
          state: matchState(info.state) || current.state,
        }));
      })
      .catch((err) => {
        if (active && err.status === 404) {
          setErrors((current) => ({ ...current, pincode: 'We could not find this PIN code. Check it and try again.' }));
        }
        // Any other failure just means no autofill; the fields stay editable.
      });
    return () => {
      active = false;
    };
  }, [form.pincode]);

  function fillFromLocation() {
    setNote(null);
    // Browsers only share location with secure pages. Asking anyway on plain
    // http fails silently in some browsers, so explain instead.
    if (!window.isSecureContext) {
      setNote({
        kind: 'warning',
        text:
          'Your browser only shares location with secure (https://) websites, and this site is not ' +
          'on HTTPS yet. Enter your PIN code instead and the city and state will fill in automatically.',
      });
      return;
    }
    if (!('geolocation' in navigator)) {
      setNote({ kind: 'warning', text: 'This browser cannot share its location. Enter the address below.' });
      return;
    }

    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const { latitude, longitude } = position.coords;
        try {
          const geo = await api.get(`/api/geo/reverse?lat=${latitude}&lng=${longitude}`);
          if (/^[1-9][0-9]{5}$/.test(geo.pincode ?? '')) lookedUpPin.current = geo.pincode;
          setForm((current) => ({
            ...current,
            line2: geo.line2 ?? current.line2,
            city: geo.city ?? current.city,
            state: matchState(geo.state) || current.state,
            pincode: /^[1-9][0-9]{5}$/.test(geo.pincode ?? '') ? geo.pincode : current.pincode,
            latitude: Number(latitude.toFixed(6)),
            longitude: Number(longitude.toFixed(6)),
          }));
          setNote({
            kind: 'success',
            text: 'Filled in from your location. Add your flat or house number and check the rest.',
          });
        } catch (err) {
          setNote({ kind: 'warning', text: err.message });
        } finally {
          setLocating(false);
        }
      },
      (err) => {
        setLocating(false);
        setNote({ kind: 'warning', text: LOCATION_ERRORS[err.code] ?? 'Could not get your location.' });
      },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 60000 },
    );
  }

  async function submit(event) {
    event.preventDefault();
    setSaving(true);
    setFormError(null);
    try {
      const saved = await save(form, initial?.id);
      onSaved?.(saved);
    } catch (err) {
      // The API reports field-level problems by field name; show them there.
      if (err.errors) setErrors(err.errors);
      setFormError(err.errors ? 'Please correct the highlighted fields.' : err.message);
    } finally {
      setSaving(false);
    }
  }

  const field = (name, label, props = {}) => (
    <TextField
      label={label}
      value={form[name] ?? ''}
      onChange={set(name)}
      error={Boolean(errors[name])}
      helperText={errors[name]}
      fullWidth
      {...props}
    />
  );

  return (
    <Box component="form" onSubmit={submit} noValidate sx={{ display: 'grid', gap: 2, maxWidth: 560 }}>
      <Box>
        <Button
          variant="outlined"
          onClick={fillFromLocation}
          disabled={locating}
          startIcon={locating ? <CircularProgress size={16} /> : <MyLocationIcon />}
        >
          {locating ? 'Finding your location…' : 'Use my current location'}
        </Button>
      </Box>

      {note && <Alert severity={note.kind}>{note.text}</Alert>}
      {formError && <Alert severity="error">{formError}</Alert>}

      {field('fullName', 'Full name (first and last name)', {
        autoComplete: 'name',
        inputProps: { maxLength: 100 },
      })}
      {field('phone', 'Mobile number', {
        autoComplete: 'tel-national',
        placeholder: '10-digit mobile number',
        inputProps: { inputMode: 'numeric', maxLength: 10 },
      })}

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
        {field('pincode', 'PIN code', {
          autoComplete: 'postal-code',
          placeholder: '6 digits [0-9] PIN code',
          inputProps: { inputMode: 'numeric', maxLength: 6 },
        })}
        <TextField
          select
          label="State"
          value={form.state ?? ''}
          onChange={set('state')}
          error={Boolean(errors.state)}
          helperText={errors.state}
          autoComplete="address-level1"
          fullWidth
        >
          <MenuItem value="">Choose a state</MenuItem>
          {INDIAN_STATES.map((state) => (
            <MenuItem key={state} value={state}>
              {state}
            </MenuItem>
          ))}
        </TextField>
      </Box>

      {field('line1', 'Flat, House no., Building, Company, Apartment', {
        autoComplete: 'address-line1',
        inputProps: { maxLength: 200 },
      })}

      {/* The PIN code lookup returns the localities it covers, so offer them
          as suggestions rather than making the shopper type one out. */}
      <Autocomplete
        freeSolo
        options={areas}
        inputValue={form.line2 ?? ''}
        onInputChange={(_event, value) => {
          setForm((current) => ({ ...current, line2: value }));
          setErrors((current) => ({ ...current, line2: undefined }));
        }}
        renderInput={(params) => (
          <TextField
            {...params}
            label="Area, Street, Sector, Village"
            autoComplete="address-line2"
            error={Boolean(errors.line2)}
            helperText={errors.line2}
            inputProps={{ ...params.inputProps, maxLength: 200 }}
          />
        )}
      />

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
        {field('landmark', 'Landmark', {
          placeholder: 'E.g. near Apollo Hospital',
          inputProps: { maxLength: 120 },
        })}
        {field('city', 'Town/City', {
          autoComplete: 'address-level2',
          inputProps: { maxLength: 100 },
        })}
      </Box>

      {!initial?.isDefault && (
        <FormControlLabel
          control={<Checkbox size="small" checked={form.makeDefault} onChange={set('makeDefault')} />}
          label="Make this my default address"
        />
      )}

      <Box sx={{ display: 'flex', gap: 1.5 }}>
        <Button type="submit" variant="contained" disabled={saving}>
          {saving ? 'Saving…' : submitLabel}
        </Button>
        {onCancel && (
          <Button variant="text" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </Box>
    </Box>
  );
}
