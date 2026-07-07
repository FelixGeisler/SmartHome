import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Device } from '../api/devices'
import { deleteDevice, renameDevice } from '../api/devices'
import { DeviceManagementPanel } from './DeviceManagementPanel'

vi.mock('../api/devices', () => ({
  renameDevice: vi.fn(),
  deleteDevice: vi.fn(),
}))

const lamp: Device = {
  id: 1,
  externalId: 'plug-1',
  name: 'Desk Lamp',
  type: 'SHELLY_PLUG',
  capabilities: ['SWITCHABLE'],
  adapterType: 'shelly',
  state: { on: 'false' },
  sensors: [],
  reachable: true,
}

const climate: Device = {
  id: 2,
  externalId: 'node-1',
  name: 'Climate',
  type: 'SENSOR_NODE',
  capabilities: ['SENSING'],
  adapterType: null,
  state: {},
  sensors: [],
  reachable: false,
}

function renderPanel() {
  const onRenamed = vi.fn()
  const onDeleted = vi.fn()
  render(
    <DeviceManagementPanel devices={[lamp, climate]} onRenamed={onRenamed} onDeleted={onDeleted} />,
  )
  return { onRenamed, onDeleted, user: userEvent.setup() }
}

describe('DeviceManagementPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('lists each device and badges the unreachable one as offline', () => {
    renderPanel()

    expect(screen.getByText('Desk Lamp')).toBeInTheDocument()
    expect(screen.getByText('Climate')).toBeInTheDocument()
    expect(screen.getByText('Offline')).toBeInTheDocument()
  })

  it('renames a device through the inline editor', async () => {
    const updated = { ...lamp, name: 'Reading Lamp' }
    vi.mocked(renameDevice).mockResolvedValue(updated)
    const { onRenamed, user } = renderPanel()

    await user.click(screen.getAllByRole('button', { name: 'Rename' })[0])
    const input = screen.getByLabelText('New name for Desk Lamp')
    await user.clear(input)
    await user.type(input, 'Reading Lamp')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(renameDevice).toHaveBeenCalledWith(1, 'Reading Lamp')
    expect(onRenamed).toHaveBeenCalledWith(updated)
  })

  it('deletes only after a second confirming click', async () => {
    vi.mocked(deleteDevice).mockResolvedValue(undefined)
    const { onDeleted, user } = renderPanel()

    await user.click(screen.getAllByRole('button', { name: 'Delete' })[0])
    expect(deleteDevice).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(deleteDevice).toHaveBeenCalledWith(1)
    expect(onDeleted).toHaveBeenCalledWith(1)
  })

  it('leaves a device untouched when the delete confirm is dismissed', async () => {
    const { onDeleted, user } = renderPanel()

    await user.click(screen.getAllByRole('button', { name: 'Delete' })[0])
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(deleteDevice).not.toHaveBeenCalled()
    expect(onDeleted).not.toHaveBeenCalled()
    expect(screen.getAllByRole('button', { name: 'Delete' })).toHaveLength(2)
  })

  it('surfaces an error when a rename fails', async () => {
    vi.mocked(renameDevice).mockRejectedValue(new Error('Name already taken'))
    const { user } = renderPanel()

    await user.click(screen.getAllByRole('button', { name: 'Rename' })[0])
    const input = screen.getByLabelText('New name for Desk Lamp')
    await user.clear(input)
    await user.type(input, 'Nope')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Name already taken')
  })
})
