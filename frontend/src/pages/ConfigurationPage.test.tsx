import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ConfigurationPage } from './ConfigurationPage'

describe('ConfigurationPage', () => {
  it('renders the add-device form and the Hue pairing panel', () => {
    render(<ConfigurationPage onRegistered={vi.fn()} />)

    expect(screen.getByRole('heading', { name: 'Add device' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Pair a Hue bridge' })).toBeInTheDocument()
  })
})
