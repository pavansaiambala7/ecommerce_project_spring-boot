import { useState } from 'react';
import AddressForm from '../components/AddressForm';
import { useAddresses } from '../context/AddressContext';

export function AddressLines({ address }) {
  return (
    <>
      <strong>{address.fullName}</strong>
      <div>{address.line1}</div>
      {address.line2 && <div>{address.line2}</div>}
      {address.landmark && <div>Near {address.landmark}</div>}
      <div>
        {address.city}, {address.state} {address.pincode}
      </div>
      <div>India</div>
      <div>Phone: {address.phone}</div>
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

  if (!loaded) return <div className="page-status">Loading your addresses…</div>;

  if (editing) {
    return (
      <div className="panel narrow-panel">
        <h1 className="section-title">{editing === 'new' ? 'Add a new address' : 'Edit your address'}</h1>
        <AddressForm
          initial={editing === 'new' ? null : editing}
          onSaved={() => setEditing(null)}
          onCancel={() => setEditing(null)}
          submitLabel={editing === 'new' ? 'Add address' : 'Update address'}
        />
      </div>
    );
  }

  return (
    <div className="panel">
      <h1 className="section-title">Your Addresses</h1>
      {error && <div className="error">{error}</div>}
      <div className="address-grid">
        <button type="button" className="address-tile address-add" onClick={() => setEditing('new')}>
          <span aria-hidden="true">+</span>
          Add address
        </button>
        {addresses.map((address) => (
          <div key={address.id} className="address-tile">
            {address.isDefault && <div className="address-default">Default</div>}
            <div className="address-body">
              <AddressLines address={address} />
            </div>
            <div className="address-actions">
              <button type="button" className="link-btn" onClick={() => setEditing(address)}>
                Edit
              </button>
              <button
                type="button"
                className="link-btn"
                disabled={working === address.id}
                onClick={() => run(address.id, () => remove(address.id))}
              >
                Remove
              </button>
              {!address.isDefault && (
                <button
                  type="button"
                  className="link-btn"
                  disabled={working === address.id}
                  onClick={() => run(address.id, () => makeDefault(address.id))}
                >
                  Set as default
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
