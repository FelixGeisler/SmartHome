import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Device } from '../api/devices'
import { CardPicker } from './CardPicker'

const device: Device = {
  id: 1,
  externalId: 'plug-1',
  name: 'Desk Lamp',
  type: 'SHELLY_PLUG',
  capabilities: ['SWITCHABLE'],
  adapterType: 'shelly',
  state: {},
  sensors: [],
}

describe('CardPicker', () => {
  it('renders nothing when closed', () => {
    render(<CardPicker open={false} devices={[device]} onClose={vi.fn()} onAdd={vi.fn()} />)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('lists the addable devices and reports the chosen one', async () => {
    const onAdd = vi.fn()
    const user = userEvent.setup()
    render(<CardPicker open devices={[device]} onClose={vi.fn()} onAdd={onAdd} />)

    await user.click(screen.getByRole('button', { name: 'Desk Lamp' }))

    expect(onAdd).toHaveBeenCalledWith(device)
  })

  it('shows a hint when there is nothing to add', () => {
    render(<CardPicker open devices={[]} onClose={vi.fn()} onAdd={vi.fn()} />)

    expect(screen.getByText(/already on the dashboard/i)).toBeInTheDocument()
  })

  it('closes via the close button', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(<CardPicker open devices={[device]} onClose={onClose} onAdd={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: 'Close' }))

    expect(onClose).toHaveBeenCalled()
  })
})
