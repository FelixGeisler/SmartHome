import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Device } from '../api/devices'
import { DeviceCard } from './DeviceCard'

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

function renderCard(device: Device, onCommand = vi.fn(), onToggle = vi.fn()) {
  render(
    <ul>
      <DeviceCard device={device} busy={false} onToggle={onToggle} onCommand={onCommand} />
    </ul>,
  )
  return { onCommand, onToggle }
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
})
