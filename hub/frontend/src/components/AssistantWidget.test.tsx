import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AssistantWidget } from './AssistantWidget'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('AssistantWidget', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('opens from the floating button, sends a message, and renders a Markdown reply', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ reply: 'The living room is **24 C**.' }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<AssistantWidget />)

    await user.click(screen.getByRole('button', { name: 'Open assistant' }))
    await user.type(screen.getByLabelText('Message the assistant'), 'How warm is it?')
    await user.click(screen.getByRole('button', { name: 'Send' }))

    // The reply's **24 C** must render as a <strong>, not literal asterisks.
    const bold = await screen.findByText('24 C')
    expect(bold.tagName).toBe('STRONG')
  })

  it('sends an example command verbatim when its chip is clicked', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ reply: 'Two lights are on.' }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<AssistantWidget />)

    await user.click(screen.getByRole('button', { name: 'Open assistant' }))
    await user.click(screen.getByRole('button', { name: 'Which devices are on right now?' }))

    expect(await screen.findByText('Two lights are on.')).toBeInTheDocument()
    const sentBody = JSON.parse(fetchMock.mock.calls[0][1].body) as { message: string }
    expect(sentBody.message).toBe('Which devices are on right now?')
  })

  it('asks the assistant to review the home from "Check my home"', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ reply: 'Everything looks fine.' }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<AssistantWidget />)

    await user.click(screen.getByRole('button', { name: 'Open assistant' }))
    await user.click(screen.getByRole('button', { name: 'Check my home' }))

    expect(await screen.findByText('Everything looks fine.')).toBeInTheDocument()
    const sentBody = JSON.parse(fetchMock.mock.calls[0][1].body) as { message: string }
    expect(sentBody.message).toMatch(/review my home/i)
  })
})
