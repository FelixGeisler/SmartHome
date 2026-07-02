import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AssistantPanel } from './AssistantPanel'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('AssistantPanel', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the current status and saves a new key', async () => {
    const fetchMock = vi.fn((url: string) =>
      url.endsWith('/status')
        ? Promise.resolve(jsonResponse({ configured: false }))
        : Promise.resolve(jsonResponse({ configured: true })),
    )
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<AssistantPanel />)

    expect(await screen.findByText('Status: no key set')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Anthropic API key'), 'paste-it-here')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Status: key set')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/assistant/key',
      expect.objectContaining({ method: 'POST' }),
    )
  })
})
