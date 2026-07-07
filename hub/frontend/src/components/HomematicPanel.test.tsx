import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Device } from '../api/devices'
import { registerDevice } from '../api/devices'
import { connectCcu, discoverDevices } from '../api/homematic'
import { HomematicPanel } from './HomematicPanel'

vi.mock('../api/homematic')
vi.mock('../api/devices', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/devices')>()),
  registerDevice: vi.fn(),
}))

const plug: Device = {
  id: 10,
  externalId: 'HmIP-RF/0001DD89A4662F:3',
  name: 'Steckdose PC',
  type: 'HOMEMATIC_DEVICE',
  capabilities: ['SWITCHABLE'],
  adapterType: 'homematic',
  state: {},
  sensors: [],
}

async function fillCredentials(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('CCU host'), '192.168.178.84')
  await user.type(screen.getByLabelText('Username'), 'Admin')
  await user.type(screen.getByLabelText('Password'), 'admin')
}

describe('HomematicPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('connects, discovers devices, and registers the selected one', async () => {
    vi.mocked(connectCcu).mockResolvedValue({ connected: true, message: 'ok' })
    vi.mocked(discoverDevices).mockResolvedValue([
      {
        externalId: 'HmIP-RF/0001DD89A4662F:3',
        name: 'Steckdose PC',
        capabilities: ['SWITCHABLE'],
        sensors: [],
      },
    ])
    vi.mocked(registerDevice).mockResolvedValue(plug)
    const onRegistered = vi.fn()
    const user = userEvent.setup()
    render(<HomematicPanel onRegistered={onRegistered} />)

    await fillCredentials(user)
    await user.click(screen.getByRole('button', { name: 'Connect' }))

    await user.click(await screen.findByLabelText('Steckdose PC'))
    await user.click(screen.getByRole('button', { name: 'Add selected devices' }))

    expect(connectCcu).toHaveBeenCalledWith('192.168.178.84', 'Admin', 'admin')
    expect(registerDevice).toHaveBeenCalledWith({
      externalId: 'HmIP-RF/0001DD89A4662F:3',
      name: 'Steckdose PC',
      type: 'HOMEMATIC_DEVICE',
      adapterType: 'homematic',
      capabilities: ['SWITCHABLE'],
      sensors: [],
    })
    expect(onRegistered).toHaveBeenCalledWith(plug)
  })

  it('shows the CCU message when the credentials are rejected', async () => {
    vi.mocked(connectCcu).mockResolvedValue({
      connected: false,
      message: 'The CCU rejected those credentials.',
    })
    const user = userEvent.setup()
    render(<HomematicPanel onRegistered={vi.fn()} />)

    await fillCredentials(user)
    await user.click(screen.getByRole('button', { name: 'Connect' }))

    expect(
      await screen.findByText('The CCU rejected those credentials.'),
    ).toBeInTheDocument()
    expect(discoverDevices).not.toHaveBeenCalled()
  })

  it('surfaces an error when the CCU is unreachable', async () => {
    vi.mocked(connectCcu).mockRejectedValue(new Error('Could not reach the Homematic CCU'))
    const user = userEvent.setup()
    render(<HomematicPanel onRegistered={vi.fn()} />)

    await fillCredentials(user)
    await user.click(screen.getByRole('button', { name: 'Connect' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the Homematic CCU')
  })
})
