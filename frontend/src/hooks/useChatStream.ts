import { useCallback, useRef, useState } from 'react'
import { api } from '@/api/client'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface UseChatStream {
  messages: ChatMessage[]
  streaming: boolean
  error: string | null
  send: (text: string) => Promise<void>
  reset: () => void
  stop: () => void
}

/**
 * Thin wrapper around {@code api.chat.stream}. Owns the transcript so the
 * panel can stay dumb; sends the full prior history with each request because
 * the backend is stateless.
 */
export function useChatStream(): UseChatStream {
  const [messages, setMessages]   = useState<ChatMessage[]>([])
  const [streaming, setStreaming] = useState(false)
  const [error, setError]         = useState<string | null>(null)
  const abortRef                  = useRef<AbortController | null>(null)

  const send = useCallback(async (text: string) => {
    if (streaming || !text.trim()) return
    setError(null)

    const userMsg: ChatMessage = { role: 'user', content: text }
    // Capture the history that will go to the backend BEFORE appending the placeholder,
    // so the assistant turn we're about to generate is not echoed back as context.
    const historyForRequest = messages

    setMessages(prev => [...prev, userMsg, { role: 'assistant', content: '' }])
    setStreaming(true)

    const controller = new AbortController()
    abortRef.current = controller

    try {
      await api.chat.stream(
        text,
        historyForRequest,
        token => {
          setMessages(prev => {
            const last = prev[prev.length - 1]
            if (!last || last.role !== 'assistant') return prev
            return [...prev.slice(0, -1), { ...last, content: last.content + token }]
          })
        },
        errMsg => {
          setError(errMsg)
        },
        controller.signal,
      )
    } catch (e) {
      if ((e as Error).name !== 'AbortError') {
        setError((e as Error).message)
      }
    } finally {
      setStreaming(false)
      abortRef.current = null
    }
  }, [messages, streaming])

  const reset = useCallback(() => {
    abortRef.current?.abort()
    setMessages([])
    setError(null)
  }, [])

  const stop = useCallback(() => {
    abortRef.current?.abort()
  }, [])

  return { messages, streaming, error, send, reset, stop }
}
