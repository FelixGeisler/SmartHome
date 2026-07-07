import type { Device, Sensor } from './api/devices'

/**
 * How old a reading may get before the UI mutes it as stale. It matches the hub's own freshness
 * window for reporting devices. The per-reading mute is independent of the device-level offline
 * badge, so a device the hub has never heard from can show the badge while its yet-empty readings
 * are not muted.
 */
export const STALE_AFTER_MS = 10 * 60 * 1000

/** True when the hub has marked the device unreachable; an absent flag counts as reachable. */
export function isOffline(device: Device): boolean {
  return device.reachable === false
}

/** True when a sensor has a reading that is older than the staleness window. */
export function isStaleReading(sensor: Sensor, now: number): boolean {
  if (sensor.updatedAt === null) {
    return false
  }
  return now - Date.parse(sensor.updatedAt) >= STALE_AFTER_MS
}

/**
 * A compact "updated N ago" label for a reading, or null before the first reading arrives. The
 * granularity coarsens with age: seconds collapse to "just now", then minutes, hours, and days.
 *
 * @param sensor the sensor whose latest reading is being labeled
 * @param now the current time in epoch milliseconds
 * @returns the relative-age label, or null when the sensor has no reading yet
 */
export function formatReadingAge(sensor: Sensor, now: number): string | null {
  if (sensor.updatedAt === null) {
    return null
  }
  const ageMs = Math.max(0, now - Date.parse(sensor.updatedAt))
  const minutes = Math.floor(ageMs / 60_000)
  if (minutes < 1) {
    return 'just now'
  }
  if (minutes < 60) {
    return `${minutes} min ago`
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return `${hours} h ago`
  }
  return `${Math.floor(hours / 24)} d ago`
}
