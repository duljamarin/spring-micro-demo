import { useState } from 'react'
import { checkoutCart, removeFromCart } from '../api.js'

/**
 * Renders a checkout result as what it actually is: three outcomes, not two.
 *
 * cart-service distinguishes `failure` (sent downstream, may have left state
 * behind) from `notAttempted` (the time budget ran out before this line was
 * touched, so nothing was sent and retrying is unambiguously safe). Folding
 * those into one red "failed" would throw away the only information that
 * tells a user whether it is safe to press the button again.
 */
function Outcome({ result, nameFor }) {
  const skipped = !result.success && result.orderId === null && /not attempted/i.test(result.message)
  const kind = result.success ? 'success' : skipped ? 'skipped' : 'failure'

  return (
    <div className={`outcome outcome--${kind}`}>
      <div className="outcome__head">
        <span className="outcome__verdict">{nameFor(result.productId)}</span>
        {result.success && <span className="status status--confirmed">Order {result.orderId}</span>}
        {skipped && <span className="status status--pending">Left in cart</span>}
        {!result.success && !skipped && <span className="status status--failed">Failed</span>}
      </div>
      <p className="outcome__detail">
        {result.message}
        {skipped && ' Nothing was sent downstream for this line, so retrying is safe.'}
      </p>
    </div>
  )
}

export default function Cart({ cart, products, onChanged, onNotify }) {
  const [busy, setBusy] = useState(null)
  const [results, setResults] = useState(null)

  const nameFor = (productId) =>
    products?.find((p) => p.id === productId)?.name ?? `Product #${productId}`
  const priceFor = (productId) => products?.find((p) => p.id === productId)?.price

  if (cart === null) {
    return <p className="product__meta">Loading your cart…</p>
  }

  const items = cart.items ?? []

  const total = items.reduce((sum, item) => {
    const price = priceFor(item.productId)
    return price == null ? sum : sum + Number(price) * item.quantity
  }, 0)

  async function handleCheckout() {
    setBusy('checkout')
    setResults(null)
    try {
      setResults(await checkoutCart())
      onChanged()
    } catch (err) {
      onNotify(
        null,
        err.status === 503
          ? new Error(
              'The gateway served its fallback for cart-service. The checkout may still be running — check Orders before retrying.',
            )
          : err,
      )
    } finally {
      setBusy(null)
    }
  }

  async function handleRemove(productId) {
    setBusy(`remove-${productId}`)
    try {
      await removeFromCart(productId)
      onChanged()
    } catch (err) {
      onNotify(null, err)
    } finally {
      setBusy(null)
    }
  }

  return (
    <>
      <p className="intro">
        cart-service runs one saga per line, in sequence, inside a 20-second budget. Lines it never
        reaches come back as left in the cart rather than failed — those two things mean different
        amounts of risk when you retry.
      </p>

      {items.length === 0 ? (
        <div className="empty">
          <p>Your cart is empty. Add something from the catalog to check out.</p>
        </div>
      ) : (
        <>
          {items.map((item) => (
            <div className="row" key={item.productId}>
              <div className="row__main">
                <div className="row__title">{nameFor(item.productId)}</div>
                <p className="row__sub">Quantity {item.quantity}</p>
              </div>
              {priceFor(item.productId) != null && (
                <span className="price">
                  {(Number(priceFor(item.productId)) * item.quantity).toFixed(2)}
                </span>
              )}
              <div className="row__actions">
                <button
                  className="btn"
                  disabled={busy !== null}
                  onClick={() => handleRemove(item.productId)}
                >
                  {busy === `remove-${item.productId}` ? 'Removing…' : 'Remove'}
                </button>
              </div>
            </div>
          ))}

          <div className="toolbar">
            <button className="btn btn--primary" onClick={handleCheckout} disabled={busy !== null}>
              {busy === 'checkout' ? 'Running the saga…' : 'Check out'}
            </button>
            {total > 0 && <span className="price">Total {total.toFixed(2)}</span>}
          </div>

          {busy === 'checkout' && (
            <p className="notice notice--working" style={{ marginTop: '1rem' }}>
              Reserving stock, creating each order, charging payment, confirming. Compensations run
              automatically if a step fails after an earlier one succeeded.
            </p>
          )}
        </>
      )}

      {results && (
        <>
          <div className="scale-key">
            <h2>Checkout result</h2>
            <span className="scale-key__hint">One line per cart item</span>
          </div>
          {results.map((result) => (
            <Outcome key={result.productId} result={result} nameFor={nameFor} />
          ))}
        </>
      )}
    </>
  )
}
