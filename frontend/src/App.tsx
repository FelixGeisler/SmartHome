import { useEffect, useRef, useState } from 'react'
import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import type { Device, DeviceCommand } from './api/devices'
import { deleteDevice, listDevices, sendCommand, toggleDevice } from './api/devices'
import { AssistantWidget } from './components/AssistantWidget'
import { ConfigurationPage } from './pages/ConfigurationPage'
import { DashboardPage, type LoadState } from './pages/DashboardPage'

/** How often the dashboard re-fetches the device list so values update without a reload. */
const POLL_INTERVAL_MS = 5000

/**
 * Application shell: loads the devices and refreshes them on an interval, owns the shared device
 * state, and routes between the Dashboard (control) and Configuration (setup) views.
 */
function App() {
  const [devices, setDevices] = useState<Device[]>([])
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [error, setError] = useState<string | null>(null)
  const [busyIds, setBusyIds] = useState<ReadonlySet<number>>(new Set())
  const [loadAttempt, setLoadAttempt] = useState(0)
  // Latest busy set, read by the poll without re-arming its interval on every change.
  const busyIdsRef = useRef(busyIds)
  useEffect(() => {
    busyIdsRef.current = busyIds
  }, [busyIds])

  useEffect(() => {
    // StrictMode mounts effects twice, and a retry can supersede a slow first
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

  // Poll the device list so newly auto-provisioned devices and fresh sensor readings appear live,
  // without a manual reload. A device whose command is in flight keeps its optimistic state.
  useEffect(() => {
    const id = setInterval(() => {
      listDevices()
        .then((loaded) => {
          setDevices((current) => mergeDevices(current, loaded, busyIdsRef.current))
        })
        .catch(() => {
          // Keep the last good data on a transient poll failure; the next tick retries.
        })
    }, POLL_INTERVAL_MS)
    return () => clearInterval(id)
  }, [])

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

  async function handleCommand(device: Device, command: DeviceCommand) {
    setBusyIds((ids) => new Set(ids).add(device.id))
    setError(null)
    try {
      const updated = await sendCommand(device.id, command)
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

  async function handleDelete(device: Device) {
    setBusyIds((ids) => new Set(ids).add(device.id))
    setError(null)
    try {
      await deleteDevice(device.id)
      setDevices((current) => current.filter((existing) => existing.id !== device.id))
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
                onCommand={(device, command) => void handleCommand(device, command)}
                onDelete={(device) => void handleDelete(device)}
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

      <AssistantWidget />
    </div>
  )
}

function navClass({ isActive }: { isActive: boolean }): string {
  return isActive ? 'app__nav-link app__nav-link--active' : 'app__nav-link'
}

function mergeDevices(
  current: Device[],
  loaded: Device[],
  busyIds: ReadonlySet<number>,
): Device[] {
  const byId = new Map(current.map((device) => [device.id, device] as const))
  return loaded.map((device) =>
    busyIds.has(device.id) ? (byId.get(device.id) ?? device) : device,
  )
}

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Something went wrong'
}

export default App
