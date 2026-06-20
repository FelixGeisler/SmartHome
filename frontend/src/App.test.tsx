import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Device } from './api/devices'
import { listDevices, registerDevice, toggleDevice } from './api/devices'
import App from './App'

// Mock only the HTTP functions; the pure helpers (isSwitchable, isOn) stay real.
vi.mock('./api/devices', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./api/devices')>()),
  listDevices: vi.fn(),
  registerDevice: vi.fn(),
  toggleDevice: vi.fn(),
}))

const lamp: Device = {
  id: 1,
  externalId: 'shelly-plug-1',
  name: 'Desk Lamp',
  type: 'SHELLY_PLUG',
  capabilities: ['SWITCHABLE'],
  adapterType: 'shelly',
  state: {},
  sensors: [],
}

const heater: Device = { ...lamp, id: 2, externalId: 'shelly-plug-2', name: 'Heater', state: { on: 'true' } }

function renderApp() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <App />
    </MemoryRouter>,
  )
}

describe('App', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('shows the dashboard with the loaded devices by default', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp, heater])

    renderApp()

    expect(await screen.findByText('Desk Lamp')).toBeInTheDocument()
    expect(screen.getByText('Heater')).toBeInTheDocument()
  })

  it('toggles a device and renders its new state', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp])
    vi.mocked(toggleDevice).mockResolvedValue({ ...lamp, state: { on: 'true' } })
    const user = userEvent.setup()
    renderApp()

    await user.click(await screen.findByRole('button', { name: 'Turn Desk Lamp on' }))

    expect(toggleDevice).toHaveBeenCalledWith(lamp.id)
    expect(await screen.findByRole('button', { name: 'Turn Desk Lamp off' })).toBeInTheDocument()
  })

  it('navigates to the configuration view', async () => {
    vi.mocked(listDevices).mockResolvedValue([])
    const user = userEvent.setup()
    renderApp()
    await screen.findByText('No devices yet — add one in Configuration.')

    await user.click(screen.getByRole('link', { name: 'Configuration' }))

    expect(screen.getByRole('heading', { name: 'Add device' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Pair a Hue bridge' })).toBeInTheDocument()
  })

  it('adds a device in Configuration and shows it back on the Dashboard', async () => {
    vi.mocked(listDevices).mockResolvedValue([])
    vi.mocked(registerDevice).mockResolvedValue(heater)
    const user = userEvent.setup()
    renderApp()
    await screen.findByText('No devices yet — add one in Configuration.')

    await user.click(screen.getByRole('link', { name: 'Configuration' }))
    await user.type(screen.getByLabelText('Name'), 'Heater')
    await user.type(screen.getByLabelText('Host'), '192.168.1.51')
    await user.click(screen.getByRole('button', { name: 'Add device' }))

    await user.click(screen.getByRole('link', { name: 'Dashboard' }))

    expect(await screen.findByText('Heater')).toBeInTheDocument()
  })

  it('surfaces a load error with a Retry that reloads', async () => {
    vi.mocked(listDevices)
      .mockRejectedValueOnce(new Error('Request failed with status 503'))
      .mockResolvedValueOnce([lamp])
    const user = userEvent.setup()
    renderApp()

    expect(await screen.findByRole('alert')).toHaveTextContent('Request failed with status 503')
    await user.click(screen.getByRole('button', { name: 'Retry' }))

    expect(await screen.findByText('Desk Lamp')).toBeInTheDocument()
  })

  it('updates only the toggled device in a multi-device list', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp, heater])
    vi.mocked(toggleDevice).mockResolvedValue({ ...lamp, state: { on: 'true' } })
    const user = userEvent.setup()
    renderApp()

    await user.click(await screen.findByRole('button', { name: 'Turn Desk Lamp on' }))

    expect(await screen.findByRole('button', { name: 'Turn Desk Lamp off' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Turn Heater off' })).toBeInTheDocument()
  })

  it('disables the toggle while its request is in flight', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp])
    let resolveToggle: (device: Device) => void = () => {}
    vi.mocked(toggleDevice).mockImplementation(
      () =>
        new Promise<Device>((resolve) => {
          resolveToggle = resolve
        }),
    )
    const user = userEvent.setup()
    renderApp()

    await user.click(await screen.findByRole('button', { name: 'Turn Desk Lamp on' }))

    expect(screen.getByRole('button', { name: 'Turn Desk Lamp on' })).toBeDisabled()
    resolveToggle({ ...lamp, state: { on: 'true' } })
    expect(await screen.findByRole('button', { name: 'Turn Desk Lamp off' })).toBeEnabled()
  })

  it('keeps the device list usable when a toggle fails', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp])
    vi.mocked(toggleDevice).mockRejectedValue(new Error('Device with id 1 not found'))
    const user = userEvent.setup()
    renderApp()

    await user.click(await screen.findByRole('button', { name: 'Turn Desk Lamp on' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Device with id 1 not found')
    expect(screen.getByRole('button', { name: 'Turn Desk Lamp on' })).toBeEnabled()
  })
})
