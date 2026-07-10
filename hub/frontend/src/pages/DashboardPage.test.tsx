import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ComponentProps, ReactNode } from 'react'
import type { Layout } from 'react-grid-layout'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Device } from '../api/devices'
import { DashboardPage } from './DashboardPage'

// Render react-grid-layout as a plain container: RGL's DOM measurement and drag internals don't run under jsdom.
vi.mock('react-grid-layout', () => ({
  __esModule: true,
  default: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  useContainerWidth: () => ({ width: 1200, containerRef: { current: null }, mounted: true }),
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

const heater: Device = { ...lamp, id: 2, name: 'Heater', state: { on: 'true' } }
const fan: Device = { ...lamp, id: 3, name: 'Fan' }

function layoutFor(devices: Device[]): Layout {
  return devices.map((device, index) => ({ i: String(device.id), x: index, y: 0, w: 4, h: 7 }))
}

function renderDashboard(overrides: Partial<ComponentProps<typeof DashboardPage>> = {}) {
  const devices = overrides.devices ?? [lamp, heater]
  const props: ComponentProps<typeof DashboardPage> = {
    devices,
    layout: layoutFor(devices),
    cardLayouts: devices.map((device) => ({ deviceId: device.id, x: 0, y: 0, w: 4, h: 7 })),
    loadState: 'ready',
    error: null,
    busyIds: new Set<number>(),
    editing: false,
    addable: [],
    onToggle: vi.fn(),
    onCommand: vi.fn(),
    onRemoveCard: vi.fn(),
    onToggleSensor: vi.fn(),
    onRetry: vi.fn(),
    onEnterEdit: vi.fn(),
    onSave: vi.fn(),
    onCancel: vi.fn(),
    onLayoutChange: vi.fn(),
    onAddCard: vi.fn(),
    ...overrides,
  }
  render(<DashboardPage {...props} />)
  return props
}

describe('DashboardPage', () => {
  // A sensing device's SensorChart reads its history on mount; stub the call to an empty series.
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } }),
      ),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders every shown device when ready', () => {
    renderDashboard()

    expect(screen.getByText('Desk Lamp')).toBeInTheDocument()
    expect(screen.getByText('Heater')).toBeInTheDocument()
  })

  it('shows an empty state when there are no devices', () => {
    renderDashboard({ devices: [], layout: [] })

    expect(screen.getByText('No devices yet. Register one in Configuration.')).toBeInTheDocument()
  })

  it('renders sensor readings for a sensing device', () => {
    const sensorNode: Device = {
      ...lamp,
      id: 3,
      name: 'Outdoor Sensor',
      type: 'SENSOR_NODE',
      capabilities: ['SENSING'],
      adapterType: null,
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
    renderDashboard({ devices: [sensorNode] })

    expect(screen.getByText('temperature')).toBeInTheDocument()
    expect(screen.getByText('21.5 °C')).toBeInTheDocument()
  })

  it('calls onToggle with the device when its toggle is clicked', async () => {
    const user = userEvent.setup()
    const props = renderDashboard({ devices: [lamp] })

    await user.click(screen.getByRole('button', { name: 'Turn Desk Lamp on' }))

    expect(props.onToggle).toHaveBeenCalledWith(lamp)
  })

  it('hides the card remove button until editing', () => {
    renderDashboard({ devices: [lamp] })

    expect(screen.queryByRole('button', { name: 'Remove Desk Lamp' })).not.toBeInTheDocument()
  })

  it('removes a card in edit mode', async () => {
    const user = userEvent.setup()
    const props = renderDashboard({ devices: [lamp], editing: true })

    await user.click(screen.getByRole('button', { name: 'Remove Desk Lamp' }))

    expect(props.onRemoveCard).toHaveBeenCalledWith(lamp)
  })

  it('hides a deselected sensor chart and toggles it from the chart chips in edit mode', async () => {
    const user = userEvent.setup()
    const sensorNode: Device = {
      ...lamp,
      id: 3,
      name: 'Sensor',
      type: 'SENSOR_NODE',
      capabilities: ['SENSING'],
      adapterType: null,
      sensors: [
        {
          key: 'temperature',
          type: 'TEMPERATURE',
          unit: '°C',
          value: '21',
          updatedAt: '2026-06-15T12:00:00Z',
        },
        {
          key: 'humidity',
          type: 'HUMIDITY',
          unit: '%',
          value: '40',
          updatedAt: '2026-06-15T12:00:00Z',
        },
      ],
    }
    const props = renderDashboard({
      devices: [sensorNode],
      editing: true,
      cardLayouts: [{ deviceId: 3, x: 0, y: 0, w: 4, h: 7, hiddenSensors: ['humidity'] }],
    })

    expect(screen.getByText('21 °C')).toBeInTheDocument()
    expect(screen.queryByText('40 %')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Show humidity chart' }))

    expect(props.onToggleSensor).toHaveBeenCalledWith(3, 'humidity')
  })

  it('disables the toggle for a busy device', () => {
    renderDashboard({ devices: [lamp], busyIds: new Set([lamp.id]) })

    expect(screen.getByRole('button', { name: 'Turn Desk Lamp on' })).toBeDisabled()
  })

  it('shows the error and calls onRetry when Retry is clicked', async () => {
    const user = userEvent.setup()
    const props = renderDashboard({
      devices: [],
      layout: [],
      loadState: 'error',
      error: 'Request failed with status 500',
    })

    expect(screen.getByRole('alert')).toHaveTextContent('Request failed with status 500')
    await user.click(screen.getByRole('button', { name: 'Retry' }))

    expect(props.onRetry).toHaveBeenCalled()
  })

  it('enters edit mode from the toolbar', async () => {
    const user = userEvent.setup()
    const props = renderDashboard()

    await user.click(screen.getByRole('button', { name: 'Edit layout' }))

    expect(props.onEnterEdit).toHaveBeenCalled()
  })

  it('saves and cancels the layout from the toolbar', async () => {
    const user = userEvent.setup()
    const props = renderDashboard({ editing: true })

    await user.click(screen.getByRole('button', { name: 'Save layout' }))
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(props.onSave).toHaveBeenCalled()
    expect(props.onCancel).toHaveBeenCalled()
  })

  it('adds a card from the picker in edit mode', async () => {
    const user = userEvent.setup()
    const props = renderDashboard({ editing: true, addable: [fan] })

    await user.click(screen.getByRole('button', { name: 'Add card' }))
    const dialog = screen.getByRole('dialog', { name: 'Add a card' })
    await user.click(within(dialog).getByRole('button', { name: 'Fan' }))

    expect(props.onAddCard).toHaveBeenCalledWith(fan)
  })
})
