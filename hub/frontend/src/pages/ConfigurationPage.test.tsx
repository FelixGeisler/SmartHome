import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ConfigurationPage } from './ConfigurationPage'

describe('ConfigurationPage', () => {
  it('renders the device form and the integration panels', () => {
    render(<ConfigurationPage onRegistered={vi.fn()} />)

    expect(screen.getByRole('heading', { name: 'Add device' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Pair a Hue bridge' })).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Connect a Solakon inverter' }),
    ).toBeInTheDocument()
  })
})
