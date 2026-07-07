import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ConfigurationPage } from './ConfigurationPage'

describe('ConfigurationPage', () => {
  it('renders the device form, the integration panels, and the manage-devices panel', () => {
    render(
      <ConfigurationPage
        devices={[]}
        onRegistered={vi.fn()}
        onDeviceUpdated={vi.fn()}
        onDeviceDeleted={vi.fn()}
      />,
    )

    expect(screen.getByRole('heading', { name: 'Add device' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Pair a Hue bridge' })).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Connect a Solakon inverter' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Manage devices' })).toBeInTheDocument()
  })
})
