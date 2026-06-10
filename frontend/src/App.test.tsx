import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Device } from './api/devices'
import { listDevices, registerDevice, toggleDevice } from './api/devices'
import App from './App'

vi.mock('./api/devices')

const lamp: Device = {
  id: 1,
  externalId: 'shelly-plug-1',
  name: 'Desk Lamp',
  type: 'SHELLY_PLUG',
  adapterType: 'shelly',
  on: false,
}

const heater: Device = {
  ...lamp,
  id: 2,
  externalId: 'shelly-plug-2',
  name: 'Heater',
  on: true,
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

  it('toggles a device and renders its new state', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp])
    vi.mocked(toggleDevice).mockResolvedValue({ ...lamp, on: true })
    const user = userEvent.setup()
    render(<App />)

    await user.click(await screen.findByRole('button', { name: 'Turn Desk Lamp on' }))

    expect(toggleDevice).toHaveBeenCalledWith(lamp.id)
    expect(await screen.findByRole('button', { name: 'Turn Desk Lamp off' })).toBeInTheDocument()
  })

  it('updates only the toggled device in a multi-device list', async () => {
    vi.mocked(listDevices).mockResolvedValue([lamp, heater])
    vi.mocked(toggleDevice).mockResolvedValue({ ...lamp, on: true })
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
    resolveToggle({ ...lamp, on: true })
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
