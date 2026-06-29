import type { Device, DeviceCommand } from '../api/devices'
import { DeviceCard } from '../components/DeviceCard'
import type { SensorHistory } from '../sensorHistory'

export type LoadState = 'loading' | 'ready' | 'error'

interface DashboardPageProps {
  devices: Device[]
  loadState: LoadState
  error: string | null
  busyIds: ReadonlySet<number>
  history: SensorHistory
  onToggle: (device: Device) => void
  onCommand: (device: Device, command: DeviceCommand) => void
  onDelete: (device: Device) => void
  onRetry: () => void
}

/** The dashboard view lists every registered device and lets the user control each one. */
export function DashboardPage({
  devices,
  loadState,
  error,
  busyIds,
  history,
  onToggle,
  onCommand,
  onDelete,
  onRetry,
}: DashboardPageProps) {
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

      {loadState === 'ready' && devices.length === 0 && (
        <p className="dashboard__hint">No devices yet — add one in Configuration.</p>
      )}

      {loadState === 'ready' && devices.length > 0 && (
        <ul className="device-grid">
          {devices.map((device) => (
            <DeviceCard
              key={device.id}
              device={device}
              busy={busyIds.has(device.id)}
              history={history}
              onToggle={onToggle}
              onCommand={onCommand}
              onDelete={onDelete}
            />
          ))}
        </ul>
      )}
    </section>
  )
}
