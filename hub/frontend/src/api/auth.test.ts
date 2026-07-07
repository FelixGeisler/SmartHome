import { afterEach, describe, expect, it, vi } from 'vitest'
import { getAuthStatus, login, logout, setupAdmin } from './auth'

// Test-only value; not a real credential.
const PW = 'open-sesame-1'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('auth api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('reads the status from GET /api/auth/status', async () => {
    const status = { configured: true, authenticated: false, username: 'admin' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(status))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getAuthStatus()).resolves.toEqual(status)

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/status', undefined)
  })

  it('sets up the administrator via POST /api/auth/setup', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await setupAdmin('admin', PW)

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/setup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: PW }),
    })
  })

  it('logs in with url-encoded credentials via POST /api/auth/login', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await login('admin', PW)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/auth/login')
    expect(init.method).toBe('POST')
    expect((init.body as URLSearchParams).toString()).toBe(
      new URLSearchParams({ username: 'admin', password: PW }).toString(),
    )
  })

  it('logs out via POST /api/auth/logout', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await logout()

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/logout', { method: 'POST' })
  })
})
