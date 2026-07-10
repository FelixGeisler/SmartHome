import { useEffect, useRef } from 'react'
import type { Device, DeviceCommand } from '../api/devices'
import { brightnessOf, colorTemperatureKOf, colorXyOf } from '../api/devices'
import { hexToXy, xyToHex } from '../color'

/** Hue's tunable-white range, reused for the slider. */
const MIN_KELVIN = 2000
const MAX_KELVIN = 6500
const DEFAULT_KELVIN = 2700
const DEFAULT_BRIGHTNESS = 100
const DEFAULT_COLOR = '#ffffff'

interface ControlProps {
  device: Device
  onCommand: (device: Device, command: DeviceCommand) => void
}

/** A brightness slider; commits on release, not every step. */
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

/** A color picker; commits as CIE xy. */
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
 * Uncontrolled input that commits only on the native change event (slider release / color-picker
 * close), so one gesture sends one command. The key resets it when the value changes elsewhere
 * (e.g., another client moved it).
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
