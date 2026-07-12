import { useEffect, useState } from 'react'
import { connectSolakonIr, disconnectSolakonIr, solakonIrStatus } from '../api/solakonIr'
import { StatusBadge } from './StatusBadge'

export function SolakonIrPanel() {
  const [host, setHost] = useState('')
  const [connected, setConnected] = useState(false)
  const [busy, setBusy] = useState(false)
  const [status, setStatus] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    solakonIrStatus()
      .then((result) => setConnected(result.connected))
      .catch(() => setConnected(false))
  }, [])

  async function connect() {
    setBusy(true)
    setError(null)
    setStatus(null)
    try {
      const result = await connectSolakonIr(host.trim())
      setConnected(result.connected)
      // A "not connected" result is a failed attempt; surface it as an error.
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
      const result = await disconnectSolakonIr()
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
      <h2>Connect a Solakon IR meter</h2>
      <p className="config-panel__hint">
        Point the hub at a Solakon infrared meter head clipped onto your utility electricity meter.
        It reports grid import and export power and energy the inverter integration cannot see; those
        readings then chart on the dashboard.
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
          Meter host
          <input
            value={host}
            onChange={(event) => setHost(event.target.value)}
            placeholder="192.168.1.60"
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
