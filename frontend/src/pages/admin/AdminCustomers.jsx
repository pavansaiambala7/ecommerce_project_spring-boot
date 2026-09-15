import { useEffect, useState } from 'react';
import { api } from '../../api/client';

export default function AdminCustomers() {
  const [users, setUsers] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.get('/api/users').then(setUsers).catch(setError);
  }, []);

  if (error) return <div className="panel"><div className="error">{error.message}</div></div>;
  if (!users) return <div className="page-status">Loading customers…</div>;

  return (
    <>
      <h1 className="section-title">Customers <span style={{ color: 'var(--muted)', fontSize: 14 }}>({users.length})</span></h1>
      <div className="panel table-scroll">
        <table className="admin-table">
          <thead><tr><th>Username</th><th>Email</th><th>Address</th><th>Role</th></tr></thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.id}>
                <td>{user.username}</td>
                <td>{user.email}</td>
                <td>{user.address || '—'}</td>
                <td>
                  <span className={user.role === 'ROLE_ADMIN' ? 'badge' : ''}>
                    {user.role === 'ROLE_ADMIN' ? 'Admin' : 'Customer'}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
