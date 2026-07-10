import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import type { Layout } from 'react-grid-layout'
import { logout } from './api/auth'
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
  toggleHiddenSensor,
} from './dashboardLayout'
import { AutomationsPage } from './pages/AutomationsPage'
import { ConfigurationPage } from './pages/ConfigurationPage'
import { DashboardPage, type LoadState } from './pages/DashboardPage'
import { RoomsFloorPlan } from './pages/RoomsFloorPlan'

/**
 * Application shell: owns the shared device state and keeps it live over the event stream.
 *
 * <p>A fetched snapshot can predate events that arrive while its fetch is in flight, so those
 * events are replayed on top of the fetched list instead of being clobbered by it.
 */
function App() {
  const [devices, setDevices] = useState<Device[]>([])
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [error, setError] = useState<string | null>(null)
  const [busyIds, setBusyIds] = useState<ReadonlySet<number>>(new Set())
  // Bumped on every stream (re)connect so charts refetch history missed during a stream gap.
  const [syncToken, setSyncToken] = useState(0)

  // null until a layout has ever been saved: a first-run dashboard shows every device, a
  // saved-but-empty one stays empty. `draft` is the unsaved edit copy; `layout` is committed.
  const [layout, setLayout] = useState<CardLayout[] | null>(null)
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState<CardLayout[]>([])
  const [saving, setSaving] = useState(false)

  // The newest sync wins (seq); events arriving during its in-flight fetch are collected for replay.
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

  // Initial load; also covers a broken event stream. The seq guard makes the overlap with the
  // stream's own first-open sync harmless.
  useEffect(() => {
    sync()
  }, [sync])

  // The layout is advisory: a missing or unreadable one just leaves the default device order.
  useEffect(() => {
    getLayout()
      .then((saved) => setLayout(saved ? saved.cards : null))
      .catch(() => {
        // The hub is briefly unreachable; leave the layout unset (every device shows).
      })
  }, [])

  // Save an auto-placement, debounced so a batch of adds coalesces into one write.
  const layoutDirty = useRef(false)
  const layoutSaveTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(() => {
    if (!layoutDirty.current || layout === null) {
      return
    }
    layoutDirty.current = false
    const cards = layout
    if (layoutSaveTimer.current !== null) {
      clearTimeout(layoutSaveTimer.current)
    }
    layoutSaveTimer.current = setTimeout(() => {
      void saveLayout({ cards }).catch(() => {
        // A failed save just leaves the device in the add-card picker; nothing else is lost.
      })
    }, 250)
  }, [layout])

  // Events also apply during an in-flight sync (recorded for replay), so the UI never races the fetch.
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
    // Auto-place a hand-added device onto an arranged dashboard; stream-provisioned ones don't.
    setLayout((current) => {
      if (current === null || current.some((card) => card.deviceId === device.id)) {
        return current
      }
      layoutDirty.current = true
      return addCard(current, device)
    })
  }

  function handleDeviceUpdated(device: Device) {
    // Fold in the updated device now, so a rename or room change shows without waiting for the
    // stream to echo it.
    setDevices((current) => patch(current, device))
  }

  function handleDeviceDeleted(id: number) {
    // Drop it now so the change is immediate; the stream's later removed event finds nothing to remove.
    setDevices((current) => remove(current, id))
  }

  // Curated draft while editing, else the committed layout (a tidy every-device default before it
  // has ever been arranged). Live stream events flow through so the grid stays consistent.
  const cards = useMemo(
    () => (editing ? curate(devices, draft) : displayCards(devices, layout)),
    [devices, editing, draft, layout],
  )
  const gridLayout = useMemo(() => toGridLayout(cards), [cards])
  const addableDevices = useMemo(() => available(devices, draft), [devices, draft])

  function enterEdit() {
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

  // The grid layout holds only geometry, so carry over each card's hidden-chart selection.
  function handleLayoutChange(next: Layout) {
    setDraft((current) => fromGridLayout(next, current))
  }

  function handleAddCard(device: Device) {
    setDraft((current) => addCard(current, device))
  }

  function handleRemoveCard(device: Device) {
    setDraft((current) => removeCard(current, device.id))
  }

  function handleToggleSensor(deviceId: number, sensorKey: string) {
    setDraft((current) => toggleHiddenSensor(current, deviceId, sensorKey))
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
          <NavLink to="/automations" className={navClass}>
            Automations
          </NavLink>
        </nav>
        <div className="app__actions">
          <NavLink to="/configuration" className={iconNavClass} aria-label="Configuration">
            <GearIcon />
          </NavLink>
          <button
            type="button"
            className="app__logout"
            aria-label="Log out"
            title="Log out"
            onClick={() => void logout().then(() => window.location.reload())}
          >
            <LogoutIcon />
          </button>
        </div>
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
                cardLayouts={cards}
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
                onToggleSensor={handleToggleSensor}
                onRetry={retry}
                onEnterEdit={enterEdit}
                onSave={() => void commitEdit()}
                onCancel={cancelEdit}
                onLayoutChange={handleLayoutChange}
                onAddCard={handleAddCard}
              />
            }
          />
          <Route path="/automations" element={<AutomationsPage devices={devices} />} />
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
            element={
              <ConfigurationPage
                devices={devices}
                onRegistered={handleRegistered}
                onDeviceUpdated={handleDeviceUpdated}
                onDeviceDeleted={handleDeviceDeleted}
              />
            }
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

function iconNavClass({ isActive }: { isActive: boolean }): string {
  return isActive ? 'app__icon-link app__icon-link--active' : 'app__icon-link'
}

function GearIcon() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  )
}

function LogoutIcon() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <polyline points="16 17 21 12 16 7" />
      <line x1="21" y1="12" x2="9" y2="12" />
    </svg>
  )
}

type StreamEvent = { kind: 'changed'; device: Device } | { kind: 'removed'; id: number }

function upsert(current: Device[], device: Device): Device[] {
  return current.some((existing) => existing.id === device.id)
    ? current.map((existing) => (existing.id === device.id ? device : existing))
    : [...current, device]
}

/**
 * Replaces a device by id only when it is still listed, so a command response never resurrects a
 * device another client deleted while the command was in flight.
 */
function patch(current: Device[], device: Device): Device[] {
  return current.map((existing) => (existing.id === device.id ? device : existing))
}

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
