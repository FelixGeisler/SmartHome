import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AssistantPage } from './AssistantPage'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('AssistantPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('sends a typed message and shows the reply', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ reply: 'The living room is 24 C.' }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<AssistantPage />)

    await user.type(screen.getByLabelText('Message the assistant'), 'How warm is it?')
    await user.click(screen.getByRole('button', { name: 'Send' }))

    expect(await screen.findByText('The living room is 24 C.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/assistant/chat',
      expect.objectContaining({ body: JSON.stringify({ message: 'How warm is it?' }) }),
    )
  })

  it('asks the assistant to review the home from the "Check my home" action', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ reply: 'Everything looks fine.' }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<AssistantPage />)

    await user.click(screen.getByRole('button', { name: 'Check my home' }))

    expect(await screen.findByText('Everything looks fine.')).toBeInTheDocument()
    const sentBody = JSON.parse(fetchMock.mock.calls[0][1].body) as { message: string }
    expect(sentBody.message).toMatch(/review my home/i)
  })

  it('shows an error when the assistant is unavailable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ detail: 'Could not reach the Claude API' }, 502)),
    )
    const user = userEvent.setup()
    render(<AssistantPage />)

    await user.type(screen.getByLabelText('Message the assistant'), 'hi')
    await user.click(screen.getByRole('button', { name: 'Send' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the Claude API')
  })
})
