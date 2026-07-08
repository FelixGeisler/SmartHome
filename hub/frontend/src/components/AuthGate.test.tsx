import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getAuthStatus, login, setupAdmin } from '../api/auth'
import { AuthGate } from './AuthGate'

vi.mock('../api/auth')
vi.mock('../api/devices', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/devices')>()),
  setUnauthorizedHandler: vi.fn(),
}))

// Test-only value; not a real credential.
const PW = 'open-sesame-1'

describe('AuthGate', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders the app when already authenticated', async () => {
    vi.mocked(getAuthStatus).mockResolvedValue({
      configured: true,
      authenticated: true,
      username: 'admin',
    })

    render(
      <AuthGate>
        <div>Protected app</div>
      </AuthGate>,
    )

    expect(await screen.findByText('Protected app')).toBeInTheDocument()
  })

  it('sets up the administrator on first run, then shows the app', async () => {
    vi.mocked(getAuthStatus)
      .mockResolvedValueOnce({ configured: false, authenticated: false, username: null })
      .mockResolvedValue({ configured: true, authenticated: true, username: 'admin' })
    vi.mocked(setupAdmin).mockResolvedValue(undefined)
    vi.mocked(login).mockResolvedValue(undefined)
    const user = userEvent.setup()

    render(
      <AuthGate>
        <div>Protected app</div>
      </AuthGate>,
    )

    await user.type(await screen.findByLabelText('Password'), PW)
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(setupAdmin).toHaveBeenCalledWith('admin', PW)
    expect(await screen.findByText('Protected app')).toBeInTheDocument()
  })

  it('logs in when an administrator already exists', async () => {
    vi.mocked(getAuthStatus)
      .mockResolvedValueOnce({ configured: true, authenticated: false, username: 'admin' })
      .mockResolvedValue({ configured: true, authenticated: true, username: 'admin' })
    vi.mocked(login).mockResolvedValue(undefined)
    const user = userEvent.setup()

    render(
      <AuthGate>
        <div>Protected app</div>
      </AuthGate>,
    )

    await user.type(await screen.findByLabelText('Username'), 'admin')
    await user.type(screen.getByLabelText('Password'), PW)
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    expect(login).toHaveBeenCalledWith('admin', PW)
    expect(await screen.findByText('Protected app')).toBeInTheDocument()
  })

  it('shows an error when the password is wrong', async () => {
    vi.mocked(getAuthStatus).mockResolvedValue({
      configured: true,
      authenticated: false,
      username: 'admin',
    })
    vi.mocked(login).mockRejectedValue(new Error('nope'))
    const user = userEvent.setup()

    render(
      <AuthGate>
        <div>Protected app</div>
      </AuthGate>,
    )

    await user.type(await screen.findByLabelText('Username'), 'admin')
    await user.type(screen.getByLabelText('Password'), PW)
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect username or password.')
  })
})
