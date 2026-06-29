import type { Device } from './api/devices'

/** One point in a sensor's client-side time series. */
export interface SensorPoint {
  /** Reading time as epoch milliseconds. */
  t: number
  /** Numeric reading value. */
  v: number
}

/** Per-sensor time series, keyed by {@link seriesKey}. */
export type SensorHistory = Record<string, SensorPoint[]>

/** The history-map key for a device's sensor. */
export function seriesKey(deviceId: number, sensorKey: string): string {
  return `${deviceId}:${sensorKey}`
}

/** Most recent points kept per sensor. */
const CAP = 40

/**
 * Returns the history with each sensor's current reading appended, since the hub keeps only the
 * latest value. A reading is appended only when its timestamp moves past the series' last point
 * (so repeated polls of the same value are ignored), and each series is capped at the most recent
 * points. The same reference is returned when nothing changed, so callers can bail out cheaply.
 *
 * @param previous the accumulated history so far
 * @param devices the freshly fetched device list
 * @returns the updated history
 */
export function accumulateHistory(previous: SensorHistory, devices: Device[]): SensorHistory {
  let next = previous
  for (const device of devices) {
    for (const sensor of device.sensors) {
      if (sensor.value === null || sensor.updatedAt === null) {
        continue
      }
      const value = Number(sensor.value)
      if (Number.isNaN(value)) {
        continue
      }
      const key = seriesKey(device.id, sensor.key)
      const series = next[key] ?? []
      const time = Date.parse(sensor.updatedAt)
      if (series.length > 0 && series[series.length - 1].t === time) {
        continue
      }
      if (next === previous) {
        next = { ...previous }
      }
      next[key] = [...series, { t: time, v: value }].slice(-CAP)
    }
  }
  return next
}
