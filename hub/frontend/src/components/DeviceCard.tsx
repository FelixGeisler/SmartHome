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
import { SensorChart } from './SensorChart'

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
  onDelete: (device: Device) => void
}

/** One dashboard tile: device name, metadata, controls per capability, and sensor charts. */
export function DeviceCard({
  device,
  busy,
  onToggle,
  onCommand,
  onDelete,
}: DeviceCardProps) {
  const on = isSwitchable(device) && isOn(device)
  return (
    <li className={on ? 'device-card device-card--on' : 'device-card'}>
      <div className="device-card__header">
        <div className="device-card__info">
          <span className="device-card__name">{device.name}</span>
          <span className="device-card__meta">
            {formatType(device.type)} &middot; {device.externalId}
          </span>
        </div>
        <button
          type="button"
          className="device-card__delete"
          disabled={busy}
          aria-label={`Remove ${device.name}`}
          title="Remove device"
          onClick={() => onDelete(device)}
        >
          <TrashIcon />
        </button>
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
                sensorKey={sensor.key}
                unit={sensor.unit}
              />
            </div>
          ))}
        </div>
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
