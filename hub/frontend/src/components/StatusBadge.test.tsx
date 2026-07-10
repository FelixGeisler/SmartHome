import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { StatusBadge } from './StatusBadge'

describe('StatusBadge', () => {
  it('shows the positive label and the on modifier when on', () => {
    render(<StatusBadge on onLabel="Connected" offLabel="Not connected" />)

    const badge = screen.getByText('Connected')
    expect(badge).toBeInTheDocument()
    expect(badge).toHaveClass('status-badge--on')
  })

  it('shows the negative label and the off modifier when not on', () => {
    render(<StatusBadge on={false} onLabel="Connected" offLabel="Not connected" />)

    const badge = screen.getByText('Not connected')
    expect(badge).toBeInTheDocument()
    expect(badge).toHaveClass('status-badge--off')
  })
})
