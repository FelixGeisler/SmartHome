import { useRef, useState } from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
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
 * A floating assistant: a chat button anchored bottom-right on every view that pops a chat window.
 * The assistant can answer about the home, control devices, and proactively flag issues; each turn
 * calls the hub, which runs the model's tool calls. Assistant replies are rendered as Markdown.
 */
export function AssistantWidget() {
  const [open, setOpen] = useState(false)
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
    <div className="assistant-widget">
      {open && (
        <section className="assistant-widget__panel" role="dialog" aria-label="Assistant">
          <header className="assistant-widget__head">
            <span className="assistant-widget__title">Assistant</span>
            <button
              type="button"
              className="assistant-widget__close"
              aria-label="Close assistant"
              onClick={() => setOpen(false)}
            >
              {'×'}
            </button>
          </header>

          <div className="assistant-widget__actions">
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
            {messages.length === 0 && !busy && (
              <li className="assistant__msg assistant__msg--assistant">
                <p className="assistant__text assistant__text--pending">
                  Ask about your home, control a device, or use &ldquo;Check my home&rdquo;.
                </p>
              </li>
            )}
            {messages.map((message) => (
              <li key={message.id} className={`assistant__msg assistant__msg--${message.role}`}>
                <span className="assistant__role">
                  {message.role === 'user' ? 'You' : 'Assistant'}
                </span>
                {message.role === 'assistant' ? (
                  <div className="assistant__text assistant__markdown">
                    <Markdown remarkPlugins={[remarkGfm]}>{message.text}</Markdown>
                  </div>
                ) : (
                  <p className="assistant__text">{message.text}</p>
                )}
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
            <button
              type="submit"
              className="assistant__send"
              disabled={busy || input.trim() === ''}
            >
              Send
            </button>
          </form>
        </section>
      )}
      <button
        type="button"
        className="assistant-widget__toggle"
        aria-label={open ? 'Close assistant' : 'Open assistant'}
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <ChatIcon />
      </button>
    </div>
  )
}

/** A speech-bubble glyph for the floating button. */
function ChatIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="24"
      height="24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
    </svg>
  )
}
