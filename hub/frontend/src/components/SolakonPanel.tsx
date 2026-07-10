import { useEffect, useState } from 'react'
import { connectSolakon, disconnectSolakon, solakonStatus } from '../api/solakon'
import { StatusBadge } from './StatusBadge'

/**
 * Connects the hub to a Solakon ONE inverter over Modbus TCP. Once connected, its power and battery
 * readings appear on the dashboard as live charts, so there is nothing to register by hand here.
 */
export function SolakonPanel() {
  const [host, setHost] = useState('')
  const [port, setPort] = useState('502')
  const [unitId, setUnitId] = useState('1')
  const [connected, setConnected] = useState(false)
  const [busy, setBusy] = useState(false)
  const [status, setStatus] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    solakonStatus()
      .then((result) => setConnected(result.connected))
      .catch(() => setConnected(false))
  }, [])

  async function connect() {
    setBusy(true)
    setError(null)
    setStatus(null)
    try {
      const parsedPort = parseNumericField(port, 'Port', 1, 65535)
      const parsedUnitId = parseNumericField(unitId, 'Unit ID', 1, 247)
      const result = await connectSolakon(host.trim(), parsedPort, parsedUnitId)
      setConnected(result.connected)
      // A "not connected" result is a failed attempt, so surface it as an error, not a neutral
      // status line where it reads like a hint.
      if (result.connected) {
        setStatus(result.message)
      } else {
        setError(result.message)
      }
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setBusy(false)
    }
  }

  async function disconnect() {
    setBusy(true)
    setError(null)
    setStatus(null)
    try {
      const result = await disconnectSolakon()
      setConnected(result.connected)
      setStatus(result.message)
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="config-panel">
      <h2>Connect a Solakon inverter</h2>
      <p className="config-panel__hint">
        Point the hub at your Solakon ONE over Modbus TCP. Enable Modbus TCP in the Solakon app and
        connect the inverter by wired Ethernet; its power and battery readings then chart on the
        dashboard.
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
          Inverter host
          <input
            value={host}
            onChange={(event) => setHost(event.target.value)}
            placeholder="192.168.1.50"
            disabled={busy}
          />
        </label>
        <label className="add-device__field">
          Port
          <input
            value={port}
            onChange={(event) => setPort(event.target.value)}
            placeholder="502"
            inputMode="numeric"
            disabled={busy}
          />
        </label>
        <label className="add-device__field">
          Unit ID
          <input
            value={unitId}
            onChange={(event) => setUnitId(event.target.value)}
            placeholder="1"
            inputMode="numeric"
            disabled={busy}
          />
        </label>
        <button type="button" onClick={() => void connect()} disabled={busy || host.trim() === ''}>
          Connect
        </button>
        {connected && (
          <button type="button" onClick={() => void disconnect()} disabled={busy}>
            Disconnect
          </button>
        )}
      </div>
    </section>
  )
}

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Something went wrong'
}

/**
 * Parses an optional numeric field: a blank value returns undefined so the backend applies its
 * default, while a non-blank value must be a whole number within range, otherwise it throws so the
 * caller surfaces the error instead of sending NaN (which JSON encodes as null and silently defaults).
 */
function parseNumericField(
  raw: string,
  label: string,
  min: number,
  max: number,
): number | undefined {
  const trimmed = raw.trim()
  if (trimmed === '') {
    return undefined
  }
  const value = Number(trimmed)
  if (!Number.isInteger(value) || value < min || value > max) {
    throw new Error(`${label} must be a whole number between ${min} and ${max}.`)
  }
  return value
}
