import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Device } from '../api/devices'
import { DeviceCard } from './DeviceCard'

// Stub the chart so the sensor tests here don't reach for a history fetch.
vi.mock('./SensorChart', () => ({ SensorChart: () => null }))

function climateReadAt(agoMs: number): Device {
  return {
    id: 3,
    externalId: 'node-1',
    name: 'Climate',
    type: 'SENSOR_NODE',
    capabilities: ['SENSING'],
    adapterType: null,
    state: {},
    sensors: [
      {
        key: 'temperature',
        type: 'TEMPERATURE',
        unit: '°C',
        value: '21',
        updatedAt: new Date(Date.now() - agoMs).toISOString(),
      },
    ],
  }
}

const bulb: Device = {
  id: 1,
  externalId: 'light-1',
  name: 'Ceiling',
  type: 'HUE_LIGHT',
  capabilities: ['SWITCHABLE', 'DIMMABLE', 'COLOR', 'COLOR_TEMPERATURE'],
  adapterType: 'hue',
  state: { on: 'true', brightness: '40', colorXy: '0.3,0.3', colorTemperatureK: '3000' },
  sensors: [],
}

const plug: Device = {
  id: 2,
  externalId: 'plug-1',
  name: 'Desk Lamp',
  type: 'SHELLY_PLUG',
  capabilities: ['SWITCHABLE'],
  adapterType: 'shelly',
  state: { on: 'false' },
  sensors: [],
}

function renderCard(device: Device, editing = false) {
  const onCommand = vi.fn()
  const onToggle = vi.fn()
  const onRemove = vi.fn()
  render(
    <DeviceCard
      device={device}
      busy={false}
      editing={editing}
      onToggle={onToggle}
      onCommand={onCommand}
      onRemove={onRemove}
    />,
  )
  return { onCommand, onToggle, onRemove }
}

describe('DeviceCard', () => {
  it('renders a control per capability for a rich light', () => {
    renderCard(bulb)

    expect(screen.getByLabelText('Brightness for Ceiling')).toBeInTheDocument()
    expect(screen.getByLabelText('Color for Ceiling')).toBeInTheDocument()
    expect(screen.getByLabelText('Color temperature for Ceiling')).toBeInTheDocument()
  })

  it('commits a brightness command when the slider is released', () => {
    const { onCommand } = renderCard(bulb)

    fireEvent.change(screen.getByLabelText('Brightness for Ceiling'), {
      target: { value: '70' },
    })

    expect(onCommand).toHaveBeenCalledWith(bulb, { brightness: 70 })
  })

  it('commits the chosen color as CIE xy', () => {
    const { onCommand } = renderCard(bulb)

    fireEvent.change(screen.getByLabelText('Color for Ceiling'), {
      target: { value: '#ff0000' },
    })

    expect(onCommand).toHaveBeenCalledTimes(1)
    const command = onCommand.mock.calls[0][1]
    expect(command.colorXy.x).toBeCloseTo(0.64, 2)
    expect(command.colorXy.y).toBeCloseTo(0.33, 2)
  })

  it('renders no light controls for a plain switch', () => {
    renderCard(plug)

    expect(screen.queryByLabelText(/Brightness/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Color/)).not.toBeInTheDocument()
  })

  it('toggles through the existing on/off button', async () => {
    const { onToggle } = renderCard(plug)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Turn Desk Lamp on' }))

    expect(onToggle).toHaveBeenCalledWith(plug)
  })

  it('shows no remove button outside edit mode', () => {
    renderCard(plug)

    expect(screen.queryByRole('button', { name: 'Remove Desk Lamp' })).not.toBeInTheDocument()
  })

  it('removes the card in edit mode', async () => {
    const { onRemove } = renderCard(plug, true)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Remove Desk Lamp' }))

    expect(onRemove).toHaveBeenCalledWith(plug)
  })

  it('shows an offline badge when the device is unreachable', () => {
    renderCard({ ...plug, reachable: false })

    expect(screen.getByText('Offline')).toBeInTheDocument()
  })

  it('shows no offline badge for a reachable device', () => {
    renderCard({ ...plug, reachable: true })

    expect(screen.queryByText('Offline')).not.toBeInTheDocument()
  })

  it('labels a fresh reading as just updated and does not mute it', () => {
    renderCard(climateReadAt(30_000))

    expect(screen.getByText('updated just now')).toBeInTheDocument()
    expect(document.querySelector('.sensor--stale')).toBeNull()
  })

  it('mutes a stale reading and shows how long ago it arrived', () => {
    renderCard(climateReadAt(20 * 60_000))

    expect(screen.getByText('updated 20 min ago')).toBeInTheDocument()
    expect(document.querySelector('.sensor--stale')).not.toBeNull()
  })

  it('shows sensor tiles on a switchable device that also reports readings', () => {
    const meteredPlug: Device = {
      ...plug,
      sensors: [
        {
          key: 'power',
          type: 'POWER',
          unit: 'W',
          value: '12.3',
          updatedAt: new Date().toISOString(),
        },
      ],
    }
    renderCard(meteredPlug)

    expect(screen.getByText('power')).toBeInTheDocument()
    expect(screen.getByText('12.3 W')).toBeInTheDocument()
    // A metered plug is both switchable and sensing, so the toggle stays.
    expect(screen.getByRole('button', { name: 'Turn Desk Lamp on' })).toBeInTheDocument()
  })
})
