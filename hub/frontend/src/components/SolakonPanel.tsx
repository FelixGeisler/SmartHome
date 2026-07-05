import { useEffect, useState } from 'react'
import { connectSolakon, disconnectSolakon, solakonStatus } from '../api/solakon'

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
      const parsedPort = port.trim() === '' ? undefined : Number(port.trim())
      const parsedUnitId = unitId.trim() === '' ? undefined : Number(unitId.trim())
      const result = await connectSolakon(host.trim(), parsedPort, parsedUnitId)
      setConnected(result.connected)
      setStatus(result.message)
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
    <section className="solakon-panel">
      <h2>Connect a Solakon inverter</h2>
      <p className="solakon-panel__hint">
        Point the hub at your Solakon ONE over Modbus TCP. Enable Modbus TCP in the Solakon app and
        connect the inverter by wired Ethernet; its power and battery readings then chart on the
        dashboard.
      </p>
      {error !== null && (
        <p className="solakon-panel__error" role="alert">
          {error}
        </p>
      )}
      {status !== null && <p className="solakon-panel__status">{status}</p>}
      <p className="solakon-panel__state">Status: {connected ? 'connected' : 'not connected'}</p>
      <div className="solakon-panel__connect">
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
