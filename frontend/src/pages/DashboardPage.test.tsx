import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Device } from '../api/devices'
import { DashboardPage } from './DashboardPage'

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

  it('renders every device when ready', () => {
    render(
      <DashboardPage
        devices={[lamp, heater]}
        loadState="ready"
        error={null}
        busyIds={new Set()}
        onToggle={vi.fn()}
        onCommand={vi.fn()}
        onDelete={vi.fn()}
        onRetry={vi.fn()}
      />,
    )

    expect(screen.getByText('Desk Lamp')).toBeInTheDocument()
    expect(screen.getByText('Heater')).toBeInTheDocument()
  })

  it('shows an empty state pointing to Configuration', () => {
    render(
      <DashboardPage
        devices={[]}
        loadState="ready"
        error={null}
        busyIds={new Set()}
        onToggle={vi.fn()}
        onCommand={vi.fn()}
        onDelete={vi.fn()}
        onRetry={vi.fn()}
      />,
    )

    expect(screen.getByText('No devices yet — add one in Configuration.')).toBeInTheDocument()
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
    render(
      <DashboardPage
        devices={[sensorNode]}
        loadState="ready"
        error={null}
        busyIds={new Set()}
        onToggle={vi.fn()}
        onCommand={vi.fn()}
        onDelete={vi.fn()}
        onRetry={vi.fn()}
      />,
    )

    expect(screen.getByText('temperature')).toBeInTheDocument()
    expect(screen.getByText('21.5 °C')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^turn /i })).not.toBeInTheDocument()
  })

  it('calls onToggle with the device when its toggle is clicked', async () => {
    const onToggle = vi.fn()
    const user = userEvent.setup()
    render(
      <DashboardPage
        devices={[lamp]}
        loadState="ready"
        error={null}
        busyIds={new Set()}
        onToggle={onToggle}
        onCommand={vi.fn()}
        onDelete={vi.fn()}
        onRetry={vi.fn()}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Turn Desk Lamp on' }))

    expect(onToggle).toHaveBeenCalledWith(lamp)
  })

  it('disables the toggle for a busy device', () => {
    render(
      <DashboardPage
        devices={[lamp]}
        loadState="ready"
        error={null}
        busyIds={new Set([lamp.id])}
        onToggle={vi.fn()}
        onCommand={vi.fn()}
        onDelete={vi.fn()}
        onRetry={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: 'Turn Desk Lamp on' })).toBeDisabled()
  })

  it('shows the error and calls onRetry when Retry is clicked', async () => {
    const onRetry = vi.fn()
    const user = userEvent.setup()
    render(
      <DashboardPage
        devices={[]}
        loadState="error"
        error="Request failed with status 500"
        busyIds={new Set()}
        onToggle={vi.fn()}
        onCommand={vi.fn()}
        onDelete={vi.fn()}
        onRetry={onRetry}
      />,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('Request failed with status 500')
    await user.click(screen.getByRole('button', { name: 'Retry' }))

    expect(onRetry).toHaveBeenCalled()
  })
})
