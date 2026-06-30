import { useEffect, useState } from 'react'
import { fetchSensorHistory, type ReadingPoint } from '../api/telemetry'

interface SensorChartProps {
  /** The device's external id, as published onto the telemetry topic. */
  deviceExternalId: string
  /** The sensor's key within its device. */
  sensorKey: string
}

/** How far back the chart reads, in hours. */
const LOOKBACK_HOURS = 24
/** How often the chart re-reads its history, matching the dashboard's device poll. */
const REFRESH_MS = 5000

const WIDTH = 240
const HEIGHT = 40
const PAD = 4

/**
 * An always-on line chart of a sensor's persisted history. It reads the readings the streaming
 * pipeline has stored in Elasticsearch (via the hub) and refreshes on an interval, so every sensor
 * value carries its own diagram that survives reloads and tracks new readings within a poll. Until
 * two readings exist it shows a baseline placeholder, since one point is not a line.
 */
export function SensorChart({ deviceExternalId, sensorKey }: SensorChartProps) {
  const [points, setPoints] = useState<ReadingPoint[]>([])

  useEffect(() => {
    let cancelled = false
    function load() {
      fetchSensorHistory(deviceExternalId, sensorKey, LOOKBACK_HOURS)
        .then((loaded) => {
          if (!cancelled) {
            setPoints(loaded)
          }
        })
        .catch(() => {
          // Keep the last good chart on a transient failure; the next tick retries.
        })
    }
    load()
    const id = setInterval(load, REFRESH_MS)
    return () => {
      cancelled = true
      clearInterval(id)
    }
  }, [deviceExternalId, sensorKey])

  const series = points
    .map((point) => ({ t: Date.parse(point.timestamp), v: point.value }))
    .filter((point) => !Number.isNaN(point.t) && !Number.isNaN(point.v))
  if (series.length < 2) {
    return <div className="sensor-chart sensor-chart--empty" aria-hidden="true" />
  }
  const times = series.map((point) => point.t)
  const values = series.map((point) => point.v)
  const tMin = Math.min(...times)
  const tMax = Math.max(...times)
  const vMin = Math.min(...values)
  const vMax = Math.max(...values)
  const spanT = tMax - tMin || 1
  const spanV = vMax - vMin || 1
  const xAt = (t: number) => PAD + ((t - tMin) / spanT) * (WIDTH - 2 * PAD)
  const yAt = (v: number) => HEIGHT - PAD - ((v - vMin) / spanV) * (HEIGHT - 2 * PAD)
  const line = series
    .map((point) => `${xAt(point.t).toFixed(1)},${yAt(point.v).toFixed(1)}`)
    .join(' ')
  return (
    <svg
      className="sensor-chart"
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      preserveAspectRatio="none"
      role="img"
      aria-label={`${sensorKey} history, ${series.length} readings`}
    >
      <polyline className="sensor-chart__line" points={line} />
    </svg>
  )
}
