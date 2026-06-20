import { useEffect, useState } from 'react'
import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import type { Device } from './api/devices'
import { listDevices, toggleDevice } from './api/devices'
import { ConfigurationPage } from './pages/ConfigurationPage'
import { DashboardPage, type LoadState } from './pages/DashboardPage'

/**
 * Application shell: loads the devices once, owns the shared device state, and routes between
 * the Dashboard (control) and Configuration (setup) views.
 */
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

  function handleRegistered(device: Device) {
    setDevices((current) => [...current, device])
  }

  return (
    <div className="app">
      <header className="app__header">
        <div className="app__brand">
          <h1>SmartHome</h1>
        </div>
        <nav className="app__nav">
          <NavLink to="/dashboard" className={navClass}>
            Dashboard
          </NavLink>
          <NavLink to="/configuration" className={navClass}>
            Configuration
          </NavLink>
        </nav>
      </header>

      <main className="app__main">
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route
            path="/dashboard"
            element={
              <DashboardPage
                devices={devices}
                loadState={loadState}
                error={error}
                busyIds={busyIds}
                onToggle={(device) => void handleToggle(device)}
                onRetry={retry}
              />
            }
          />
          <Route
            path="/configuration"
            element={<ConfigurationPage onRegistered={handleRegistered} />}
          />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </main>
    </div>
  )
}

function navClass({ isActive }: { isActive: boolean }): string {
  return isActive ? 'app__nav-link app__nav-link--active' : 'app__nav-link'
}

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Something went wrong'
}

export default App
