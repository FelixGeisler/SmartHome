import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Sparkles, X, Send, Loader2, RotateCcw, StopCircle } from 'lucide-react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { api } from '@/api/client'
import { useChatStream } from '@/hooks/useChatStream'
import { cn } from '@/lib/utils'

/**
 * Floating chat bubble + slide-in drawer. Talks to /api/chat/stream which
 * is backed by Claude + the same tool registry the MCP server exposes.
 *
 * When ANTHROPIC_API_KEY isn't set on the server, the drawer shows a
 * one-line setup hint instead of the composer.
 */
export function ChatPanel() {
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const { messages, streaming, error, send, reset, stop } = useChatStream()
  const transcriptRef = useRef<HTMLDivElement>(null)

  const { data: status } = useQuery({
    queryKey: ['chat-status'],
    queryFn: api.chat.status,
    refetchInterval: 30_000,
  })

  // Auto-scroll on new tokens
  useEffect(() => {
    if (transcriptRef.current) {
      transcriptRef.current.scrollTop = transcriptRef.current.scrollHeight
    }
  }, [messages])

  async function submit() {
    const text = draft.trim()
    if (!text) return
    setDraft('')
    await send(text)
  }

  return (
    <>
      {/* ── Floating bubble ──────────────────────────────────────── */}
      <button
        onClick={() => setOpen(o => !o)}
        className={cn(
          'fixed bottom-5 right-5 z-40 w-12 h-12 rounded-full shadow-lg',
          'bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))]',
          'flex items-center justify-center transition-transform',
          'hover:scale-105',
          open && 'opacity-0 pointer-events-none',
        )}
        title="Ask the home"
      >
        <Sparkles size={20} />
      </button>

      {/* ── Drawer ───────────────────────────────────────────────── */}
      <aside
        className={cn(
          'fixed top-0 right-0 z-50 h-screen w-[26rem] max-w-[100vw]',
          'bg-[hsl(var(--card))] border-l border-[hsl(var(--border))]',
          'flex flex-col shadow-2xl transition-transform duration-200',
          open ? 'translate-x-0' : 'translate-x-full',
        )}
      >
        {/* Header */}
        <header className="flex items-center justify-between px-4 py-3 border-b border-[hsl(var(--border))]">
          <div className="flex items-center gap-2">
            <Sparkles size={16} className="text-[hsl(var(--primary))]" />
            <h2 className="text-sm font-semibold">Ask the home</h2>
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={reset}
              disabled={messages.length === 0}
              className="p-1.5 rounded-md hover:bg-[hsl(var(--accent))] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              title="Reset conversation"
            >
              <RotateCcw size={14} />
            </button>
            <button
              onClick={() => setOpen(false)}
              className="p-1.5 rounded-md hover:bg-[hsl(var(--accent))] transition-colors"
              title="Close"
            >
              <X size={16} />
            </button>
          </div>
        </header>

        {/* Transcript */}
        <div
          ref={transcriptRef}
          className="flex-1 overflow-y-auto p-4 space-y-3 text-sm"
        >
          {messages.length === 0 && (
            <div className="text-[hsl(var(--muted-foreground))] text-xs space-y-2">
              <p>Try asking:</p>
              <ul className="space-y-1 pl-1">
                <li>• "What's happening in the house?"</li>
                <li>• "Turn off the kitchen lights"</li>
                <li>• "Activate the movie scene"</li>
                <li>• "Why did the heating come on at 3am?"</li>
              </ul>
            </div>
          )}

          {messages.map((m, i) => (
            <div
              key={i}
              className={cn(
                'rounded-lg px-3 py-2 max-w-[90%] break-words',
                m.role === 'user'
                  ? 'ml-auto bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] whitespace-pre-wrap'
                  : 'mr-auto bg-[hsl(var(--accent))] text-[hsl(var(--foreground))]',
              )}
            >
              {m.role === 'assistant' ? (
                m.content ? (
                  <ReactMarkdown
                    remarkPlugins={[remarkGfm]}
                    components={{
                      p:  ({ children }) => <p className="mb-2 last:mb-0 leading-relaxed">{children}</p>,
                      ul: ({ children }) => <ul className="list-disc pl-5 my-2 space-y-1">{children}</ul>,
                      ol: ({ children }) => <ol className="list-decimal pl-5 my-2 space-y-1">{children}</ol>,
                      li: ({ children }) => <li className="leading-relaxed">{children}</li>,
                      strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
                      em: ({ children }) => <em className="italic">{children}</em>,
                      code: ({ children }) => (
                        <code className="px-1 py-0.5 rounded bg-black/20 text-[0.85em] font-mono">{children}</code>
                      ),
                      pre: ({ children }) => (
                        <pre className="my-2 p-2 rounded bg-black/30 overflow-x-auto text-xs">{children}</pre>
                      ),
                      table: ({ children }) => (
                        <div className="my-2 overflow-x-auto -mx-1">
                          <table className="text-xs border-collapse">{children}</table>
                        </div>
                      ),
                      th: ({ children }) => (
                        <th className="border border-[hsl(var(--border))] px-2 py-1 font-semibold bg-black/15 text-left">{children}</th>
                      ),
                      td: ({ children }) => (
                        <td className="border border-[hsl(var(--border))] px-2 py-1">{children}</td>
                      ),
                      h1: ({ children }) => <h1 className="text-base font-semibold mt-2 mb-1">{children}</h1>,
                      h2: ({ children }) => <h2 className="text-sm font-semibold mt-2 mb-1">{children}</h2>,
                      h3: ({ children }) => <h3 className="text-sm font-semibold mt-2 mb-1">{children}</h3>,
                      a: ({ href, children }) => (
                        <a href={href} target="_blank" rel="noreferrer" className="underline text-[hsl(var(--primary))]">{children}</a>
                      ),
                    }}
                  >
                    {m.content}
                  </ReactMarkdown>
                ) : (
                  streaming && i === messages.length - 1 && (
                    <Loader2 size={14} className="animate-spin opacity-60" />
                  )
                )
              ) : (
                m.content
              )}
            </div>
          ))}

          {error && (
            <div className="text-xs text-red-400 bg-red-500/10 rounded-md px-3 py-2">
              {error}
            </div>
          )}
        </div>

        {/* Composer */}
        <div className="p-3 border-t border-[hsl(var(--border))]">
          {status && !status.enabled ? (
            <p className="text-xs text-[hsl(var(--muted-foreground))] leading-relaxed">
              {status.message}
            </p>
          ) : (
            <div className="flex gap-2">
              <textarea
                value={draft}
                onChange={e => setDraft(e.target.value)}
                onKeyDown={e => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault()
                    submit()
                  }
                }}
                placeholder="Ask anything about your home…"
                rows={2}
                className={cn(
                  'flex-1 resize-none rounded-md px-3 py-2 text-sm',
                  'bg-[hsl(var(--background))] border border-[hsl(var(--border))]',
                  'focus:outline-none focus:ring-1 focus:ring-[hsl(var(--primary))]',
                )}
                disabled={streaming}
              />
              {streaming ? (
                <button
                  onClick={stop}
                  className="px-3 rounded-md bg-red-500/20 text-red-400 hover:bg-red-500/30 transition-colors"
                  title="Stop"
                >
                  <StopCircle size={16} />
                </button>
              ) : (
                <button
                  onClick={submit}
                  disabled={!draft.trim()}
                  className={cn(
                    'px-3 rounded-md transition-colors',
                    'bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))]',
                    'hover:opacity-90 disabled:opacity-30 disabled:cursor-not-allowed',
                  )}
                  title="Send (Enter)"
                >
                  <Send size={16} />
                </button>
              )}
            </div>
          )}
        </div>
      </aside>
    </>
  )
}
