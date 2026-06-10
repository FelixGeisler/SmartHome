import type { Device } from '../api/devices'

interface DeviceCardProps {
  device: Device
  /** True while a toggle for this device is in flight; disables the button. */
  busy: boolean
  onToggle: (device: Device) => void
}

/** One dashboard tile: device name, metadata, power state, and a toggle button. */
export function DeviceCard({ device, busy, onToggle }: DeviceCardProps) {
  return (
    <li className={device.on ? 'device-card device-card--on' : 'device-card'}>
      <div className="device-card__info">
        <span className="device-card__name">{device.name}</span>
        <span className="device-card__meta">
          {formatType(device.type)} &middot; {device.externalId}
        </span>
      </div>
      <div className="device-card__state">
        <span className="device-card__status">
          <span className="device-card__dot" aria-hidden="true" />
          {device.on ? 'On' : 'Off'}
        </span>
        <button
          type="button"
          className="device-card__toggle"
          disabled={busy}
          aria-label={`Turn ${device.name} ${device.on ? 'off' : 'on'}`}
          onClick={() => onToggle(device)}
        >
          {device.on ? 'Turn off' : 'Turn on'}
        </button>
      </div>
    </li>
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
