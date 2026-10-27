// Every call goes through the gateway on :8080 (proxied by Vite in dev), so
// the front-end exercises the same JWT filter, routing and circuit breakers
// that any other client would.
//
// What is deliberately NOT here: product reserve/release and order
// confirm/fail. Those paths are routed publicly, but they are individual
// steps of the checkout saga, meant to be driven by order-orchestrator in a
// sequence that includes their compensations. Calling them from a UI would
// move stock or flip an order's status with no saga around it - the kind of
// "complete" that corrupts data. Checkout is exposed as checkout.

const TOKEN_KEY = 'review-scoreboard.token'
const SESSION_KEY = 'review-scoreboard.session'

function readStored(key) {
  try {
    return localStorage.getItem(key)
  } catch {
    // Private browsing / blocked site data: fall back to per-session login
    // rather than failing to render.
    return null
  }
}

function writeStored(key, value) {
  try {
    localStorage.setItem(key, value)
  } catch {
    /* non-fatal: the session still lives in React state for this tab */
  }
}

export const getToken = () => readStored(TOKEN_KEY)

/** The signed-in user's id and name, needed to scope orders and label reviews. */
export function getSession() {
  const raw = readStored(SESSION_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function clearSession() {
  try {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(SESSION_KEY)
  } catch {
    /* ignore */
  }
}

/**
 * One error type for the whole client. `status` matters to callers: a 401
 * means the token expired and the user has to sign in again, a 409 is a
 * business outcome like "out of stock", and a 503 is the gateway's
 * circuit-breaker fallback meaning "try again shortly". Those must not
 * produce the same message.
 */
export class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.status = status
  }
}

async function request(path, { method = 'GET', body, auth = true } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const token = getToken()
  if (auth && token) headers.Authorization = `Bearer ${token}`

  let response
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError('Cannot reach the gateway on localhost:8080.', 0)
  }

  if (response.status === 204) return null

  // Gateway fallbacks and some error pages are not JSON, so parsing has to be
  // allowed to fail without masking the real status code.
  let payload = null
  const text = await response.text()
  if (text) {
    try {
      payload = JSON.parse(text)
    } catch {
      payload = { message: text }
    }
  }

  if (!response.ok) {
    throw new ApiError(payload?.message || `Request failed (${response.status})`, response.status)
  }
  return payload
}

/* --- auth ---------------------------------------------------------------- */

export async function login(username, password) {
  const auth = await request('/auth/login', {
    method: 'POST',
    body: { username, password },
    auth: false,
  })
  writeStored(TOKEN_KEY, auth.token)
  writeStored(SESSION_KEY, JSON.stringify({ userId: auth.userId, username: auth.username }))
  return { userId: auth.userId, username: auth.username }
}

export async function register(username, password, email) {
  await request('/auth/register', {
    method: 'POST',
    body: { username, email, password },
    auth: false,
  })
  return login(username, password)
}

export const getUser = (id) => request(`/users/${id}`)

/* --- catalog ------------------------------------------------------------- */

export const getProducts = () => request('/products')
export const createProduct = (product) => request('/products', { method: 'POST', body: product })
export const deleteProduct = (id) => request(`/products/${id}`, { method: 'DELETE' })

/* --- cart ---------------------------------------------------------------- */
// The gateway turns the JWT into X-User-Id, so "me" is all the client needs
// to say - the cart is never addressed by user id from outside.

export const getCart = () => request('/carts/me')
export const addToCart = (productId, quantity) =>
  request('/carts/me/items', { method: 'POST', body: { productId, quantity } })
export const removeFromCart = (productId) =>
  request(`/carts/me/items/${productId}`, { method: 'DELETE' })
export const checkoutCart = () => request('/carts/me/checkout', { method: 'POST' })

/** Single-item checkout straight into the saga, bypassing the cart. */
export const checkoutNow = (productId, quantity) =>
  request('/checkout', { method: 'POST', body: { productId, quantity } })

/* --- orders -------------------------------------------------------------- */

export const getOrders = (userId) =>
  request(userId == null ? '/orders' : `/orders?userId=${userId}`)

/* --- reviews and scores -------------------------------------------------- */

export const getEngine = () => request('/reviews/engine')
export const getScores = () => request('/reviews/scores')
export const getReviews = (productId) => request(`/reviews?productId=${productId}`)
export const rescore = (id) => request(`/reviews/${id}/rescore`, { method: 'POST' })
export const submitReview = (productId, author, text) =>
  request('/reviews', { method: 'POST', body: { productId, author, text } })
