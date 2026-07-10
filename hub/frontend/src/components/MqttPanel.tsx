import { useEffect, useState } from 'react'
import { connectMqtt, disconnectMqtt, mqttStatus } from '../api/mqtt'
import { StatusBadge } from './StatusBadge'

export function MqttPanel() {
  const [host, setHost] = useState('')
  const [port, setPort] = useState('1883')
  const [connected, setConnected] = useState(false)
  const [busy, setBusy] = useState(false)
  const [status, setStatus] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    mqttStatus()
      .then((result) => setConnected(result.connected))
      .catch(() => setConnected(false))
  }, [])

  async function connect() {
    setBusy(true)
    setError(null)
    setStatus(null)
    try {
      const trimmedPort = port.trim()
      const parsedPort = trimmedPort === '' ? undefined : Number(trimmedPort)
      const result = await connectMqtt(host.trim(), parsedPort)
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
      const result = await disconnectMqtt()
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
      <h2>Connect an MQTT broker</h2>
      <p className="config-panel__hint">
        Point the hub at your broker. Sensor nodes publishing to it appear on the dashboard
        automatically.
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
          Broker host
          <input
            value={host}
            onChange={(event) => setHost(event.target.value)}
            placeholder="192.168.1.21"
            disabled={busy}
          />
        </label>
        <label className="add-device__field">
          Port
          <input
            value={port}
            onChange={(event) => setPort(event.target.value)}
            placeholder="1883"
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
