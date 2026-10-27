import { useCallback, useEffect, useState } from 'react'
import {
  clearSession,
  getCart,
  getEngine,
  getOrders,
  getProducts,
  getScores,
  getSession,
  getToken,
  getUser,
} from './api.js'
import SignIn from './components/SignIn.jsx'
import Account from './views/Account.jsx'
import Cart from './views/Cart.jsx'
import Catalog from './views/Catalog.jsx'
import Orders from './views/Orders.jsx'
import Scores from './views/Scores.jsx'

/** How often the engine lamp re-checks. Long enough not to spam the probe. */
const ENGINE_POLL_MS = 15000

const VIEWS = [
  { id: 'catalog', label: 'Catalog' },
  { id: 'cart', label: 'Cart' },
  { id: 'orders', label: 'Orders' },
  { id: 'scores', label: 'Review scores' },
  { id: 'account', label: 'Account' },
]

const HEADINGS = {
  catalog: ['What’s in stock', 'product-service owns the catalog and is the only writer to it.'],
  cart: ['Your cart', 'cart-service holds lines until you check out, then runs a saga per line.'],
  orders: ['Your orders', 'What the saga actually committed, including what it failed to.'],
  scores: [
    'What customers said, scored by a model on this machine',
    'review-service reads each review with a local LLM through Spring AI and rates it on four 0–100 scales.',
  ],
  account: ['Your account', 'Issued by user-service, verified once by the gateway.'],
}

export default function App() {
  const [session, setSession] = useState(() => (getToken() ? getSession() : null))
  const [view, setView] = useState('catalog')

  const [products, setProducts] = useState(null)
  const [cart, setCart] = useState(null)
  const [orders, setOrders] = useState(null)
  const [rows, setRows] = useState(null)
  const [engine, setEngine] = useState(null)
  const [profile, setProfile] = useState(null)

  const [error, setError] = useState(null)
  const [flash, setFlash] = useState(null)

  const signedIn = session !== null

  const signOut = useCallback(() => {
    clearSession()
    setSession(null)
    setProducts(null)
    setCart(null)
    setOrders(null)
    setRows(null)
    setEngine(null)
    setProfile(null)
  }, [])

  /**
   * One message channel for every view. `onNotify(message)` reports success,
   * `onNotify(null, error)` reports failure, and a 401 anywhere drops straight
   * back to sign-in rather than leaving the user clicking dead buttons.
   */
  const notify = useCallback(
    (message, err) => {
      if (err) {
        if (err.status === 401) {
          signOut()
          return
        }
        setFlash(null)
        setError(err.message)
        return
      }
      setError(null)
      setFlash(message)
    },
    [signOut],
  )

  /** Everything the shell owns, reloaded together: a checkout changes stock,
   *  the cart and the order list at once, so refreshing one of them alone
   *  would leave the other two showing a state that no longer exists. */
  const reload = useCallback(async () => {
    if (!session) return
    const results = await Promise.allSettled([
      getProducts(),
      getCart(),
      getOrders(session.userId),
      getScores(),
    ])
    const [p, c, o, s] = results

    if (p.status === 'fulfilled') setProducts(p.value)
    if (c.status === 'fulfilled') setCart(c.value)
    if (o.status === 'fulfilled') setOrders(o.value)
    if (s.status === 'fulfilled') setRows(s.value)

    // allSettled, not all: one service being down must not blank the views
    // fed by the others. A 401 is the exception - the token is gone, so
    // nothing will load.
    const failure = results.find((r) => r.status === 'rejected')
    if (failure) {
      if (failure.reason?.status === 401) signOut()
      else setError(failure.reason?.message ?? 'Some data could not be loaded.')
    } else {
      setError(null)
    }
  }, [session, signOut])

  useEffect(() => {
    reload()
  }, [reload])

  // Engine state, polled: it changes without the user doing anything (they
  // start ollama in another terminal), and the lamp has to notice.
  useEffect(() => {
    if (!signedIn) return
    let cancelled = false
    const check = async () => {
      try {
        const next = await getEngine()
        if (!cancelled) setEngine(next)
      } catch {
        /* the error banner already covers an outage */
      }
    }
    check()
    const timer = setInterval(check, ENGINE_POLL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [signedIn])

  // Profile is only needed on one tab, so it is fetched when that tab opens.
  useEffect(() => {
    if (view !== 'account' || !session || profile) return
    getUser(session.userId)
      .then(setProfile)
      .catch(() => {
        /* the account view falls back to the session's own username */
      })
  }, [view, session, profile])

  if (!signedIn) {
    return <SignIn onSignedIn={setSession} />
  }

  const [heading, blurb] = HEADINGS[view]

  return (
    <div className="shell">
      <nav className="nav">
        {VIEWS.map((item) => (
          <button
            key={item.id}
            className="nav__link"
            aria-current={view === item.id ? 'page' : undefined}
            onClick={() => {
              setView(item.id)
              setFlash(null)
            }}
          >
            {item.label}
            {item.id === 'cart' && cart?.items?.length > 0 && (
              <span className="nav__count">{cart.items.length}</span>
            )}
            {item.id === 'orders' && orders?.length > 0 && (
              <span className="nav__count">{orders.length}</span>
            )}
          </button>
        ))}
        <span className="nav__spacer" />
        <span className="product__meta">{session.username}</span>
      </nav>

      <header className="masthead">
        <h1>{heading}</h1>
        <p>{blurb}</p>
      </header>

      {error && <p className="notice">{error}</p>}
      {flash && <p className="notice notice--working">{flash}</p>}

      {view === 'catalog' && (
        <Catalog products={products} onChanged={reload} onNotify={notify} />
      )}
      {view === 'cart' && (
        <Cart cart={cart} products={products} onChanged={reload} onNotify={notify} />
      )}
      {view === 'orders' && <Orders orders={orders} onRefresh={reload} />}
      {view === 'scores' && (
        <Scores
          engine={engine}
          rows={rows}
          author={session.username}
          onChanged={reload}
          onNotify={notify}
        />
      )}
      {view === 'account' && (
        <Account profile={profile} session={session} onSignOut={signOut} />
      )}
    </div>
  )
}
