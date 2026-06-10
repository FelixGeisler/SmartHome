import { useEffect, useState } from 'react'
import type { Device } from './api/devices'
import { listDevices, toggleDevice } from './api/devices'
import { DeviceCard } from './components/DeviceCard'

type LoadState = 'loading' | 'ready' | 'error'

/** The device dashboard: lists every registered device and lets the user toggle each one. */
function App() {
  const [devices, setDevices] = useState<Device[]>([])
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [error, setError] = useState<string | null>(null)
  const [busyIds, setBusyIds] = useState<ReadonlySet<number>>(new Set())
  const [loadAttempt, setLoadAttempt] = useState(0)

  useEffect(() => {
    // StrictMode mounts effects twice and a retry can supersede a slow first
    // load; the stale flag keeps an outdated response from overwriting state.
    let stale = false
    listDevices()
      .then((loaded) => {
        if (!stale) {
          setDevices(loaded)
          setLoadState('ready')
        }
      })
      .catch((cause: unknown) => {
        if (!stale) {
          setError(messageOf(cause))
          setLoadState('error')
        }
      })
    return () => {
      stale = true
    }
  }, [loadAttempt])

  function retry() {
    setLoadState('loading')
    setError(null)
    setLoadAttempt((attempt) => attempt + 1)
  }

  async function handleToggle(device: Device) {
    setBusyIds((ids) => new Set(ids).add(device.id))
    setError(null)
    try {
      const updated = await toggleDevice(device.id)
      setDevices((current) =>
        current.map((existing) => (existing.id === updated.id ? updated : existing)),
      )
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setBusyIds((ids) => {
        const next = new Set(ids)
        next.delete(device.id)
        return next
      })
    }
  }

  return (
    <main className="dashboard">
      <header className="dashboard__header">
        <h1>SmartHome</h1>
        <p>Device dashboard</p>
      </header>

      {error !== null && (
        <p className="dashboard__error" role="alert">
          {error}
        </p>
      )}

      {loadState === 'loading' && <p className="dashboard__hint">Loading devices&hellip;</p>}

      {loadState === 'error' && (
        <button type="button" className="dashboard__retry" onClick={retry}>
          Retry
        </button>
      )}

      {loadState === 'ready' && devices.length === 0 && (
        <p className="dashboard__hint">No devices registered yet.</p>
      )}

      {loadState === 'ready' && devices.length > 0 && (
        <ul className="device-grid">
          {devices.map((device) => (
            <DeviceCard
              key={device.id}
              device={device}
              busy={busyIds.has(device.id)}
              onToggle={(toggled) => void handleToggle(toggled)}
            />
          ))}
        </ul>
      )}
    </main>
  )
}

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Something went wrong'
}

export default App
