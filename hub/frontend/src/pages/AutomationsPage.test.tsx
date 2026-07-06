import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Automation } from '../api/automations'
import {
  createAutomation,
  deleteAutomation,
  listAutomations,
  runAutomation,
  setAutomationEnabled,
} from '../api/automations'
import type { Device } from '../api/devices'
import { AutomationsPage } from './AutomationsPage'

vi.mock('../api/automations', () => ({
  listAutomations: vi.fn(),
  createAutomation: vi.fn(),
  updateAutomation: vi.fn(),
  setAutomationEnabled: vi.fn(),
  runAutomation: vi.fn(),
  deleteAutomation: vi.fn(),
}))

const sensorNode: Device = {
  id: 10,
  externalId: 'node-1',
  name: 'Office Sensor',
  type: 'SENSOR_NODE',
  capabilities: ['SENSING'],
  adapterType: null,
  state: {},
  sensors: [{ key: 'temperature', type: 'TEMPERATURE', unit: '°C', value: '21', updatedAt: null }],
}

const fan: Device = {
  id: 20,
  externalId: 'plug-1',
  name: 'Fan',
  type: 'SHELLY_PLUG',
  capabilities: ['SWITCHABLE'],
  adapterType: 'shelly',
  state: {},
  sensors: [],
}

const sample: Automation = {
  id: 1,
  name: 'Vent the office',
  enabled: true,
  triggers: [
    {
      kind: 'SENSOR_THRESHOLD',
      deviceId: 10,
      sensorKey: 'temperature',
      comparison: 'GREATER_THAN',
      threshold: 25,
      atTime: null,
      onDays: [],
    },
  ],
  conditions: [],
  actions: [{ kind: 'DEVICE_TOGGLE', deviceId: 20, on: null, brightness: null, colorTemperatureK: null }],
}

function renderPage() {
  return render(<AutomationsPage devices={[sensorNode, fan]} />)
}

describe('AutomationsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(listAutomations).mockResolvedValue([])
  })

  it('shows an empty state when there are no automations', async () => {
    renderPage()

    expect(await screen.findByText(/No automations yet/)).toBeInTheDocument()
  })

  it('lists an automation with a readable summary', async () => {
    vi.mocked(listAutomations).mockResolvedValue([sample])

    renderPage()

    expect(await screen.findByText('Vent the office')).toBeInTheDocument()
    expect(
      screen.getByText('When Office Sensor temperature > 25, then toggle Fan'),
    ).toBeInTheDocument()
  })

  it('builds and saves a new automation', async () => {
    vi.mocked(createAutomation).mockResolvedValue(sample)
    const user = userEvent.setup()
    renderPage()
    await screen.findByText(/No automations yet/)

    await user.click(screen.getByRole('button', { name: 'New automation' }))
    await user.type(screen.getByLabelText('Name'), 'Vent the office')
    await user.selectOptions(screen.getByLabelText('Trigger device'), '10')
    await user.selectOptions(screen.getByLabelText('Trigger sensor'), 'temperature')
    await user.type(screen.getByLabelText('Threshold'), '25')
    await user.click(screen.getByRole('button', { name: 'Add action' }))
    await user.selectOptions(screen.getByLabelText('Action 1 device'), '20')
    await user.click(screen.getByRole('button', { name: 'Save automation' }))

    await waitFor(() =>
      expect(createAutomation).toHaveBeenCalledWith({
        name: 'Vent the office',
        enabled: true,
        triggers: [
          {
            kind: 'SENSOR_THRESHOLD',
            deviceId: 10,
            sensorKey: 'temperature',
            comparison: 'GREATER_THAN',
            threshold: 25,
            atTime: null,
            onDays: [],
          },
        ],
        conditions: [],
        actions: [
          { kind: 'DEVICE_TOGGLE', deviceId: 20, on: null, brightness: null, colorTemperatureK: null },
        ],
      }),
    )
  })

  it('builds and saves a schedule automation', async () => {
    vi.mocked(createAutomation).mockResolvedValue(sample)
    const user = userEvent.setup()
    renderPage()
    await screen.findByText(/No automations yet/)

    await user.click(screen.getByRole('button', { name: 'New automation' }))
    await user.type(screen.getByLabelText('Name'), 'Morning')
    await user.selectOptions(screen.getByLabelText('Trigger type'), 'SCHEDULE')
    fireEvent.change(screen.getByLabelText('Schedule time'), { target: { value: '07:30' } })
    await user.click(screen.getByLabelText('MONDAY'))
    await user.click(screen.getByRole('button', { name: 'Add action' }))
    await user.selectOptions(screen.getByLabelText('Action 1 device'), '20')
    await user.click(screen.getByRole('button', { name: 'Save automation' }))

    await waitFor(() =>
      expect(createAutomation).toHaveBeenCalledWith({
        name: 'Morning',
        enabled: true,
        triggers: [
          {
            kind: 'SCHEDULE',
            deviceId: null,
            sensorKey: null,
            comparison: null,
            threshold: null,
            atTime: '07:30',
            onDays: ['MONDAY'],
          },
        ],
        conditions: [],
        actions: [
          { kind: 'DEVICE_TOGGLE', deviceId: 20, on: null, brightness: null, colorTemperatureK: null },
        ],
      }),
    )
  })

  it('blocks saving an incomplete automation and explains why', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText(/No automations yet/)

    await user.click(screen.getByRole('button', { name: 'New automation' }))
    await user.click(screen.getByRole('button', { name: 'Save automation' }))

    expect(screen.getByRole('alert')).toHaveTextContent(/name/i)
    expect(createAutomation).not.toHaveBeenCalled()
  })

  it('runs an automation on demand and reports it', async () => {
    vi.mocked(listAutomations).mockResolvedValue([sample])
    vi.mocked(runAutomation).mockResolvedValue()
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Vent the office')

    await user.click(screen.getByRole('button', { name: 'Run now' }))

    expect(runAutomation).toHaveBeenCalledWith(1)
    expect(await screen.findByRole('status')).toHaveTextContent('Ran "Vent the office"')
  })

  it('toggles an automation off', async () => {
    vi.mocked(listAutomations).mockResolvedValue([sample])
    vi.mocked(setAutomationEnabled).mockResolvedValue({ ...sample, enabled: false })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Vent the office')

    await user.click(screen.getByLabelText('Enable Vent the office'))

    expect(setAutomationEnabled).toHaveBeenCalledWith(1, false)
  })

  it('deletes an automation', async () => {
    vi.mocked(listAutomations).mockResolvedValue([sample])
    vi.mocked(deleteAutomation).mockResolvedValue()
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Vent the office')

    await user.click(screen.getByRole('button', { name: 'Delete' }))

    expect(deleteAutomation).toHaveBeenCalledWith(1)
    await waitFor(() => expect(screen.queryByText('Vent the office')).not.toBeInTheDocument())
  })

  it('opens the builder pre-filled when editing', async () => {
    vi.mocked(listAutomations).mockResolvedValue([sample])
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Vent the office')

    await user.click(screen.getByRole('button', { name: 'Edit' }))

    expect(screen.getByLabelText('Name')).toHaveValue('Vent the office')
    expect(screen.getByLabelText('Threshold')).toHaveValue(25)
  })
})
