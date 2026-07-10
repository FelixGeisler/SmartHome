import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ConfigurationPage } from './ConfigurationPage'

function renderPage() {
  render(
    <ConfigurationPage
      devices={[]}
      onRegistered={vi.fn()}
      onDeviceUpdated={vi.fn()}
      onDeviceDeleted={vi.fn()}
    />,
  )
}

describe('ConfigurationPage', () => {
  it('shows the devices tab first, with the add and manage panels', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: 'Add device' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Manage devices' })).toBeInTheDocument()
  })

  it('reveals an integration panel only after its tab is selected', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(screen.queryByRole('heading', { name: 'Pair a Hue bridge' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Hue' }))

    expect(screen.getByRole('heading', { name: 'Pair a Hue bridge' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Add device' })).not.toBeInTheDocument()
  })
})
