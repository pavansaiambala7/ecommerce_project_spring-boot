import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { formatDate, formatPrice } from '../utils/format';

const CANCELLABLE = ['CREATED', 'PAID'];

export default function OrdersPage() {
  const [orders, setOrders] = useState(null);
  const [error, setError] = useState(null);
  const [workingOn, setWorkingOn] = useState(null);

  const load = useCallback(() => {
    api
      .get('/api/orders/me')
      .then(setOrders)
      .catch(setError);
  }, []);

  useEffect(load, [load]);

  async function cancel(orderId) {
    setWorkingOn(orderId);
    setError(null);
    try {
      await api.post(`/api/orders/${orderId}/cancel`);
      load();
    } catch (err) {
      setError(err);
    } finally {
      setWorkingOn(null);
    }
  }

  if (error && !orders) return <div className="page-status">{error.message}</div>;
  if (!orders) return <div className="page-status">Loading your orders…</div>;

  if (orders.length === 0) {
    return (
      <div className="panel">
        <h1 className="section-title">No orders yet</h1>
        <Link to="/" className="btn">
          Start shopping
        </Link>
      </div>
    );
  }

  return (
    <div className="panel">
      <h1 className="section-title">Your Orders</h1>
      {error && <div className="error">{error.message}</div>}

      {orders.map((order) => (
        <div className="order-card" key={order.id}>
          <div className="order-head">
            <div>
              Order placed
              <strong>{formatDate(order.createdAt)}</strong>
            </div>
            <div>
              Total
              <strong>{formatPrice(order.totalAmount)}</strong>
            </div>
            <div>
              Order #
              <strong>{order.id}</strong>
            </div>
            <div style={{ marginLeft: 'auto' }}>
              <span className="badge">{order.status}</span>
            </div>
          </div>

          <div className="order-body">
            {order.items.map((item) => (
              <div key={item.id} className="meta-row">
                {item.quantity} × {item.productName} — {formatPrice(item.lineTotal)}
              </div>
            ))}

            {CANCELLABLE.includes(order.status) && (
              <button
                type="button"
                className="btn-plain"
                style={{ marginTop: 10 }}
                disabled={workingOn === order.id}
                onClick={() => cancel(order.id)}
              >
                {workingOn === order.id ? 'Cancelling…' : 'Cancel order'}
              </button>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
