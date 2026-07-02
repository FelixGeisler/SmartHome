import { afterEach, describe, expect, it, vi } from 'vitest'
import { sendChat } from './assistant'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('assistant api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('posts a message to POST /api/assistant/chat and returns the reply', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ reply: 'All good.' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(sendChat('how warm is it?')).resolves.toEqual({ reply: 'All good.' })

    expect(fetchMock).toHaveBeenCalledWith('/api/assistant/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: 'how warm is it?' }),
    })
  })

  it('reports the assistant error detail from a 502', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ detail: 'Could not reach the Claude API' }, 502)),
    )

    await expect(sendChat('hi')).rejects.toThrow('Could not reach the Claude API')
  })
})
