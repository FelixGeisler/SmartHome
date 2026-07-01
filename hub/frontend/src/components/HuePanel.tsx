import { useState } from 'react'
import type { Device } from '../api/devices'
import { registerDevice } from '../api/devices'
import type { HueLight } from '../api/hue'
import { discoverLights, pairBridge } from '../api/hue'

interface HuePanelProps {
  onRegistered: (device: Device) => void
}

/** Pairs with a Hue bridge, discovers its lights, and registers the chosen ones as devices. */
export function HuePanel({ onRegistered }: HuePanelProps) {
  const [host, setHost] = useState('')
  const [paired, setPaired] = useState(false)
  const [lights, setLights] = useState<HueLight[]>([])
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set())
  const [busy, setBusy] = useState(false)
  const [status, setStatus] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function pair() {
    setBusy(true)
    setError(null)
    setStatus(null)
    // Start each attempt from a clean slate: a failed re-pair must not leave a stale
    // paired flag or the previous bridge's lights on screen.
    setPaired(false)
    setLights([])
    setSelected(new Set())
    try {
      const result = await pairBridge(host.trim())
      if (!result.paired) {
        setStatus(result.message)
        return
      }
      setPaired(true)
      const found = await discoverLights()
      setLights(found)
      setStatus(found.length > 0 ? `Found ${found.length} light(s).` : 'No lights on the bridge.')
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setBusy(false)
    }
  }

  function toggleSelected(id: string) {
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  async function addSelected() {
    setBusy(true)
    setError(null)
    try {
      for (const light of lights.filter((candidate) => selected.has(candidate.id))) {
        const device = await registerDevice({
          externalId: light.id,
          name: light.name,
          type: 'HUE_LIGHT',
          adapterType: 'hue',
          capabilities: light.capabilities,
        })
        onRegistered(device)
      }
      setStatus('Added the selected lights.')
      setSelected(new Set())
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="hue-panel">
      <h2>Pair a Hue bridge</h2>
      {error !== null && (
        <p className="hue-panel__error" role="alert">
          {error}
        </p>
      )}
      {status !== null && <p className="hue-panel__status">{status}</p>}
      <div className="hue-panel__pair">
        <label className="add-device__field">
          Bridge host
          <input
            value={host}
            onChange={(event) => setHost(event.target.value)}
            placeholder="192.168.1.10"
            disabled={busy}
          />
        </label>
        <button type="button" onClick={() => void pair()} disabled={busy || host.trim() === ''}>
          {paired ? 'Re-pair' : 'Press the link button, then pair'}
        </button>
      </div>
      {lights.length > 0 && (
        <div className="hue-panel__lights">
          <ul>
            {lights.map((light) => (
              <li key={light.id}>
                <label>
                  <input
                    type="checkbox"
                    checked={selected.has(light.id)}
                    onChange={() => toggleSelected(light.id)}
                  />
                  {light.name}
                </label>
              </li>
            ))}
          </ul>
          <button
            type="button"
            onClick={() => void addSelected()}
            disabled={busy || selected.size === 0}
          >
            Add selected lights
          </button>
        </div>
      )}
    </section>
  )
}

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Something went wrong'
}
