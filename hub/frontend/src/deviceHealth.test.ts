import { describe, expect, it } from 'vitest'
import type { Device, Sensor } from './api/devices'
import { formatReadingAge, isOffline, isStaleReading, STALE_AFTER_MS } from './deviceHealth'

const NOW = Date.parse('2026-06-15T12:00:00Z')

function sensor(updatedAt: string | null): Sensor {
  return { key: 'temperature', type: 'TEMPERATURE', unit: '°C', value: '21', updatedAt }
}

function device(reachable?: boolean): Device {
  return {
    id: 1,
    externalId: 'node-1',
    name: 'Climate',
    type: 'SENSOR_NODE',
    capabilities: [],
    adapterType: null,
    state: {},
    sensors: [],
    reachable,
  }
}

function isoAgo(ms: number): string {
  return new Date(NOW - ms).toISOString()
}

describe('isOffline', () => {
  it('is true only when the reachable flag is explicitly false', () => {
    expect(isOffline(device(false))).toBe(true)
    expect(isOffline(device(true))).toBe(false)
    expect(isOffline(device(undefined))).toBe(false)
  })
})

describe('isStaleReading', () => {
  it('treats a sensor with no reading as not stale', () => {
    expect(isStaleReading(sensor(null), NOW)).toBe(false)
  })

  it('treats a reading within the window as fresh', () => {
    expect(isStaleReading(sensor(isoAgo(STALE_AFTER_MS - 60_000)), NOW)).toBe(false)
  })

  it('treats a reading past the window as stale', () => {
    expect(isStaleReading(sensor(isoAgo(STALE_AFTER_MS + 60_000)), NOW)).toBe(true)
  })
})

describe('formatReadingAge', () => {
  it('returns null before the first reading', () => {
    expect(formatReadingAge(sensor(null), NOW)).toBeNull()
  })

  it('collapses a very recent reading to just now', () => {
    expect(formatReadingAge(sensor(isoAgo(30_000)), NOW)).toBe('just now')
  })

  it('reports the age in minutes, then hours, then days as it grows', () => {
    expect(formatReadingAge(sensor(isoAgo(5 * 60_000)), NOW)).toBe('5 min ago')
    expect(formatReadingAge(sensor(isoAgo(3 * 3_600_000)), NOW)).toBe('3 h ago')
    expect(formatReadingAge(sensor(isoAgo(2 * 86_400_000)), NOW)).toBe('2 d ago')
  })
})
