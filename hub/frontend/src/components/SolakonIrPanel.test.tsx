import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { connectSolakonIr, disconnectSolakonIr, solakonIrStatus } from '../api/solakonIr'
import { SolakonIrPanel } from './SolakonIrPanel'

vi.mock('../api/solakonIr')

describe('SolakonIrPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(solakonIrStatus).mockResolvedValue({ connected: false, message: 'Not connected.' })
  })

  it('connects to the meter with the entered host', async () => {
    vi.mocked(connectSolakonIr).mockResolvedValue({
      connected: true,
      message: 'Connected to the meter.',
    })
    const user = userEvent.setup()
    render(<SolakonIrPanel />)

    await user.type(screen.getByLabelText('Meter host'), '192.168.1.60')
    await user.click(screen.getByRole('button', { name: 'Connect' }))

    expect(connectSolakonIr).toHaveBeenCalledWith('192.168.1.60')
    expect(await screen.findByText('Connected to the meter.')).toBeInTheDocument()
    expect(screen.getByText('Connected')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Disconnect' })).toBeInTheDocument()
  })

  it('trims the host before connecting', async () => {
    vi.mocked(connectSolakonIr).mockResolvedValue({ connected: true, message: 'Connected' })
    const user = userEvent.setup()
    render(<SolakonIrPanel />)

    await user.type(screen.getByLabelText('Meter host'), '  192.168.1.60  ')
    await user.click(screen.getByRole('button', { name: 'Connect' }))

    expect(connectSolakonIr).toHaveBeenCalledWith('192.168.1.60')
  })

  it('disables the connect button until a host is entered', async () => {
    render(<SolakonIrPanel />)

    expect(await screen.findByRole('button', { name: 'Connect' })).toBeDisabled()
  })

  it('surfaces an error when the connection attempt fails', async () => {
    vi.mocked(connectSolakonIr).mockRejectedValue(new Error('Could not reach the meter'))
    const user = userEvent.setup()
    render(<SolakonIrPanel />)

    await user.type(screen.getByLabelText('Meter host'), '192.168.1.60')
    await user.click(screen.getByRole('button', { name: 'Connect' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the meter')
    expect(screen.getByText('Not connected')).toBeInTheDocument()
  })

  it('shows a not-connected result as an error, not a neutral status', async () => {
    vi.mocked(connectSolakonIr).mockResolvedValue({
      connected: false,
      message: 'Could not reach the meter.',
    })
    const user = userEvent.setup()
    render(<SolakonIrPanel />)

    await user.type(screen.getByLabelText('Meter host'), '192.168.1.60')
    await user.click(screen.getByRole('button', { name: 'Connect' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the meter.')
    expect(screen.getByText('Not connected')).toBeInTheDocument()
  })

  it('restores the connected state on mount and disconnects on request', async () => {
    vi.mocked(solakonIrStatus).mockResolvedValue({ connected: true, message: 'Connected' })
    vi.mocked(disconnectSolakonIr).mockResolvedValue({
      connected: false,
      message: 'Disconnected from the meter.',
    })
    const user = userEvent.setup()
    render(<SolakonIrPanel />)

    expect(await screen.findByText('Connected')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Disconnect' }))

    expect(disconnectSolakonIr).toHaveBeenCalled()
    expect(await screen.findByText('Disconnected from the meter.')).toBeInTheDocument()
    expect(screen.getByText('Not connected')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Disconnect' })).not.toBeInTheDocument()
  })
})
