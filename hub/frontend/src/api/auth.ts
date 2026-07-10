import { request } from './devices'

export interface AuthStatus {
  /** Whether an administrator has been set up on first start. */
  configured: boolean
  authenticated: boolean
  /** The administrator username, or null before setup. */
  username: string | null
}

export function getAuthStatus(): Promise<AuthStatus> {
  return request<AuthStatus>('/api/auth/status')
}

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

export function logout(): Promise<void> {
  return request<void>('/api/auth/logout', { method: 'POST' })
}
