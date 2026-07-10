import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { connectMqtt, disconnectMqtt, mqttStatus } from '../api/mqtt'
import { MqttPanel } from './MqttPanel'

vi.mock('../api/mqtt')

describe('MqttPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(mqttStatus).mockResolvedValue({ connected: false, message: 'Not connected' })
  })

  it('connects to the broker with the entered host and port', async () => {
    vi.mocked(connectMqtt).mockResolvedValue({
      connected: true,
      message: 'Connected to 192.168.1.21:1883',
    })
    const user = userEvent.setup()
    render(<MqttPanel />)

    await user.type(screen.getByLabelText('Broker host'), '192.168.1.21')
    await user.click(screen.getByRole('button', { name: 'Connect' }))

    expect(connectMqtt).toHaveBeenCalledWith('192.168.1.21', 1883)
    expect(await screen.findByText('Connected to 192.168.1.21:1883')).toBeInTheDocument()
    expect(screen.getByText('Connected')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Disconnect' })).toBeInTheDocument()
  })

  it('omits the port when the field is cleared, letting the backend default it', async () => {
    vi.mocked(connectMqtt).mockResolvedValue({ connected: true, message: 'Connected' })
    const user = userEvent.setup()
    render(<MqttPanel />)

    await user.type(screen.getByLabelText('Broker host'), '192.168.1.21')
    await user.clear(screen.getByLabelText('Port'))
    await user.click(screen.getByRole('button', { name: 'Connect' }))

    expect(connectMqtt).toHaveBeenCalledWith('192.168.1.21', undefined)
  })

  it('disables the connect button until a host is entered', async () => {
    render(<MqttPanel />)

    expect(await screen.findByRole('button', { name: 'Connect' })).toBeDisabled()
  })

  it('surfaces an error when the connection attempt fails', async () => {
    vi.mocked(connectMqtt).mockRejectedValue(new Error('The broker refused the connection'))
    const user = userEvent.setup()
    render(<MqttPanel />)

    await user.type(screen.getByLabelText('Broker host'), '192.168.1.21')
    await user.click(screen.getByRole('button', { name: 'Connect' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The broker refused the connection')
    expect(screen.getByText('Not connected')).toBeInTheDocument()
  })

  it('restores the connected state on mount and disconnects on request', async () => {
    vi.mocked(mqttStatus).mockResolvedValue({ connected: true, message: 'Connected' })
    vi.mocked(disconnectMqtt).mockResolvedValue({ connected: false, message: 'Disconnected' })
    const user = userEvent.setup()
    render(<MqttPanel />)

    // The mount-time status query restores the persisted connection.
    expect(await screen.findByText('Connected')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Disconnect' }))

    expect(disconnectMqtt).toHaveBeenCalled()
    expect(await screen.findByText('Disconnected')).toBeInTheDocument()
    expect(screen.getByText('Not connected')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Disconnect' })).not.toBeInTheDocument()
  })
})
