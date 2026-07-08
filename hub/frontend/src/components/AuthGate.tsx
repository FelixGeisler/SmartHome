import { useCallback, useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import type { AuthStatus } from '../api/auth'
import { getAuthStatus, login, setupAdmin } from '../api/auth'
import { setUnauthorizedHandler } from '../api/devices'

interface AuthGateProps {
  children: ReactNode
}

/** Minimum admin password length, matching the server's setup validation. */
const MIN_PASSWORD_LENGTH = 8

/**
 * Gates the app behind the single administrator login. On first start it shows a setup form to
 * choose the credentials; after that a login form; once authenticated it renders the app. A 401
 * from any API call drops back to the login form.
 */
export function AuthGate({ children }: AuthGateProps) {
  const [status, setStatus] = useState<AuthStatus | null>(null)
  const [unreachable, setUnreachable] = useState(false)

  const refresh = useCallback(() => {
    getAuthStatus()
      .then((next) => {
        setStatus(next)
        setUnreachable(false)
      })
      .catch(() => setUnreachable(true))
  }, [])

  useEffect(() => refresh(), [refresh])

  useEffect(() => {
    // A 401 from a normal API call means the session is gone; fall back to the login form.
    setUnauthorizedHandler(() =>
      setStatus((current) =>
        current === null ? current : { ...current, authenticated: false },
      ),
    )
    return () => setUnauthorizedHandler(null)
  }, [])

  if (unreachable) {
    return (
      <AuthShell>
        <p className="auth__error" role="alert">
          Cannot reach the hub.
        </p>
        <button type="button" onClick={refresh}>
          Retry
        </button>
      </AuthShell>
    )
  }
  if (status === null) {
    return (
      <AuthShell>
        <p className="auth__status">Loading…</p>
      </AuthShell>
    )
  }
  if (!status.configured) {
    return (
      <AuthShell>
        <SetupForm onDone={refresh} />
      </AuthShell>
    )
  }
  if (!status.authenticated) {
    return (
      <AuthShell>
        <LoginForm onLoggedIn={refresh} />
      </AuthShell>
    )
  }
  return <>{children}</>
}

function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="auth">
      <section className="auth__card">
        <h1 className="auth__brand">SmartHome</h1>
        {children}
      </section>
    </div>
  )
}

function SetupForm({ onDone }: { onDone: () => void }) {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await setupAdmin(username.trim(), password)
      await login(username.trim(), password)
      onDone()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Something went wrong')
      setBusy(false)
    }
  }

  return (
    <form className="auth__form" onSubmit={(event) => void submit(event)}>
      <p className="auth__hint">Create the administrator account for this hub.</p>
      {error !== null && (
        <p className="auth__error" role="alert">
          {error}
        </p>
      )}
      <label className="auth__field">
        Username
        <input
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          autoComplete="username"
          disabled={busy}
        />
      </label>
      <label className="auth__field">
        Password
        <input
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="new-password"
          disabled={busy}
        />
      </label>
      <button
        type="submit"
        disabled={busy || username.trim() === '' || password.length < MIN_PASSWORD_LENGTH}
      >
        Create account
      </button>
      <p className="auth__note">At least {MIN_PASSWORD_LENGTH} characters.</p>
    </form>
  )
}

function LoginForm({ onLoggedIn }: { onLoggedIn: () => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(username.trim(), password)
      onLoggedIn()
    } catch {
      setError('Incorrect username or password.')
      setBusy(false)
    }
  }

  return (
    <form className="auth__form" onSubmit={(event) => void submit(event)}>
      {error !== null && (
        <p className="auth__error" role="alert">
          {error}
        </p>
      )}
      <label className="auth__field">
        Username
        <input
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          autoComplete="username"
          disabled={busy}
        />
      </label>
      <label className="auth__field">
        Password
        <input
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="current-password"
          disabled={busy}
        />
      </label>
      <button type="submit" disabled={busy || username.trim() === '' || password === ''}>
        Log in
      </button>
    </form>
  )
}
