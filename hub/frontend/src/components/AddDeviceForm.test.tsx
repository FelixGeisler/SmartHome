import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Device } from '../api/devices'
import { registerDevice } from '../api/devices'
import { AddDeviceForm } from './AddDeviceForm'

vi.mock('../api/devices', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/devices')>()),
  registerDevice: vi.fn(),
}))

const heater: Device = {
  id: 2,
  externalId: 'shelly-plug-2',
  name: 'Heater',
  type: 'SHELLY_PLUG',
  capabilities: ['SWITCHABLE'],
  adapterType: 'shelly',
  state: { on: 'true' },
  sensors: [],
}

describe('AddDeviceForm', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('registers a plug and reports it to the parent, then clears the form', async () => {
    vi.mocked(registerDevice).mockResolvedValue(heater)
    const onRegistered = vi.fn()
    const user = userEvent.setup()
    render(<AddDeviceForm onRegistered={onRegistered} />)

    await user.type(screen.getByLabelText('Name'), 'Heater')
    await user.type(screen.getByLabelText('Host'), '192.168.1.51')
    await user.click(screen.getByRole('button', { name: 'Add device' }))

    expect(registerDevice).toHaveBeenCalledWith({
      externalId: '192.168.1.51',
      name: 'Heater',
      type: 'SHELLY_PLUG',
      adapterType: 'shelly',
    })
    expect(onRegistered).toHaveBeenCalledWith(heater)
    expect(screen.getByLabelText('Name')).toHaveValue('')
    expect(screen.getByLabelText('Host')).toHaveValue('')
  })

  it('registers a sensor node with its declared sensors', async () => {
    const sensorNode: Device = {
      ...heater,
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
    render(<AddDeviceForm onRegistered={vi.fn()} />)

    await user.selectOptions(
      screen.getByLabelText('Kind'),
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
  })

  it('shows the API error when registration fails and keeps the input', async () => {
    vi.mocked(registerDevice).mockRejectedValue(
      new Error("Device with external id '192.168.1.50' already exists"),
    )
    const user = userEvent.setup()
    render(<AddDeviceForm onRegistered={vi.fn()} />)

    await user.type(screen.getByLabelText('Name'), 'Lamp Again')
    await user.type(screen.getByLabelText('Host'), '192.168.1.50')
    await user.click(screen.getByRole('button', { name: 'Add device' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      "Device with external id '192.168.1.50' already exists",
    )
    expect(screen.getByLabelText('Name')).toHaveValue('Lamp Again')
  })
})
