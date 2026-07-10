import { useCallback, useRef, useState } from 'react'
import ReactGridLayout from 'react-grid-layout'
import type { Layout } from 'react-grid-layout'
import type { CardLayout } from '../api/dashboard'
import type { Device, DeviceCommand } from '../api/devices'
import { CardPicker } from '../components/CardPicker'
import { DeviceCard } from '../components/DeviceCard'
import { EditToolbar } from '../components/EditToolbar'
import { GRID_COLS } from '../dashboardLayout'

export type LoadState = 'loading' | 'ready' | 'error'

interface DashboardPageProps {
  devices: Device[]
  layout: Layout
  cardLayouts: CardLayout[]
  loadState: LoadState
  error: string | null
  busyIds: ReadonlySet<number>
  syncToken?: number
  editing: boolean
  saving?: boolean
  addable: Device[]
  onToggle: (device: Device) => void
  onCommand: (device: Device, command: DeviceCommand) => void
  onRemoveCard: (device: Device) => void
  onToggleSensor: (deviceId: number, sensorKey: string) => void
  onRetry: () => void
  onEnterEdit: () => void
  onSave: () => void
  onCancel: () => void
  onLayoutChange: (layout: Layout) => void
  onAddCard: (device: Device) => void
}

const GRID_CONFIG = {
  cols: GRID_COLS,
  rowHeight: 24,
  margin: [16, 16] as [number, number],
  containerPadding: [0, 0] as [number, number],
}
// Width used before the container is measured (and in test environments with no layout).
const FALLBACK_WIDTH = 1200
// Interactive controls must not start a drag; grabbing the card body or header moves it instead.
const DRAG_CANCEL = 'button, input, select, a'

export function DashboardPage({
  devices,
  layout,
  cardLayouts,
  loadState,
  error,
  busyIds,
  syncToken = 0,
  editing,
  saving = false,
  addable,
  onToggle,
  onCommand,
  onRemoveCard,
  onToggleSensor,
  onRetry,
  onEnterEdit,
  onSave,
  onCancel,
  onLayoutChange,
  onAddCard,
}: DashboardPageProps) {
  const [pickerOpen, setPickerOpen] = useState(false)
  // Hand the grid the wrapper's own (constrained) clientWidth, not the grid's overflowing content
  // width, since react-grid-layout sizes its cards from this number. A ref callback measures once
  // the wrapper appears, which only happens after devices load.
  const [gridWidth, setGridWidth] = useState(0)
  const observerRef = useRef<ResizeObserver | null>(null)
  const measureRef = useCallback((element: HTMLDivElement | null) => {
    observerRef.current?.disconnect()
    if (element === null || typeof ResizeObserver === 'undefined') {
      return
    }
    const measure = () => setGridWidth(element.clientWidth || FALLBACK_WIDTH)
    measure()
    observerRef.current = new ResizeObserver(measure)
    observerRef.current.observe(element)
  }, [])

  const shown = new Set(layout.map((item) => item.i))
  const cards = devices.filter((device) => shown.has(String(device.id)))
  const hiddenByDevice = new Map(
    cardLayouts.map((card) => [card.deviceId, card.hiddenSensors ?? []]),
  )

  return (
    <section className="dashboard">
      {error !== null && (
        <p className="dashboard__error" role="alert">
          {error}
        </p>
      )}

      {loadState === 'loading' && <p className="dashboard__hint">Loading devices&hellip;</p>}

      {loadState === 'error' && (
        <button type="button" className="dashboard__retry" onClick={onRetry}>
          Retry
        </button>
      )}

      {loadState === 'ready' && (
        <>
          {devices.length > 0 && (
            <div className="dashboard__bar">
              <h2 className="dashboard__title">Devices</h2>
              <EditToolbar
                editing={editing}
                saving={saving}
                onEnterEdit={onEnterEdit}
                onSave={onSave}
                onCancel={onCancel}
                onAddCard={() => setPickerOpen(true)}
              />
            </div>
          )}

          {cards.length === 0 ? (
            <p className="dashboard__hint">{emptyHint(devices.length, editing)}</p>
          ) : (
            <div ref={measureRef}>
              {gridWidth > 0 && (
                <ReactGridLayout
                  className={editing ? 'device-grid device-grid--editing' : 'device-grid'}
                  layout={layout}
                  width={gridWidth}
                  gridConfig={GRID_CONFIG}
                  dragConfig={{ enabled: editing, cancel: DRAG_CANCEL }}
                  resizeConfig={{ enabled: editing }}
                  onLayoutChange={editing ? onLayoutChange : undefined}
                >
                  {cards.map((device) => (
                    <div key={String(device.id)}>
                      <DeviceCard
                        device={device}
                        editing={editing}
                        busy={busyIds.has(device.id)}
                        syncToken={syncToken}
                        hiddenSensors={hiddenByDevice.get(device.id) ?? []}
                        onToggle={onToggle}
                        onCommand={onCommand}
                        onRemove={onRemoveCard}
                        onToggleSensor={onToggleSensor}
                      />
                    </div>
                  ))}
                </ReactGridLayout>
              )}
            </div>
          )}

          <CardPicker
            open={pickerOpen}
            devices={addable}
            onClose={() => setPickerOpen(false)}
            onAdd={onAddCard}
          />
        </>
      )}
    </section>
  )
}

function emptyHint(deviceCount: number, editing: boolean): string {
  if (deviceCount === 0) {
    return 'No devices yet. Register one in Configuration.'
  }
  if (editing) {
    return 'No cards yet. Use + to add cards.'
  }
  return 'No cards on the dashboard. Use Edit to add some.'
}
