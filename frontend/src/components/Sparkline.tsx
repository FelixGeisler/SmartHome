import type { SensorPoint } from '../sensorHistory'

interface SparklineProps {
  points: SensorPoint[]
}

const WIDTH = 240
const HEIGHT = 40
const PAD = 4

/**
 * A compact SVG line chart of a sensor's recent readings; stretches to its container width.
 * Until two readings have arrived it shows a baseline placeholder, since one point is not a line.
 */
export function Sparkline({ points }: SparklineProps) {
  if (points.length < 2) {
    return <div className="sparkline sparkline--empty" aria-hidden="true" />
  }
  const times = points.map((point) => point.t)
  const values = points.map((point) => point.v)
  const tMin = Math.min(...times)
  const tMax = Math.max(...times)
  const vMin = Math.min(...values)
  const vMax = Math.max(...values)
  const spanT = tMax - tMin || 1
  const spanV = vMax - vMin || 1
  const xAt = (t: number) => PAD + ((t - tMin) / spanT) * (WIDTH - 2 * PAD)
  const yAt = (v: number) => HEIGHT - PAD - ((v - vMin) / spanV) * (HEIGHT - 2 * PAD)
  const line = points
    .map((point) => `${xAt(point.t).toFixed(1)},${yAt(point.v).toFixed(1)}`)
    .join(' ')
  return (
    <svg
      className="sparkline"
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      preserveAspectRatio="none"
      role="img"
      aria-hidden="true"
    >
      <polyline className="sparkline__line" points={line} />
    </svg>
  )
}
