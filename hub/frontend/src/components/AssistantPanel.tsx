import { useEffect, useState } from 'react'
import { assistantStatus, setAssistantKey } from '../api/assistant'
import { StatusBadge } from './StatusBadge'

/**
 * Sets the assistant's Anthropic API key at runtime, the way the MQTT broker and Hue bridge are
 * configured here. The key is stored in the hub's local settings (so it survives a restart) and
 * never shown again, so the field shows status, not the value.
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
    <section className="config-panel">
      <h2>AI assistant</h2>
      <p className="config-panel__hint">
        Set an Anthropic API key to enable the assistant. The key is stored in the hub&apos;s local
        settings and survives a restart; it is never shown again.
      </p>
      {error !== null && (
        <p className="config-panel__error" role="alert">
          {error}
        </p>
      )}
      <div className="config-panel__state">
        <StatusBadge on={configured} onLabel="Key set" offLabel="No key set" />
      </div>
      <div className="config-panel__row">
        <label className="add-device__field">
          API key
          <input
            type="password"
            value={apiKey}
            onChange={(event) => setApiKey(event.target.value)}
            placeholder={configured ? '•••••• (set; paste to replace)' : 'paste your key'}
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
