import type { Device, Sensor } from './api/devices'
import { hasColor, hasColorTemperature, isDimmable, isSensing, isSwitchable } from './api/devices'

/** The visual kind of a device's floor-plan symbol. */
export type IconKind = 'bulb' | 'plug' | 'solar' | 'thermometer' | 'humidity' | 'power' | 'gauge'

/** Reading types shown before others as a device's headline: produced power, then climate. */
const PRIMARY_ORDER = ['PV_POWER', 'OUTPUT_POWER', 'TEMPERATURE', 'HUMIDITY']

/**
 * The device's primary reading, the one worth showing on the floor: a produced-power channel for an
 * inverter, else temperature, else humidity, else the first declared sensor. Null for a non-sensor.
 *
 * @param device the device to read
 * @returns the headline sensor, or null when the device declares none
 */
export function primarySensor(device: Device): Sensor | null {
  for (const type of PRIMARY_ORDER) {
    const match = device.sensors.find((sensor) => sensor.type === type)
    if (match !== undefined) {
      return match
    }
  }
  return device.sensors[0] ?? null
}

/** Short acronyms kept uppercase when humanizing a reading key. */
const READING_ACRONYMS: Record<string, string> = { pv: 'PV', co2: 'CO2', soc: 'SoC' }

/**
 * A short human label for a reading, derived from its key: "batteryTemp" becomes "Battery Temp",
 * "pvPower" becomes "PV Power", "co2" becomes "CO2". Lets a device say which value is which.
 *
 * @param sensor the reading to label
 * @returns a title-cased label
 */
export function sensorLabel(sensor: Sensor): string {
  return sensor.key
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .split(' ')
    .filter((word) => word.length > 0)
    .map((word) => {
      const lower = word.toLowerCase()
      return READING_ACRONYMS[lower] ?? lower.charAt(0).toUpperCase() + lower.slice(1)
    })
    .join(' ')
}

/**
 * Chooses a floor-plan glyph for a device: a bulb for anything light-like, a sun for the solar
 * inverter, a reading-specific gauge for a sensor, and a plug for a plain switch. Capabilities and
 * the solar type are checked before the generic sensor and switch cases, so a lamp never reads as a
 * plug and the inverter (which also reports temperatures) never reads as a thermometer.
 *
 * @param device the device to symbolize
 * @returns the glyph kind
 */
export function deviceIconKind(device: Device): IconKind {
  const lightLike =
    isDimmable(device) ||
    hasColor(device) ||
    hasColorTemperature(device) ||
    device.type === 'HUE_LIGHT'
  if (lightLike) {
    return 'bulb'
  }
  if (device.type === 'SOLAR_INVERTER') {
    return 'solar'
  }
  if (isSensing(device) && !isSwitchable(device)) {
    switch (primarySensor(device)?.type) {
      case 'TEMPERATURE':
      case 'BATTERY_TEMPERATURE':
      case 'INVERTER_TEMPERATURE':
        return 'thermometer'
      case 'HUMIDITY':
        return 'humidity'
      case 'PV_POWER':
      case 'BATTERY_POWER':
      case 'OUTPUT_POWER':
      case 'ENERGY_TOTAL':
        return 'power'
      default:
        return 'gauge'
    }
  }
  return 'plug'
}

/** Reading types that measure temperature, so their value can be tinted on a warm-cool scale. */
const TEMPERATURE_TYPES = new Set(['TEMPERATURE', 'BATTERY_TEMPERATURE', 'INVERTER_TEMPERATURE'])

/** True when the sensor measures a temperature. */
export function isTemperatureReading(sensor: Sensor): boolean {
  return TEMPERATURE_TYPES.has(sensor.type)
}

/** Color stops from cool to hot, in degrees Celsius, for the temperature tint. */
const TEMPERATURE_STOPS: { t: number; rgb: [number, number, number] }[] = [
  { t: 5, rgb: [74, 163, 255] },
  { t: 18, rgb: [52, 199, 89] },
  { t: 24, rgb: [255, 204, 0] },
  { t: 30, rgb: [255, 59, 48] },
]

/**
 * A cool-to-warm color for a temperature in Celsius: blue when cold, green when comfortable, amber
 * then red as it climbs, so a reading tells you hot or cold before you read the number.
 *
 * @param celsius the temperature to color
 * @returns an {@code rgb(...)} color string
 */
export function temperatureColor(celsius: number): string {
  const first = TEMPERATURE_STOPS[0]
  const last = TEMPERATURE_STOPS[TEMPERATURE_STOPS.length - 1]
  if (celsius <= first.t) {
    return toRgb(first.rgb)
  }
  if (celsius >= last.t) {
    return toRgb(last.rgb)
  }
  for (let i = 0; i < TEMPERATURE_STOPS.length - 1; i += 1) {
    const low = TEMPERATURE_STOPS[i]
    const high = TEMPERATURE_STOPS[i + 1]
    if (celsius <= high.t) {
      const f = (celsius - low.t) / (high.t - low.t)
      return toRgb([
        Math.round(low.rgb[0] + (high.rgb[0] - low.rgb[0]) * f),
        Math.round(low.rgb[1] + (high.rgb[1] - low.rgb[1]) * f),
        Math.round(low.rgb[2] + (high.rgb[2] - low.rgb[2]) * f),
      ])
    }
  }
  return toRgb(last.rgb)
}

function toRgb(rgb: [number, number, number]): string {
  return `rgb(${rgb[0]}, ${rgb[1]}, ${rgb[2]})`
}
