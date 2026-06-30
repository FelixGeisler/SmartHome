import { useRef, useState } from 'react'
import { sendChat } from '../api/assistant'

/** One line in the chat transcript. */
interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  text: string
}

/** The canned prompt behind the "Check my home" quick action. */
const REVIEW_PROMPT = 'Review my home and flag anything that looks off, with concrete suggestions.'

/**
 * The assistant view: a chat that can answer questions about the home, control devices, and
 * proactively flag issues. Each turn calls the hub, which runs the model's tool calls against the
 * device and telemetry services.
 */
export function AssistantPage() {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const nextId = useRef(0)

  async function ask(text: string) {
    const trimmed = text.trim()
    if (trimmed === '' || busy) {
      return
    }
    setError(null)
    setInput('')
    setMessages((current) => [...current, { id: nextId.current++, role: 'user', text: trimmed }])
    setBusy(true)
    try {
      const { reply } = await sendChat(trimmed)
      setMessages((current) => [
        ...current,
        { id: nextId.current++, role: 'assistant', text: reply },
      ])
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'The assistant is unavailable.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="assistant">
      <div className="assistant__intro">
        <p className="assistant__hint">
          Ask about your home, control a device, or see what needs attention.
        </p>
        <button
          type="button"
          className="assistant__suggest"
          disabled={busy}
          onClick={() => void ask(REVIEW_PROMPT)}
        >
          Check my home
        </button>
      </div>

      <ul className="assistant__log">
        {messages.map((message) => (
          <li key={message.id} className={`assistant__msg assistant__msg--${message.role}`}>
            <span className="assistant__role">
              {message.role === 'user' ? 'You' : 'Assistant'}
            </span>
            <p className="assistant__text">{message.text}</p>
          </li>
        ))}
        {busy && (
          <li className="assistant__msg assistant__msg--assistant">
            <span className="assistant__role">Assistant</span>
            <p className="assistant__text assistant__text--pending">Thinking&hellip;</p>
          </li>
        )}
      </ul>

      {error !== null && (
        <p className="assistant__error" role="alert">
          {error}
        </p>
      )}

      <form
        className="assistant__form"
        onSubmit={(event) => {
          event.preventDefault()
          void ask(input)
        }}
      >
        <input
          className="assistant__input"
          value={input}
          disabled={busy}
          onChange={(event) => setInput(event.target.value)}
          placeholder="e.g. Is the CO2 OK? Turn off the desk lamp."
          aria-label="Message the assistant"
        />
        <button type="submit" className="assistant__send" disabled={busy || input.trim() === ''}>
          Send
        </button>
      </form>
    </section>
  )
}
