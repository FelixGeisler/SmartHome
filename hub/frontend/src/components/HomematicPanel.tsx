import { useEffect, useState } from 'react'
import type { Device } from '../api/devices'
import { registerDevice } from '../api/devices'
import type { HomematicDevice } from '../api/homematic'
import { connectCcu, discoverDevices, homematicStatus } from '../api/homematic'
import { StatusBadge } from './StatusBadge'

interface HomematicPanelProps {
  onRegistered: (device: Device) => void
}

/** Connects to a Homematic CCU, discovers its channels, and registers the chosen ones as devices. */
export function HomematicPanel({ onRegistered }: HomematicPanelProps) {
  const [host, setHost] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [connected, setConnected] = useState(false)
  const [devices, setDevices] = useState<HomematicDevice[]>([])
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set())
  const [busy, setBusy] = useState(false)
  const [status, setStatus] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    homematicStatus()
      .then((result) => setConnected(result.connected))
      .catch(() => setConnected(false))
  }, [])

  async function connect() {
    setBusy(true)
    setError(null)
    setStatus(null)
    // Start each attempt from a clean slate so a failed reconnect leaves no stale state on screen.
    setConnected(false)
    setDevices([])
    setSelected(new Set())
    try {
      const result = await connectCcu(host.trim(), username.trim(), password)
      if (!result.connected) {
        // Rejected credentials are a failed attempt, so surface the reason as an error.
        setError(result.message)
        return
      }
      setConnected(true)
      const found = await discoverDevices()
      setDevices(found)
      setStatus(found.length > 0 ? `Found ${found.length} device(s).` : 'No devices on the CCU.')
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setBusy(false)
    }
  }

  function toggleSelected(externalId: string) {
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(externalId)) {
        next.delete(externalId)
      } else {
        next.add(externalId)
      }
      return next
    })
  }

  async function addSelected() {
    setBusy(true)
    setError(null)
    try {
      for (const device of devices.filter((candidate) => selected.has(candidate.externalId))) {
        const registered = await registerDevice({
          externalId: device.externalId,
          name: device.name,
          type: 'HOMEMATIC_DEVICE',
          // Only a controllable channel gets the command adapter; a sensing channel is read by the
          // hub's sensor poll, never commanded.
          adapterType: device.capabilities.includes('SWITCHABLE') ? 'homematic' : undefined,
          capabilities: device.capabilities,
          sensors: device.sensors,
        })
        onRegistered(registered)
      }
      setStatus('Added the selected devices.')
      setSelected(new Set())
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setBusy(false)
    }
  }

  const canConnect =
    !busy && host.trim() !== '' && username.trim() !== '' && password !== ''

  return (
    <section className="config-panel homematic-panel">
      <h2>Connect a Homematic CCU</h2>
      <p className="config-panel__hint">
        Enter the CCU host and a WebUI login. Its switches, thermostats, and sensors can then be
        added as devices.
      </p>
      {error !== null && (
        <p className="config-panel__error" role="alert">
          {error}
        </p>
      )}
      {status !== null && <p className="config-panel__status">{status}</p>}
      <div className="config-panel__state">
        <StatusBadge on={connected} onLabel="Connected" offLabel="Not connected" />
      </div>
      <div className="config-panel__row">
        <label className="add-device__field">
          CCU host
          <input
            value={host}
            onChange={(event) => setHost(event.target.value)}
            placeholder="192.168.1.10"
            disabled={busy}
          />
        </label>
        <label className="add-device__field">
          Username
          <input
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            autoComplete="username"
            disabled={busy}
          />
        </label>
        <label className="add-device__field">
          Password
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            disabled={busy}
          />
        </label>
        <button type="button" onClick={() => void connect()} disabled={!canConnect}>
          {connected ? 'Reconnect' : 'Connect'}
        </button>
      </div>
      {devices.length > 0 && (
        <div className="homematic-panel__devices">
          <ul>
            {devices.map((device) => (
              <li key={device.externalId}>
                <label>
                  <input
                    type="checkbox"
                    checked={selected.has(device.externalId)}
                    onChange={() => toggleSelected(device.externalId)}
                  />
                  {device.name}
                </label>
              </li>
            ))}
          </ul>
          <button
            type="button"
            onClick={() => void addSelected()}
            disabled={busy || selected.size === 0}
          >
            Add selected devices
          </button>
        </div>
      )}
    </section>
  )
}

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Something went wrong'
}
