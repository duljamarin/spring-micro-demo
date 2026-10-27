import { useState } from 'react'
import { addToCart, checkoutNow, createProduct, deleteProduct } from '../api.js'

/**
 * product-service's catalog, plus the two ways into the saga.
 *
 * "Add to cart" and "Buy one now" are genuinely different code paths, not two
 * buttons for the same thing: the cart batches lines and runs one saga per
 * line under a shared time budget, while "Buy one now" posts straight to
 * order-orchestrator's /api/checkout. Both are exposed because the difference
 * is the thing this project is demonstrating.
 */
function StockLevel({ quantity, lowStockThreshold = 5 }) {
  if (quantity === 0) {
    return <span className="stock stock--out">Out of stock</span>
  }
  const low = quantity <= lowStockThreshold
  return (
    <span className={`stock ${low ? 'stock--low' : ''}`}>
      {quantity} in stock{low ? ' — low' : ''}
    </span>
  )
}

function NewProduct({ onCreated }) {
  const [form, setForm] = useState({ name: '', description: '', price: '', quantity: '0' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value })

  async function handleSubmit(event) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await createProduct({
        name: form.name,
        description: form.description,
        price: form.price,
        quantity: Number(form.quantity),
      })
      setForm({ name: '', description: '', price: '', quantity: '0' })
      onCreated()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <details className="creator">
      <summary>Add a product</summary>
      <form className="form" onSubmit={handleSubmit}>
        <label className="field">
          <span>Name</span>
          <input value={form.name} onChange={set('name')} required />
        </label>
        <label className="field">
          <span>Description</span>
          <input value={form.description} onChange={set('description')} />
        </label>
        <label className="field">
          <span>Price</span>
          {/* Sent as a string: the backend binds BigDecimal, and routing the
              value through a JS number would introduce float rounding into a
              money field for no reason. */}
          <input
            value={form.price}
            onChange={set('price')}
            inputMode="decimal"
            placeholder="49.99"
            required
          />
        </label>
        <label className="field">
          <span>Opening stock</span>
          <input value={form.quantity} onChange={set('quantity')} type="number" min="0" required />
        </label>
        <div>
          <button className="btn btn--primary" type="submit" disabled={busy}>
            {busy ? 'Saving…' : 'Add to catalog'}
          </button>
        </div>
        {error && <p className="notice">{error}</p>}
      </form>
    </details>
  )
}

function ProductRow({ product, onChanged, onNotify }) {
  const [quantity, setQuantity] = useState(1)
  const [busy, setBusy] = useState(null)

  async function run(what, action) {
    setBusy(what)
    try {
      onNotify(await action())
      onChanged()
    } catch (err) {
      onNotify(null, err)
    } finally {
      setBusy(null)
    }
  }

  const soldOut = product.quantity === 0

  return (
    <div className="row">
      <div className="row__main">
        <div className="row__title">{product.name}</div>
        <p className="row__sub">{product.description || 'No description'}</p>
      </div>

      <span className="price">{Number(product.price).toFixed(2)}</span>
      <StockLevel quantity={product.quantity} />

      <div className="row__actions">
        <div className="field--inline">
          <input
            type="number"
            min="1"
            max={Math.max(product.quantity, 1)}
            value={quantity}
            onChange={(e) => setQuantity(Number(e.target.value))}
            aria-label={`Quantity of ${product.name}`}
            disabled={soldOut}
          />
        </div>
        <button
          className="btn"
          disabled={soldOut || busy !== null}
          onClick={() =>
            run('cart', async () => {
              await addToCart(product.id, quantity)
              return `Added ${quantity} × ${product.name} to the cart.`
            })
          }
        >
          {busy === 'cart' ? 'Adding…' : 'Add to cart'}
        </button>
        <button
          className="btn"
          disabled={soldOut || busy !== null}
          onClick={() =>
            run('buy', async () => {
              const order = await checkoutNow(product.id, quantity)
              return `Order ${order.id} placed for ${product.name} — status ${order.status}.`
            })
          }
        >
          {busy === 'buy' ? 'Running the saga…' : 'Buy now'}
        </button>
        <button
          className="btn"
          disabled={busy !== null}
          onClick={() =>
            run('delete', async () => {
              await deleteProduct(product.id)
              return `${product.name} removed from the catalog.`
            })
          }
        >
          {busy === 'delete' ? 'Removing…' : 'Remove'}
        </button>
      </div>
    </div>
  )
}

export default function Catalog({ products, onChanged, onNotify }) {
  if (products === null) {
    return <p className="product__meta">Loading the catalog…</p>
  }

  return (
    <>
      <p className="intro">
        product-service owns stock, and it is the only thing that writes to it. “Buy now” posts
        straight to order-orchestrator; “Add to cart” lets cart-service batch lines and run one
        saga per line under a shared time budget.
      </p>

      {products.length === 0 ? (
        <div className="empty">
          <p>The catalog is empty. Add a product and it becomes orderable immediately.</p>
        </div>
      ) : (
        products.map((product) => (
          <ProductRow
            key={product.id}
            product={product}
            onChanged={onChanged}
            onNotify={onNotify}
          />
        ))
      )}

      <NewProduct onCreated={onChanged} />
    </>
  )
}
