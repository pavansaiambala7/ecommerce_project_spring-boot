import { useEffect, useRef, useState } from 'react';
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
    if (!/^[1-9][0-9]{5}$/.test(pin) || pin === lookedUpPin.current) return;
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
        kind: 'warn',
        text:
          'Your browser only shares location with secure (https://) websites, and this site is not ' +
          'on HTTPS yet. Enter your PIN code instead and the city and state will fill in automatically.',
      });
      return;
    }
    if (!('geolocation' in navigator)) {
      setNote({ kind: 'warn', text: 'This browser cannot share its location. Enter the address below.' });
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
            kind: 'ok',
            text: 'Filled in from your location. Add your flat or house number and check the rest.',
          });
        } catch (err) {
          setNote({ kind: 'warn', text: err.message });
        } finally {
          setLocating(false);
        }
      },
      (err) => {
        setLocating(false);
        setNote({ kind: 'warn', text: LOCATION_ERRORS[err.code] ?? 'Could not get your location.' });
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
    <label className={`field ${errors[name] ? 'field-invalid' : ''}`}>
      <span>{label}</span>
      <input value={form[name] ?? ''} onChange={set(name)} {...props} />
      {errors[name] && <small className="field-error">{errors[name]}</small>}
    </label>
  );

  return (
    <form className="address-form" onSubmit={submit} noValidate>
      <button type="button" className="locate-btn" onClick={fillFromLocation} disabled={locating}>
        <span aria-hidden="true">⌖</span> {locating ? 'Finding your location…' : 'Use my current location'}
      </button>
      {note && <div className={`form-note form-note-${note.kind}`}>{note.text}</div>}
      {formError && <div className="error">{formError}</div>}

      {field('fullName', 'Full name (first and last name)', { autoComplete: 'name', maxLength: 100 })}
      {field('phone', 'Mobile number', {
        inputMode: 'numeric',
        autoComplete: 'tel-national',
        maxLength: 10,
        placeholder: '10-digit mobile number',
      })}

      <div className="field-row">
        {field('pincode', 'PIN code', {
          inputMode: 'numeric',
          autoComplete: 'postal-code',
          maxLength: 6,
          placeholder: '6 digits [0-9] PIN code',
        })}
        <label className={`field ${errors.state ? 'field-invalid' : ''}`}>
          <span>State</span>
          <select value={form.state} onChange={set('state')} autoComplete="address-level1">
            <option value="">Choose a state</option>
            {INDIAN_STATES.map((state) => (
              <option key={state} value={state}>
                {state}
              </option>
            ))}
          </select>
          {errors.state && <small className="field-error">{errors.state}</small>}
        </label>
      </div>

      {field('line1', 'Flat, House no., Building, Company, Apartment', { autoComplete: 'address-line1', maxLength: 200 })}
      {field('line2', 'Area, Street, Sector, Village', {
        autoComplete: 'address-line2',
        maxLength: 200,
        list: areas.length ? 'pincode-areas' : undefined,
      })}
      {areas.length > 0 && (
        <datalist id="pincode-areas">
          {areas.map((area) => (
            <option key={area} value={area} />
          ))}
        </datalist>
      )}
      <div className="field-row">
        {field('landmark', 'Landmark', { placeholder: 'E.g. near Apollo Hospital', maxLength: 120 })}
        {field('city', 'Town/City', { autoComplete: 'address-level2', maxLength: 100 })}
      </div>

      {!initial?.isDefault && (
        <label className="checkbox-row">
          <input type="checkbox" checked={form.makeDefault} onChange={set('makeDefault')} />
          Make this my default address
        </label>
      )}

      <div className="form-actions">
        <button type="submit" className="btn" disabled={saving}>
          {saving ? 'Saving…' : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="btn-plain" onClick={onCancel}>
            Cancel
          </button>
        )}
      </div>
    </form>
  );
}
