import { useEffect, useState } from 'react'
import { assistantStatus, setAssistantKey } from '../api/assistant'

/**
 * Sets the assistant's Anthropic API key at runtime, the way the MQTT broker and Hue bridge are
 * configured here. The key is held for the hub run and never persisted or shown again, so the field
 * shows status, not the value.
 */
export function AssistantPanel() {
  const [apiKey, setApiKey] = useState('')
  const [configured, setConfigured] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    assistantStatus()
      .then((result) => setConfigured(result.configured))
      .catch(() => setConfigured(false))
  }, [])

  async function save() {
    setBusy(true)
    setError(null)
    try {
      const result = await setAssistantKey(apiKey.trim())
      setConfigured(result.configured)
      setApiKey('')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="mqtt-panel">
      <h2>AI assistant</h2>
      <p className="mqtt-panel__hint">
        Set an Anthropic API key to enable the Assistant tab. The key is held for this hub run and is
        never stored or shown again.
      </p>
      {error !== null && (
        <p className="mqtt-panel__error" role="alert">
          {error}
        </p>
      )}
      <p className="mqtt-panel__state">Status: {configured ? 'key set' : 'no key set'}</p>
      <div className="mqtt-panel__connect">
        <label className="add-device__field">
          API key
          <input
            type="password"
            value={apiKey}
            onChange={(event) => setApiKey(event.target.value)}
            placeholder={configured ? '•••••• (set — paste to replace)' : 'paste your key'}
            aria-label="Anthropic API key"
            disabled={busy}
          />
        </label>
        <button type="button" onClick={() => void save()} disabled={busy || apiKey.trim() === ''}>
          Save
        </button>
      </div>
    </section>
  )
}
