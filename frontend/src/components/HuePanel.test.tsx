import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Device } from '../api/devices'
import { registerDevice } from '../api/devices'
import { discoverLights, pairBridge } from '../api/hue'
import { HuePanel } from './HuePanel'

vi.mock('../api/hue')
vi.mock('../api/devices', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/devices')>()),
  registerDevice: vi.fn(),
}))

const lamp: Device = {
  id: 9,
  externalId: '1',
  name: 'Living Room Lamp',
  type: 'HUE_LIGHT',
  capabilities: ['SWITCHABLE'],
  adapterType: 'hue',
  state: {},
  sensors: [],
}

describe('HuePanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('pairs, discovers lights, and registers the selected one', async () => {
    vi.mocked(pairBridge).mockResolvedValue({ paired: true, message: 'paired' })
    vi.mocked(discoverLights).mockResolvedValue([{ id: '1', name: 'Living Room Lamp', on: false }])
    vi.mocked(registerDevice).mockResolvedValue(lamp)
    const onRegistered = vi.fn()
    const user = userEvent.setup()
    render(<HuePanel onRegistered={onRegistered} />)

    await user.type(screen.getByLabelText('Bridge host'), '192.168.1.10')
    await user.click(screen.getByRole('button', { name: /pair/i }))

    await user.click(await screen.findByLabelText('Living Room Lamp'))
    await user.click(screen.getByRole('button', { name: 'Add selected lights' }))

    expect(pairBridge).toHaveBeenCalledWith('192.168.1.10')
    expect(registerDevice).toHaveBeenCalledWith({
      externalId: '1',
      name: 'Living Room Lamp',
      type: 'HUE_LIGHT',
      adapterType: 'hue',
    })
    expect(onRegistered).toHaveBeenCalledWith(lamp)
  })

  it('shows the bridge message when the link button was not pressed', async () => {
    vi.mocked(pairBridge).mockResolvedValue({
      paired: false,
      message: 'Press the bridge link button, then try again.',
    })
    const user = userEvent.setup()
    render(<HuePanel onRegistered={vi.fn()} />)

    await user.type(screen.getByLabelText('Bridge host'), '192.168.1.10')
    await user.click(screen.getByRole('button', { name: /pair/i }))

    expect(await screen.findByText('Press the bridge link button, then try again.')).toBeInTheDocument()
    expect(discoverLights).not.toHaveBeenCalled()
  })

  it('surfaces an error when the bridge is unreachable', async () => {
    vi.mocked(pairBridge).mockRejectedValue(new Error('The Hue bridge is unreachable'))
    const user = userEvent.setup()
    render(<HuePanel onRegistered={vi.fn()} />)

    await user.type(screen.getByLabelText('Bridge host'), '192.168.1.10')
    await user.click(screen.getByRole('button', { name: /pair/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The Hue bridge is unreachable')
  })

  it('clears the previous bridge lights when a re-pair attempt fails', async () => {
    vi.mocked(pairBridge)
      .mockResolvedValueOnce({ paired: true, message: 'paired' })
      .mockResolvedValueOnce({
        paired: false,
        message: 'Press the bridge link button, then try again.',
      })
    vi.mocked(discoverLights).mockResolvedValue([{ id: '1', name: 'Living Room Lamp', on: false }])
    const user = userEvent.setup()
    render(<HuePanel onRegistered={vi.fn()} />)

    // First attempt pairs and discovers a light.
    await user.type(screen.getByLabelText('Bridge host'), '192.168.1.10')
    await user.click(screen.getByRole('button', { name: /pair/i }))
    expect(await screen.findByLabelText('Living Room Lamp')).toBeInTheDocument()

    // Re-pair without pressing the link button: no stale light or paired state may remain.
    await user.click(screen.getByRole('button', { name: 'Re-pair' }))

    expect(
      await screen.findByText('Press the bridge link button, then try again.'),
    ).toBeInTheDocument()
    expect(screen.queryByLabelText('Living Room Lamp')).not.toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Press the link button, then pair' }),
    ).toBeInTheDocument()
  })
})
