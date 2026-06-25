import { useEffect, useRef } from 'react'
import type { Device, DeviceCommand, Sensor } from '../api/devices'
import {
  brightnessOf,
  colorTemperatureKOf,
  colorXyOf,
  hasColor,
  hasColorTemperature,
  isDimmable,
  isOn,
  isSensing,
  isSwitchable,
} from '../api/devices'
import { hexToXy, xyToHex } from '../color'

/** Hue's tunable-white range, also a sensible window for the color-temperature slider. */
const MIN_KELVIN = 2000
const MAX_KELVIN = 6500
const DEFAULT_KELVIN = 2700
const DEFAULT_BRIGHTNESS = 100
const DEFAULT_COLOR = '#ffffff'

interface DeviceCardProps {
  device: Device
  /** True, while a command for this device is in flight; disables the toggle. */
  busy: boolean
  onToggle: (device: Device) => void
  onCommand: (device: Device, command: DeviceCommand) => void
}

/** One dashboard tile: device name, metadata, and controls picked per capability. */
export function DeviceCard({ device, busy, onToggle, onCommand }: DeviceCardProps) {
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
      {(isDimmable(device) || hasColor(device) || hasColorTemperature(device)) && (
        <div className="device-card__controls">
          {isDimmable(device) && (
            <BrightnessControl device={device} onCommand={onCommand} />
          )}
          {hasColor(device) && <ColorControl device={device} onCommand={onCommand} />}
          {hasColorTemperature(device) && (
            <ColorTemperatureControl device={device} onCommand={onCommand} />
          )}
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

interface ControlProps {
  device: Device
  onCommand: (device: Device, command: DeviceCommand) => void
}

/** A brightness slider; commits a command to release, not on every step. */
function BrightnessControl({ device, onCommand }: ControlProps) {
  const value = brightnessOf(device) ?? DEFAULT_BRIGHTNESS
  return (
    <label className="device-card__control">
      <span className="device-card__control-label">Brightness</span>
      <CommitInput
        type="range"
        min={1}
        max={100}
        value={String(value)}
        ariaLabel={`Brightness for ${device.name}`}
        onCommit={(raw) => onCommand(device, { brightness: Number(raw) })}
      />
      <span className="device-card__control-value">{value}%</span>
    </label>
  )
}

/** A color picker; commits the chosen color as CIE xy. */
function ColorControl({ device, onCommand }: ControlProps) {
  const xy = colorXyOf(device)
  const value = xy === null ? DEFAULT_COLOR : xyToHex(xy.x, xy.y)
  return (
    <label className="device-card__control">
      <span className="device-card__control-label">Color</span>
      <CommitInput
        type="color"
        value={value}
        ariaLabel={`Color for ${device.name}`}
        onCommit={(raw) => onCommand(device, { colorXy: hexToXy(raw) })}
      />
    </label>
  )
}

/** A color-temperature slider in Kelvin. */
function ColorTemperatureControl({ device, onCommand }: ControlProps) {
  const value = colorTemperatureKOf(device) ?? DEFAULT_KELVIN
  return (
    <label className="device-card__control">
      <span className="device-card__control-label">Warmth</span>
      <CommitInput
        type="range"
        min={MIN_KELVIN}
        max={MAX_KELVIN}
        value={String(value)}
        ariaLabel={`Color temperature for ${device.name}`}
        onCommit={(raw) => onCommand(device, { colorTemperatureK: Number(raw) })}
      />
      <span className="device-card__control-value">{value}K</span>
    </label>
  )
}

interface CommitInputProps {
  type: 'range' | 'color'
  value: string
  ariaLabel: string
  min?: number
  max?: number
  onCommit: (value: string) => void
}

/**
 * An uncontrolled input that lets the browser handle the live dragging but only commits a command
 * on the native {@code change} event — slider release or color-picker close — so a single gesture
 * sends one command, not one per step. The {@code key} resets the input to the committed value
 * whenever it changes elsewhere (e.g., another client moved it).
 */
function CommitInput({ type, value, ariaLabel, min, max, onCommit }: CommitInputProps) {
  const ref = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const input = ref.current
    if (input === null) {
      return
    }
    const commit = () => onCommit(input.value)
    input.addEventListener('change', commit)
    return () => input.removeEventListener('change', commit)
  }, [onCommit, value])

  return (
    <input
      key={value}
      ref={ref}
      type={type}
      min={min}
      max={max}
      defaultValue={value}
      aria-label={ariaLabel}
    />
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
