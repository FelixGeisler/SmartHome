import { useState } from 'react'
import { fetchSensorHistory, type ReadingPoint } from '../api/telemetry'

interface SensorHistoryChartProps {
  /** The device's external id, as published onto the telemetry topic. */
  deviceExternalId: string
  /** The sensor's key within its device. */
  sensorKey: string
  /** The reading unit, shown in the chart caption. */
  unit: string
}

type Status = 'idle' | 'loading' | 'ready' | 'error'

/** How far back the history panel reads, in hours. */
const LOOKBACK_HOURS = 24

/**
 * A per-sensor history panel. Unlike the live sparkline, which holds only the points polled this
 * session, this fetches the readings persisted in the streaming store, so the series survives
 * reloads and reaches back hours. It loads lazily on first open to keep the dashboard cheap.
 */
export function SensorHistoryChart({ deviceExternalId, sensorKey, unit }: SensorHistoryChartProps) {
  const [open, setOpen] = useState(false)
  const [status, setStatus] = useState<Status>('idle')
  const [points, setPoints] = useState<ReadingPoint[]>([])
  const [error, setError] = useState<string | null>(null)

  async function toggle() {
    if (open) {
      setOpen(false)
      return
    }
    setOpen(true)
    // Load once; a reopened panel keeps the readings it already fetched.
    if (status === 'ready' || status === 'loading') {
      return
    }
    setStatus('loading')
    setError(null)
    try {
      setPoints(await fetchSensorHistory(deviceExternalId, sensorKey, LOOKBACK_HOURS))
      setStatus('ready')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not load history')
      setStatus('error')
    }
  }

  return (
    <div className="sensor-history">
      <button
        type="button"
        className="sensor-history__toggle"
        aria-expanded={open}
        onClick={() => void toggle()}
      >
        {open ? 'Hide history' : `History · ${LOOKBACK_HOURS} h`}
      </button>
      {open && (
        <div className="sensor-history__panel">
          {status === 'loading' && <p className="sensor-history__note">Loading…</p>}
          {status === 'error' && (
            <p className="sensor-history__note sensor-history__note--error">{error}</p>
          )}
          {status === 'ready' && <HistoryChart points={points} unit={unit} />}
        </div>
      )}
    </div>
  )
}

const WIDTH = 320
const HEIGHT = 120
const PAD = 6

/** A line chart of fetched readings, with a caption stating the count and value range. */
function HistoryChart({ points, unit }: { points: ReadingPoint[]; unit: string }) {
  const series = points
    .map((point) => ({ t: Date.parse(point.timestamp), v: point.value }))
    .filter((point) => !Number.isNaN(point.t) && !Number.isNaN(point.v))
  if (series.length < 2) {
    return <p className="sensor-history__note">Not enough history yet.</p>
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
    <figure className="history-chart">
      <svg
        className="history-chart__svg"
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        preserveAspectRatio="none"
        role="img"
        aria-label={`History of the last ${series.length} readings`}
      >
        <polyline className="history-chart__line" points={line} />
      </svg>
      <figcaption className="history-chart__caption">
        {series.length} readings &middot; {vMin.toFixed(1)}–{vMax.toFixed(1)} {unit}
      </figcaption>
    </figure>
  )
}
