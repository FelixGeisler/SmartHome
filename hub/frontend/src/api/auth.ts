import { request } from './devices'

/** The hub's view of authentication, used by the login gate. */
export interface AuthStatus {
  /** Whether an administrator has been set up on first start. */
  configured: boolean
  /** Whether the caller has a logged-in session. */
  authenticated: boolean
  /** The administrator username, or null before setup. */
  username: string | null
}

/** Reads whether an administrator exists and whether the caller is logged in. */
export function getAuthStatus(): Promise<AuthStatus> {
  return request<AuthStatus>('/api/auth/status')
}

/** Sets up the administrator on first start. */
export function setupAdmin(username: string, password: string): Promise<void> {
  return request<void>('/api/auth/setup', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
}

/** Logs in with the admin credentials; the session is kept in a cookie. */
export function login(username: string, password: string): Promise<void> {
  // Spring's form login reads url-encoded username/password parameters.
  return request<void>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username, password }),
  })
}

/** Ends the session. */
export function logout(): Promise<void> {
  return request<void>('/api/auth/logout', { method: 'POST' })
}
