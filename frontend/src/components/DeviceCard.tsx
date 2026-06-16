import type { Device, Sensor } from '../api/devices'
import { isOn, isSensing, isSwitchable } from '../api/devices'

interface DeviceCardProps {
  device: Device
  /** True while a toggle for this device is in flight; disables the button. */
  busy: boolean
  onToggle: (device: Device) => void
}

/** One dashboard tile: device name, metadata, and controls picked per capability. */
export function DeviceCard({ device, busy, onToggle }: DeviceCardProps) {
  const on = isSwitchable(device) && isOn(device)
  return (
    <li className={on ? 'device-card device-card--on' : 'device-card'}>
      <div className="device-card__info">
        <span className="device-card__name">{device.name}</span>
        <span className="device-card__meta">
          {formatType(device.type)} &middot; {device.externalId}
        </span>
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
      {isSensing(device) && (
        <dl className="device-card__readings">
          {device.sensors.map((sensor) => (
            <div className="device-card__reading" key={sensor.key}>
              <dt>{sensor.key}</dt>
              <dd>{formatReading(sensor)}</dd>
            </div>
          ))}
        </dl>
      )}
    </li>
  )
}

/** Renders a reading as value plus unit, or an em dash before the first reading arrives. */
function formatReading(sensor: Sensor): string {
  return sensor.value === null ? '—' : `${sensor.value} ${sensor.unit}`
}

/** Renders an enum constant like SHELLY_PLUG as "Shelly Plug". */
function formatType(type: string): string {
  return type
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}
