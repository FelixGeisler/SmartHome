import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Device } from './api/devices'
import { listDevices, toggleDevice } from './api/devices'
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

  it('surfaces an error when loading devices fails', async () => {
    vi.mocked(listDevices).mockRejectedValue(new Error('Request failed with status 500'))

    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Request failed with status 500')
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
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
