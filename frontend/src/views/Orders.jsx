/**
 * order-service's view of what happened.
 *
 * PENDING is shown as its own state rather than being rounded to "failed" or
 * hidden: an order sits in PENDING between the saga creating it and payment
 * being confirmed, so a PENDING row that never moves is a real signal that a
 * saga died partway - exactly the thing worth being able to see.
 */
function statusClass(status) {
  if (status === 'CONFIRMED') return 'status--confirmed'
  return status === 'FAILED' ? 'status--failed' : 'status--pending'
}

export default function Orders({ orders, onRefresh }) {
  if (orders === null) {
    return <p className="product__meta">Loading your orders…</p>
  }

  return (
    <>
      <p className="intro">
        order-service is a pure data owner — it calls nothing. Every row here was written by
        order-orchestrator as it ran a saga.
      </p>

      {orders.length === 0 ? (
        <div className="empty">
          <p>No orders yet. Check out from the cart, or use “Buy now” in the catalog.</p>
        </div>
      ) : (
        orders.map((order) => (
          <div className="row" key={order.id}>
            <div className="row__main">
              <div className="row__title">
                {order.productName ?? `Product #${order.productId}`}
              </div>
              <p className="row__sub">
                Order {order.id} · {order.quantity} ×{' '}
                {order.unitPrice == null ? '—' : Number(order.unitPrice).toFixed(2)} ·{' '}
                {new Date(order.createdAt).toLocaleString()}
              </p>
              {order.failureReason && <p className="row__sub">{order.failureReason}</p>}
            </div>

            {order.totalPrice != null && (
              <span className="price">{Number(order.totalPrice).toFixed(2)}</span>
            )}
            <span className={`status ${statusClass(order.status)}`}>{order.status}</span>
          </div>
        ))
      )}

      <div className="toolbar">
        <button className="btn" onClick={onRefresh}>
          Refresh
        </button>
      </div>
    </>
  )
}
