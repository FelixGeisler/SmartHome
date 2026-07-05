import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import type { Layout } from 'react-grid-layout'
import type { CardLayout } from './api/dashboard'
import { getLayout, saveLayout } from './api/dashboard'
import type { Device, DeviceCommand } from './api/devices'
import { listDevices, sendCommand, toggleDevice } from './api/devices'
import { openDeviceStream } from './api/events'
import { AssistantWidget } from './components/AssistantWidget'
import {
  addCard,
  available,
  curate,
  displayCards,
  fromGridLayout,
  removeCard,
  toGridLayout,
} from './dashboardLayout'
import { ConfigurationPage } from './pages/ConfigurationPage'
import { DashboardPage, type LoadState } from './pages/DashboardPage'
import { RoomsFloorPlan } from './pages/RoomsFloorPlan'

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

  // The saved dashboard arrangement, or null until one has ever been saved (a first-run dashboard
  // shows every device; a saved-but-empty layout stays empty). `draft` holds the unsaved edit copy
  // while `editing`; the committed `layout` is what other views and a reload see.
  const [layout, setLayout] = useState<CardLayout[] | null>(null)
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState<CardLayout[]>([])
  const [saving, setSaving] = useState(false)

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

  // Load the saved dashboard arrangement once. A missing or unreadable layout just leaves the
  // default device order; the layout is advisory and reconciled against the live device list.
  useEffect(() => {
    getLayout()
      .then((saved) => setLayout(saved ? saved.cards : null))
      .catch(() => {
        // The hub is briefly unreachable; leave the layout unset (every device shows).
      })
  }, [])

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

  function handleRegistered(device: Device) {
    // The stream also pushes the new device; upsert so the two paths never double-add it.
    setDevices((current) => upsert(current, device))
  }

  function handleDeviceUpdated(device: Device) {
    // A room assignment returns the updated device; fold it in the way command responses are, so
    // the change shows immediately without waiting for the stream to echo it.
    setDevices((current) => patch(current, device))
  }

  // The cards on the dashboard: the curated draft while editing, else the committed layout (with a
  // tidy every-device default before the dashboard has ever been arranged). Live stream events (a
  // device added or removed elsewhere) flow through, so the grid stays consistent.
  const gridLayout = useMemo(
    () => toGridLayout(editing ? curate(devices, draft) : displayCards(devices, layout)),
    [devices, editing, draft, layout],
  )
  // The devices the add-card picker can offer: those not already on the (draft) dashboard.
  const addableDevices = useMemo(() => available(devices, draft), [devices, draft])

  function enterEdit() {
    // Seed the draft from what is on screen, so arranging starts from the current cards.
    setDraft(displayCards(devices, layout))
    setEditing(true)
  }

  function cancelEdit() {
    setEditing(false)
  }

  async function commitEdit() {
    setSaving(true)
    setError(null)
    try {
      await saveLayout({ cards: draft })
      setLayout(draft)
      setEditing(false)
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setSaving(false)
    }
  }

  // Each drag or resize hands back the whole new grid layout; keep the draft in step with it.
  function handleLayoutChange(next: Layout) {
    setDraft(fromGridLayout(next))
  }

  function handleAddCard(device: Device) {
    setDraft((current) => addCard(current, device))
  }

  function handleRemoveCard(device: Device) {
    setDraft((current) => removeCard(current, device.id))
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
          <NavLink to="/rooms" className={navClass}>
            Rooms
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
                layout={gridLayout}
                loadState={loadState}
                error={error}
                busyIds={busyIds}
                syncToken={syncToken}
                editing={editing}
                saving={saving}
                addable={addableDevices}
                onToggle={(device) => void handleToggle(device)}
                onCommand={(device, command) => void handleCommand(device, command)}
                onRemoveCard={handleRemoveCard}
                onRetry={retry}
                onEnterEdit={enterEdit}
                onSave={() => void commitEdit()}
                onCancel={cancelEdit}
                onLayoutChange={handleLayoutChange}
                onAddCard={handleAddCard}
              />
            }
          />
          <Route
            path="/rooms"
            element={
              <RoomsFloorPlan
                devices={devices}
                busyIds={busyIds}
                onToggle={(device) => void handleToggle(device)}
                onCommand={(device, command) => void handleCommand(device, command)}
                onDeviceUpdated={handleDeviceUpdated}
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
