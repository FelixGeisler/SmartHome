import { useEffect, useRef } from 'react'
import type { Device, DeviceCommand } from '../api/devices'
import { brightnessOf, colorTemperatureKOf, colorXyOf } from '../api/devices'
import { hexToXy, xyToHex } from '../color'

/** Hue's tunable-white range, also a sensible window for the color-temperature slider. */
const MIN_KELVIN = 2000
const MAX_KELVIN = 6500
const DEFAULT_KELVIN = 2700
const DEFAULT_BRIGHTNESS = 100
const DEFAULT_COLOR = '#ffffff'

interface ControlProps {
  device: Device
  onCommand: (device: Device, command: DeviceCommand) => void
}

/** A brightness slider; commits a command to release, not on every step. */
export function BrightnessControl({ device, onCommand }: ControlProps) {
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
export function ColorControl({ device, onCommand }: ControlProps) {
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
export function ColorTemperatureControl({ device, onCommand }: ControlProps) {
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
 * on the native {@code change} event (slider release or color-picker close), so a single gesture
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
