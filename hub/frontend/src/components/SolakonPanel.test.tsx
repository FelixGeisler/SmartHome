import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { connectSolakon, disconnectSolakon, solakonStatus } from '../api/solakon'
import { SolakonPanel } from './SolakonPanel'

vi.mock('../api/solakon')

describe('SolakonPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(solakonStatus).mockResolvedValue({ connected: false, message: 'Not connected.' })
  })

  it('connects to the inverter with the entered host, port and unit id', async () => {
    vi.mocked(connectSolakon).mockResolvedValue({
      connected: true,
      message: 'Connected to the inverter.',
    })
    const user = userEvent.setup()
    render(<SolakonPanel />)

    await user.type(screen.getByLabelText('Inverter host'), '192.168.1.50')
    await user.click(screen.getByRole('button', { name: 'Connect' }))

    expect(connectSolakon).toHaveBeenCalledWith('192.168.1.50', 502, 1)
    expect(await screen.findByText('Connected to the inverter.')).toBeInTheDocument()
    expect(screen.getByText('Status: connected')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Disconnect' })).toBeInTheDocument()
  })

  it('omits the port and unit id when cleared, letting the backend default them', async () => {
    vi.mocked(connectSolakon).mockResolvedValue({ connected: true, message: 'Connected' })
    const user = userEvent.setup()
    render(<SolakonPanel />)

    await user.type(screen.getByLabelText('Inverter host'), '192.168.1.50')
    await user.clear(screen.getByLabelText('Port'))
    await user.clear(screen.getByLabelText('Unit ID'))
    await user.click(screen.getByRole('button', { name: 'Connect' }))

    expect(connectSolakon).toHaveBeenCalledWith('192.168.1.50', undefined, undefined)
  })

  it('disables the connect button until a host is entered', async () => {
    render(<SolakonPanel />)

    expect(await screen.findByRole('button', { name: 'Connect' })).toBeDisabled()
  })

  it('surfaces an error when the connection attempt fails', async () => {
    vi.mocked(connectSolakon).mockRejectedValue(new Error('Could not reach the inverter'))
    const user = userEvent.setup()
    render(<SolakonPanel />)

    await user.type(screen.getByLabelText('Inverter host'), '192.168.1.50')
    await user.click(screen.getByRole('button', { name: 'Connect' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the inverter')
    expect(screen.getByText('Status: not connected')).toBeInTheDocument()
  })

  it('restores the connected state on mount and disconnects on request', async () => {
    vi.mocked(solakonStatus).mockResolvedValue({ connected: true, message: 'Connected' })
    vi.mocked(disconnectSolakon).mockResolvedValue({
      connected: false,
      message: 'Disconnected from the inverter.',
    })
    const user = userEvent.setup()
    render(<SolakonPanel />)

    // The mount-time status query restores the persisted connection.
    expect(await screen.findByText('Status: connected')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Disconnect' }))

    expect(disconnectSolakon).toHaveBeenCalled()
    expect(await screen.findByText('Disconnected from the inverter.')).toBeInTheDocument()
    expect(screen.getByText('Status: not connected')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Disconnect' })).not.toBeInTheDocument()
  })
})
