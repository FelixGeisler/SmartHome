import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Device } from './api/devices'
import { listDevices, registerDevice, sendCommand, toggleDevice } from './api/devices'
import { type DeviceStreamHandlers, openDeviceStream } from './api/events'
import App from './App'

// Mock only the HTTP functions; the pure helpers (isSwitchable, isOn) stay real.
vi.mock('./api/devices', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./api/devices')>()),
  listDevices: vi.fn(),
  registerDevice: vi.fn(),
  toggleDevice: vi.fn(),
  sendCommand: vi.fn(),
}))

// The live stream is opened by App; mock it so tests can drive events directly. A test that needs
// to push events overrides the implementation to capture the handlers.
vi.mock('./api/events', () => ({
  openDeviceStream: vi.fn(() => () => {}),
}))

// Render react-grid-layout as a plain container so the dashboard's cards render under jsdom without
// RGL's DOM measurement and drag internals (which don't run there).
vi.mock('react-grid-layout', () => ({
  __esModule: true,
  default: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  useContainerWidth: () => ({ width: 1200, containerRef: { current: null }, mounted: true }),
}))

/** Renders App and returns the handlers it registered with the (mocked) event stream. */
function captureStreamHandlers(): () => DeviceStreamHandlers {
  let handlers: DeviceStreamHandlers | undefined
  vi.mocked(openDeviceStream).mockImplementation((registered) => {
    handlers = registered
    return () => {}
  })
  return () => {
    if (handlers === undefined) {
      throw new Error('the event stream was never opened')
    }
    return handlers
  }
}

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
    await screen.findByText('No devices yet. Register one in Configuration.')

    await user.click(screen.getByRole('link', { name: 'Configuration' }))

    expect(screen.getByRole('heading', { name: 'Add device' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Pair a Hue bridge' })).toBeInTheDocument()
  })

  it('adds a device in Configuration and shows it back on the Dashboard', async () => {
    vi.mocked(listDevices).mockResolvedValue([])
    vi.mocked(registerDevice).mockResolvedValue(heater)
    const user = userEvent.setup()
    renderApp()
    await screen.findByText('No devices yet. Register one in Configuration.')

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

  it('sends a brightness command and renders the new level', async () => {
    const bulb: Device = {
      id: 5,
      externalId: 'light-1',
      name: 'Ceiling',
      type: 'HUE_LIGHT',
      capabilities: ['SWITCHABLE', 'DIMMABLE'],
      adapterType: 'hue',
      state: { on: 'true', brightness: '40' },
      sensors: [],
    }
    vi.mocked(listDevices).mockResolvedValue([bulb])
    vi.mocked(sendCommand).mockResolvedValue({
      ...bulb,
      state: { on: 'true', brightness: '80' },
    })
    renderApp()

    const slider = await screen.findByLabelText('Brightness for Ceiling')
    fireEvent.change(slider, { target: { value: '80' } })

    expect(sendCommand).toHaveBeenCalledWith(5, { brightness: 80 })
    expect(await screen.findByText('80%')).toBeInTheDocument()
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

  it('applies a device change pushed over the event stream', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp])
    const handlers = captureStreamHandlers()
    renderApp()
    await screen.findByText('Desk Lamp')

    act(() => {
      handlers().onDeviceChanged({ ...lamp, state: { on: 'true' } })
    })

    expect(await screen.findByRole('button', { name: 'Turn Desk Lamp off' })).toBeInTheDocument()
  })

  it('does not let an in-flight list fetch clobber a change pushed meanwhile', async () => {
    let resolveList: (devices: Device[]) => void = () => {}
    vi.mocked(listDevices).mockImplementation(
      () =>
        new Promise<Device[]>((resolve) => {
          resolveList = resolve
        }),
    )
    const handlers = captureStreamHandlers()
    renderApp()

    // The initial fetch is still in flight when the stream pushes a newer state...
    act(() => {
      handlers().onDeviceChanged({ ...lamp, state: { on: 'true' } })
    })
    // ...and the fetch then resolves with a snapshot that predates the push.
    act(() => {
      resolveList([lamp])
    })

    expect(await screen.findByRole('button', { name: 'Turn Desk Lamp off' })).toBeInTheDocument()
  })

  it('re-syncs the device list when the stream (re)connects', async () => {
    vi.mocked(listDevices).mockResolvedValueOnce([lamp]).mockResolvedValueOnce([lamp, heater])
    const handlers = captureStreamHandlers()
    renderApp()
    await screen.findByText('Desk Lamp')

    act(() => {
      handlers().onSync()
    })

    expect(await screen.findByText('Heater')).toBeInTheDocument()
    expect(listDevices).toHaveBeenCalledTimes(2)
  })

  it('applies a pushed change even while a command for that device is in flight', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp])
    vi.mocked(toggleDevice).mockImplementation(() => new Promise<Device>(() => {}))
    const handlers = captureStreamHandlers()
    const user = userEvent.setup()
    renderApp()

    await user.click(await screen.findByRole('button', { name: 'Turn Desk Lamp on' }))
    act(() => {
      handlers().onDeviceChanged({ ...lamp, state: { on: 'true' } })
    })

    // The push renders (another client's change must not be hidden); the control stays disabled.
    expect(await screen.findByRole('button', { name: 'Turn Desk Lamp off' })).toBeDisabled()
  })

  it('does not duplicate a registered device the stream already pushed', async () => {
    vi.mocked(listDevices).mockResolvedValue([])
    vi.mocked(registerDevice).mockResolvedValue(heater)
    const handlers = captureStreamHandlers()
    const user = userEvent.setup()
    renderApp()
    await screen.findByText('No devices yet. Register one in Configuration.')

    await user.click(screen.getByRole('link', { name: 'Configuration' }))
    await user.type(screen.getByLabelText('Name'), 'Heater')
    await user.type(screen.getByLabelText('Host'), '192.168.1.51')
    // The hub pushes the new device before the registration response is applied.
    act(() => {
      handlers().onDeviceChanged(heater)
    })
    await user.click(screen.getByRole('button', { name: 'Add device' }))
    await user.click(screen.getByRole('link', { name: 'Dashboard' }))

    expect(await screen.findAllByText('Heater')).toHaveLength(1)
  })

  it('removes a device pushed as device-removed over the event stream', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp, heater])
    const handlers = captureStreamHandlers()
    renderApp()
    await screen.findByText('Desk Lamp')

    act(() => {
      handlers().onDeviceRemoved(lamp.id)
    })

    await waitFor(() => expect(screen.queryByText('Desk Lamp')).not.toBeInTheDocument())
    expect(screen.getByText('Heater')).toBeInTheDocument()
  })

  it('does not let an in-flight list fetch resurrect a device removed meanwhile', async () => {
    let resolveList: (devices: Device[]) => void = () => {}
    vi.mocked(listDevices).mockImplementation(
      () =>
        new Promise<Device[]>((resolve) => {
          resolveList = resolve
        }),
    )
    const handlers = captureStreamHandlers()
    renderApp()

    // The removal is pushed while the initial fetch is in flight...
    act(() => {
      handlers().onDeviceRemoved(lamp.id)
    })
    // ...and the fetch then resolves with a snapshot that still lists the device.
    act(() => {
      resolveList([lamp, heater])
    })

    expect(await screen.findByText('Heater')).toBeInTheDocument()
    expect(screen.queryByText('Desk Lamp')).not.toBeInTheDocument()
  })
})
