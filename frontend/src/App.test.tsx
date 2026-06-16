import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

const heater: Device = {
  ...lamp,
  id: 2,
  externalId: 'shelly-plug-2',
  name: 'Heater',
  state: { on: 'true' },
}

describe('App', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders every device returned by the API', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp, heater])

    render(<App />)

    expect(await screen.findByText('Desk Lamp')).toBeInTheDocument()
    expect(screen.getByText('Heater')).toBeInTheDocument()
  })

  it('shows an empty state when no devices are registered', async () => {
    vi.mocked(listDevices).mockResolvedValue([])

    render(<App />)

    expect(await screen.findByText('No devices registered yet.')).toBeInTheDocument()
  })

  it('renders a sensor readings card for a sensing device', async () => {
    const sensorNode: Device = {
      ...lamp,
      id: 3,
      externalId: 'node-1',
      name: 'Outdoor Sensor',
      type: 'SENSOR_NODE',
      capabilities: ['SENSING'],
      adapterType: null,
      state: {},
      sensors: [
        {
          key: 'temperature',
          type: 'TEMPERATURE',
          unit: '°C',
          value: '21.5',
          updatedAt: '2026-06-15T12:00:00Z',
        },
      ],
    }
    vi.mocked(listDevices).mockResolvedValue([sensorNode])

    render(<App />)

    expect(await screen.findByText('Outdoor Sensor')).toBeInTheDocument()
    expect(screen.getByText('temperature')).toBeInTheDocument()
    expect(screen.getByText('21.5 °C')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^turn /i })).not.toBeInTheDocument()
  })

  it('toggles a device and renders its new state', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp])
    vi.mocked(toggleDevice).mockResolvedValue({ ...lamp, state: { on: 'true' } })
    const user = userEvent.setup()
    render(<App />)

    await user.click(await screen.findByRole('button', { name: 'Turn Desk Lamp on' }))

    expect(toggleDevice).toHaveBeenCalledWith(lamp.id)
    expect(await screen.findByRole('button', { name: 'Turn Desk Lamp off' })).toBeInTheDocument()
  })

  it('updates only the toggled device in a multi-device list', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp, heater])
    vi.mocked(toggleDevice).mockResolvedValue({ ...lamp, state: { on: 'true' } })
    const user = userEvent.setup()
    render(<App />)

    await user.click(await screen.findByRole('button', { name: 'Turn Desk Lamp on' }))

    expect(await screen.findByRole('button', { name: 'Turn Desk Lamp off' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Turn Heater off' })).toBeInTheDocument()
  })

  it('disables the toggle button while its request is in flight', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp])
    let resolveToggle: (device: Device) => void = () => {}
    vi.mocked(toggleDevice).mockImplementation(
      () =>
        new Promise<Device>((resolve) => {
          resolveToggle = resolve
        }),
    )
    const user = userEvent.setup()
    render(<App />)

    await user.click(await screen.findByRole('button', { name: 'Turn Desk Lamp on' }))

    expect(screen.getByRole('button', { name: 'Turn Desk Lamp on' })).toBeDisabled()
    resolveToggle({ ...lamp, state: { on: 'true' } })
    expect(await screen.findByRole('button', { name: 'Turn Desk Lamp off' })).toBeEnabled()
  })

  it('reloads the devices when Retry is clicked after a failed load', async () => {
    vi.mocked(listDevices)
      .mockRejectedValueOnce(new Error('Request failed with status 503'))
      .mockResolvedValueOnce([lamp])
    const user = userEvent.setup()
    render(<App />)

    await user.click(await screen.findByRole('button', { name: 'Retry' }))

    expect(await screen.findByText('Desk Lamp')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
  })

  it('surfaces an error when loading devices fails', async () => {
    vi.mocked(listDevices).mockRejectedValue(new Error('Request failed with status 500'))

    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Request failed with status 500')
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('registers a new device and adds it to the list', async () => {
    vi.mocked(listDevices).mockResolvedValue([])
    vi.mocked(registerDevice).mockResolvedValue(heater)
    const user = userEvent.setup()
    render(<App />)

    await user.type(await screen.findByLabelText('Name'), 'Heater')
    await user.type(screen.getByLabelText('Host'), '192.168.1.51')
    await user.click(screen.getByRole('button', { name: 'Add device' }))

    expect(registerDevice).toHaveBeenCalledWith({
      externalId: '192.168.1.51',
      name: 'Heater',
      type: 'SHELLY_PLUG',
      adapterType: 'shelly',
    })
    expect(await screen.findByText('Heater')).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveValue('')
    expect(screen.getByLabelText('Host')).toHaveValue('')
  })

  it('registers a sensor node with its declared sensors', async () => {
    vi.mocked(listDevices).mockResolvedValue([])
    const sensorNode: Device = {
      ...lamp,
      id: 4,
      externalId: 'living-room',
      name: 'Climate',
      type: 'SENSOR_NODE',
      capabilities: ['SENSING'],
      adapterType: null,
      state: {},
      sensors: [{ key: 'temperature', type: 'TEMPERATURE', unit: '°C', value: null, updatedAt: null }],
    }
    vi.mocked(registerDevice).mockResolvedValue(sensorNode)
    const user = userEvent.setup()
    render(<App />)

    await user.selectOptions(
      await screen.findByLabelText('Kind'),
      screen.getByRole('option', { name: 'Sensor Node (MQTT)' }),
    )
    await user.type(screen.getByLabelText('Name'), 'Climate')
    await user.type(screen.getByLabelText('Node ID'), 'living-room')
    await user.click(screen.getByRole('button', { name: 'Add device' }))

    expect(registerDevice).toHaveBeenCalledWith({
      externalId: 'living-room',
      name: 'Climate',
      type: 'SENSOR_NODE',
      sensors: [{ key: 'temperature', type: 'TEMPERATURE', unit: '°C' }],
    })
    expect(await screen.findByText('Climate')).toBeInTheDocument()
  })

  it('shows the API error when registration fails', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp])
    vi.mocked(registerDevice).mockRejectedValue(
      new Error("Device with external id '192.168.1.50' already exists"),
    )
    const user = userEvent.setup()
    render(<App />)

    await user.type(await screen.findByLabelText('Name'), 'Lamp Again')
    await user.type(screen.getByLabelText('Host'), '192.168.1.50')
    await user.click(screen.getByRole('button', { name: 'Add device' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      "Device with external id '192.168.1.50' already exists",
    )
    expect(screen.getByLabelText('Name')).toHaveValue('Lamp Again')
  })

  it('keeps the device list usable when a toggle fails', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp])
    vi.mocked(toggleDevice).mockRejectedValue(new Error('Device with id 1 not found'))
    const user = userEvent.setup()
    render(<App />)

    await user.click(await screen.findByRole('button', { name: 'Turn Desk Lamp on' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Device with id 1 not found')
    expect(screen.getByRole('button', { name: 'Turn Desk Lamp on' })).toBeEnabled()
  })
})
