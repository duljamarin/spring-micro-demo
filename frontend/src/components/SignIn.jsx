import { useState } from 'react'
import { login, register } from '../api.js'

/**
 * The scoreboard sits behind the gateway's JWT filter, so there is no way to
 * read it without a token. Registering is offered on the same screen because
 * the user database is in-memory H2 - every restart of user-service empties
 * it, and sending someone to a sign-up page they can't find would be the
 * commonest way to get stuck.
 *
 * The two modes are explicit rather than two buttons on one form. An earlier
 * version derived the email from the username (`${username}@example.com`),
 * which broke the moment someone signed up with an email address as their
 * username: the derived value came out as `a@b.com@example.com` and the
 * backend rejected it with a validation error that said nothing about where
 * the address had come from. Asking for the field is both honest and shorter.
 */
export default function SignIn({ onSignedIn }) {
  const [mode, setMode] = useState('signin')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const registering = mode === 'register'

  function switchTo(next) {
    setMode(next)
    setError(null)
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      // login()/register() both resolve to {userId, username}: the id is
      // needed to scope the order list, so the caller gets the whole session
      // rather than just the name that was typed in.
      onSignedIn(
        registering
          ? await register(username, password, email)
          : await login(username, password),
      )
    } catch (err) {
      if (err.status === 0) {
        setError('No answer from the gateway on localhost:8080. Is api-gateway running?')
      } else if (err.status === 409 && registering) {
        setError(`"${username}" is taken. Sign in instead, or pick another name.`)
      } else if (err.status === 401) {
        setError('Wrong username or password. If the stack restarted, the account is gone — create it again.')
      } else {
        setError(err.message)
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="signin">
      <div className="signin__inner">
        <h1>Review scoring</h1>
        <p>
          The gateway requires a token on every route except this one, and user-service keeps
          accounts in memory — so after a restart, create yours again.
        </p>

        <form className="form" onSubmit={handleSubmit}>
          <label className="field">
            <span>Username</span>
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              required
              minLength={3}
              maxLength={50}
            />
            <span className="field__note">3 to 50 characters. Not your email address.</span>
          </label>

          {registering && (
            <label className="field">
              <span>Email</span>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                required
                placeholder="you@example.com"
              />
            </label>
          )}

          <label className="field">
            <span>Password</span>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={registering ? 'new-password' : 'current-password'}
              required
              minLength={8}
            />
            <span className="field__note">At least 8 characters.</span>
          </label>

          <div>
            <button className="btn btn--primary" type="submit" disabled={busy}>
              {busy
                ? registering
                  ? 'Creating…'
                  : 'Signing in…'
                : registering
                  ? 'Create account'
                  : 'Sign in'}
            </button>
          </div>

          {error && <p className="notice">{error}</p>}
        </form>

        <p className="field__note" style={{ marginTop: '1.5rem' }}>
          {registering ? 'Already have an account? ' : 'No account yet? '}
          <button
            className="btn btn--plain"
            type="button"
            onClick={() => switchTo(registering ? 'signin' : 'register')}
          >
            {registering ? 'Sign in' : 'Create one'}
          </button>
        </p>
      </div>
    </div>
  )
}
