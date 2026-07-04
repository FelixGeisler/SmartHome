import { useCallback, useEffect, useRef, useState } from 'react'
import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import type { Device, DeviceCommand } from './api/devices'
import { deleteDevice, listDevices, sendCommand, toggleDevice } from './api/devices'
import { openDeviceStream } from './api/events'
import { AssistantWidget } from './components/AssistantWidget'
import { ConfigurationPage } from './pages/ConfigurationPage'
import { DashboardPage, type LoadState } from './pages/DashboardPage'

/**
 * Application shell: loads the devices and keeps them live over the event stream, owns the shared
 * device state, and routes between the Dashboard (control) and Configuration (setup) views.
 *
 * <p>Synchronization model: pushed events are applied as they arrive, and every stream (re)connect
 * triggers a full list re-sync. Because a fetched snapshot can predate events that arrive while the
 * fetch is in flight, those events are replayed on top of the fetched list instead of being
 * clobbered by it.
 */
function App() {
  const [devices, setDevices] = useState<Device[]>([])
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [error, setError] = useState<string | null>(null)
  const [busyIds, setBusyIds] = useState<ReadonlySet<number>>(new Set())
  // Bumped on every stream (re)connect; charts refetch their history window when it changes,
  // since readings that arrived during a stream gap were never pushed.
  const [syncToken, setSyncToken] = useState(0)

  // Re-sync bookkeeping: the newest sync wins (seq), and events that arrive while its fetch is in
  // flight are collected so they can be replayed over the fetched snapshot.
  const syncSeq = useRef(0)
  const pendingEvents = useRef<StreamEvent[]>([])
  const collecting = useRef(false)
  const everReady = useRef(false)

  const sync = useCallback(() => {
    const seq = ++syncSeq.current
    pendingEvents.current = []
    collecting.current = true
    listDevices()
      .then((loaded) => {
        if (seq !== syncSeq.current) {
          return // superseded by a newer sync
        }
        collecting.current = false
        setDevices(pendingEvents.current.reduce(applyStreamEvent, loaded))
        everReady.current = true
        setError(null)
        setLoadState('ready')
      })
      .catch((cause: unknown) => {
        if (seq !== syncSeq.current) {
          return
        }
        collecting.current = false
        // Only the very first load surfaces an error page. Once the dashboard has data, a failed
        // re-sync stays silent: the stream keeps applying events and the next connect retries.
        if (!everReady.current) {
          setError(messageOf(cause))
          setLoadState('error')
        }
      })
  }, [])

  // Initial load. The stream below also syncs on its first open; the seq guard makes the
  // overlap harmless, and this direct call covers a broken event stream.
  useEffect(() => {
    sync()
  }, [sync])

  // Stay live over the event stream instead of polling. Events also apply during an in-flight
  // sync (recorded for replay), so the UI reacts immediately without racing the fetch.
  useEffect(() => {
    const record = (event: StreamEvent) => {
      if (collecting.current) {
        pendingEvents.current.push(event)
      }
      setDevices((current) => applyStreamEvent(current, event))
    }
    return openDeviceStream({
      onDeviceChanged: (device) => record({ kind: 'changed', device }),
      onDeviceRemoved: (id) => record({ kind: 'removed', id }),
      onSync: () => {
        setSyncToken((token) => token + 1)
        sync()
      },
    })
  }, [sync])

  function retry() {
    setLoadState('loading')
    setError(null)
    sync()
  }

  async function handleToggle(device: Device) {
    setBusyIds((ids) => new Set(ids).add(device.id))
    setError(null)
    try {
      const updated = await toggleDevice(device.id)
      setDevices((current) => patch(current, updated))
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
      setDevices((current) => patch(current, updated))
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
      setDevices((current) => remove(current, device.id))
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
    // The stream also pushes the new device; upsert so the two paths never double-add it.
    setDevices((current) => upsert(current, device))
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
                syncToken={syncToken}
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

/** One pushed stream event, kept for replay when it arrives during an in-flight sync. */
type StreamEvent = { kind: 'changed'; device: Device } | { kind: 'removed'; id: number }

/** Replaces a device by id, or appends it when unknown. Pushed snapshots are always current. */
function upsert(current: Device[], device: Device): Device[] {
  return current.some((existing) => existing.id === device.id)
    ? current.map((existing) => (existing.id === device.id ? device : existing))
    : [...current, device]
}

/**
 * Replaces a device by id only when it is still listed. Command responses go through here rather
 * than upsert: a response says nothing about existence, so it must not resurrect a device another
 * client deleted while the command was in flight.
 */
function patch(current: Device[], device: Device): Device[] {
  return current.map((existing) => (existing.id === device.id ? device : existing))
}

/** Drops a device by id; shared by local deletion and the pushed device-removed event. */
function remove(current: Device[], id: number): Device[] {
  return current.filter((existing) => existing.id !== id)
}

function applyStreamEvent(list: Device[], event: StreamEvent): Device[] {
  return event.kind === 'changed' ? upsert(list, event.device) : remove(list, event.id)
}

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Something went wrong'
}

export default App
