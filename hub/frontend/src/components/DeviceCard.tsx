import type { Device, DeviceCommand } from '../api/devices'
import {
  formatReading,
  hasColor,
  hasColorTemperature,
  isDimmable,
  isOn,
  isSensing,
  isSwitchable,
} from '../api/devices'
import { BrightnessControl, ColorControl, ColorTemperatureControl } from './deviceControls'
import { SensorChart } from './SensorChart'

interface DeviceCardProps {
  device: Device
  /** True, while a command for this device is in flight; disables the toggle. */
  busy: boolean
  /** Bumped on every event-stream (re)connect; charts refetch their history when it changes. */
  syncToken?: number
  /** True when the dashboard is in edit mode; shows the card as draggable/resizable. */
  editing?: boolean
  onToggle: (device: Device) => void
  onCommand: (device: Device, command: DeviceCommand) => void
  /** Removes this card from the dashboard (edit mode only); the device stays registered. */
  onRemove: (device: Device) => void
}

/** One dashboard tile: device name, metadata, controls per capability, and sensor charts. */
export function DeviceCard({
  device,
  busy,
  syncToken = 0,
  editing = false,
  onToggle,
  onCommand,
  onRemove,
}: DeviceCardProps) {
  const on = isSwitchable(device) && isOn(device)
  const className = ['device-card', on ? 'device-card--on' : '', editing ? 'device-card--editing' : '']
    .filter(Boolean)
    .join(' ')
  return (
    <div className={className}>
      <div className="device-card__header">
        <div className="device-card__info">
          <span className="device-card__name">{device.name}</span>
          <span className="device-card__meta">
            {formatType(device.type)} &middot; {device.externalId}
          </span>
        </div>
        {editing && (
          <button
            type="button"
            className="device-card__delete"
            aria-label={`Remove ${device.name}`}
            title="Remove card"
            onClick={() => onRemove(device)}
          >
            <TrashIcon />
          </button>
        )}
      </div>
      {isSwitchable(device) && (
        <div className="device-card__state">
          <span className="device-card__status">
            <span className="device-card__dot" aria-hidden="true" />
            {on ? 'On' : 'Off'}
          </span>
          <button
            type="button"
            className="device-card__toggle"
            disabled={busy}
            aria-label={`Turn ${device.name} ${on ? 'off' : 'on'}`}
            onClick={() => onToggle(device)}
          >
            {on ? 'Turn off' : 'Turn on'}
          </button>
        </div>
      )}
      {(isDimmable(device) || hasColor(device) || hasColorTemperature(device)) && (
        <div className="device-card__controls">
          {isDimmable(device) && <BrightnessControl device={device} onCommand={onCommand} />}
          {hasColor(device) && <ColorControl device={device} onCommand={onCommand} />}
          {hasColorTemperature(device) && (
            <ColorTemperatureControl device={device} onCommand={onCommand} />
          )}
        </div>
      )}
      {isSensing(device) && (
        <div className="device-card__sensors">
          {device.sensors.map((sensor) => (
            <div className="sensor" key={sensor.key}>
              <div className="sensor__head">
                <span className="sensor__key">{sensor.key}</span>
                <span className="sensor__value">{formatReading(sensor)}</span>
              </div>
              <SensorChart
                deviceExternalId={device.externalId}
                sensor={sensor}
                syncToken={syncToken}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

/** A trash-can glyph for the delete button. */
function TrashIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="16"
      height="16"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <polyline points="3 6 5 6 21 6" />
      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
      <line x1="10" y1="11" x2="10" y2="17" />
      <line x1="14" y1="11" x2="14" y2="17" />
    </svg>
  )
}

/** Renders an enum constant like SHELLY_PLUG as "Shelly Plug". */
function formatType(type: string): string {
  return type
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}
