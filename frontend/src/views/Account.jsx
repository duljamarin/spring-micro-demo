/**
 * user-service's record of the signed-in account, plus a plain statement of
 * where the token came from and what it is trusted for.
 *
 * That explanation lives in the UI rather than only in the README because the
 * trust boundary is the single most important thing to understand about this
 * system: the gateway verifies the JWT once, and every service behind it
 * trusts the X-User-Id header instead of re-checking.
 */
export default function Account({ profile, session, onSignOut }) {
  return (
    <>
      <p className="intro">
        The gateway verified your token once, on the way in, and replaced it with an{' '}
        <code>X-User-Id</code> header. Nothing behind the gateway re-checks it.
      </p>

      <div className="row">
        <div className="row__main">
          <div className="row__title">{profile?.username ?? session.username}</div>
          <p className="row__sub">
            {profile?.email ?? 'Loading email…'}
            {profile?.createdAt && ` · joined ${new Date(profile.createdAt).toLocaleDateString()}`}
          </p>
        </div>
        <span className="product__meta">User {session.userId}</span>
      </div>

      <div className="empty">
        <p>
          Accounts live in an in-memory database, so every restart of user-service clears them.
          If a request suddenly returns 401, sign in again — the account will need recreating.
        </p>
        <button className="btn" onClick={onSignOut}>
          Sign out
        </button>
      </div>
    </>
  )
}
