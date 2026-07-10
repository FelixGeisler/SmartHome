import { useEffect, useMemo, useRef, useState } from 'react'
import { bisector, extent } from 'd3-array'
import { scaleLinear, scaleTime } from 'd3-scale'
import { area, curveMonotoneX, line } from 'd3-shape'
import { timeFormat } from 'd3-time-format'
import type { Sensor } from '../api/devices'
import { fetchSensorHistory, type ReadingPoint } from '../api/telemetry'

interface SensorChartProps {
  deviceExternalId: string
  sensor: Sensor
  /** Bumped on every event-stream (re)connect; the history window is refetched to fill the gap. */
  syncToken?: number
}

interface Point {
  t: Date
  v: number
}

const LOOKBACK_HOURS = 24
const LOOKBACK_MS = LOOKBACK_HOURS * 60 * 60 * 1000
const RETRY_MS = 15_000

const HEIGHT = 150
const FALLBACK_WIDTH = 320
const MARGIN = { top: 10, right: 14, bottom: 22, left: 40 }

const formatTime = timeFormat('%H:%M')
const bisectTime = bisector<Point, Date>((point) => point.t).left

/**
 * Always-on d3 line chart of a sensor's history. d3 owns the maths and React renders the SVG so the
 * two never fight over the DOM. Loads a 24-hour window, then appends live readings; a reconnect
 * reloads and merges without clobbering newer live points. Shows a placeholder until two points exist.
 */
export function SensorChart({ deviceExternalId, sensor, syncToken = 0 }: SensorChartProps) {
  const [points, setPoints] = useState<ReadingPoint[]>([])
  const [width, setWidth] = useState(FALLBACK_WIDTH)
  const [hover, setHover] = useState<number | null>(null)
  const wrapperRef = useRef<HTMLDivElement>(null)
  const svgRef = useRef<SVGSVGElement>(null)

  const sensorKey = sensor.key
  const latestValue = sensor.value
  const latestAt = sensor.updatedAt

  // Reload after every reconnect: readings that arrived during a gap were never pushed. Merge so
  // live points that raced the fetch survive; a failed load retries.
  useEffect(() => {
    let cancelled = false
    let retryTimer: ReturnType<typeof setTimeout> | undefined
    function load() {
      fetchSensorHistory(deviceExternalId, sensorKey, LOOKBACK_HOURS)
        .then((loaded) => {
          if (!cancelled) {
            setPoints((current) => mergeSeries(loaded, current))
          }
        })
        .catch(() => {
          if (!cancelled) {
            retryTimer = setTimeout(load, RETRY_MS)
          }
        })
    }
    load()
    return () => {
      cancelled = true
      clearTimeout(retryTimer)
    }
  }, [deviceExternalId, sensorKey, syncToken])

  // Append each pushed reading via the render-time adjust-state-from-props pattern; pruning old
  // points keeps a long-lived dashboard from growing the series without bound.
  const [appendedAt, setAppendedAt] = useState<string | null>(null)
  if (latestAt !== appendedAt) {
    setAppendedAt(latestAt)
    const reading = parseReading(latestValue, latestAt)
    if (reading !== null) {
      setPoints((current) => appendReading(current, reading))
    }
  }

  // Track container width so the chart stays responsive.
  useEffect(() => {
    const element = wrapperRef.current
    if (element === null) {
      return
    }
    const measure = () => setWidth(element.clientWidth || FALLBACK_WIDTH)
    measure()
    if (typeof ResizeObserver === 'undefined') {
      return
    }
    const observer = new ResizeObserver(measure)
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  // Memoized so re-renders from other devices' events (the whole list is replaced on every push)
  // don't re-run the generators.
  const series: Point[] = useMemo(
    () =>
      points
        .map((point) => ({ t: new Date(point.timestamp), v: point.value }))
        .filter((point) => !Number.isNaN(point.t.getTime()) && !Number.isNaN(point.v)),
    [points],
  )

  const geometry = useMemo(() => {
    if (series.length < 2) {
      return null
    }
    const innerLeft = MARGIN.left
    const innerRight = width - MARGIN.right
    const innerTop = MARGIN.top
    const innerBottom = HEIGHT - MARGIN.bottom
    const [tMin, tMax] = extent(series, (point) => point.t) as [Date, Date]
    const vExtent = extent(series, (point) => point.v) as [number, number]
    const x = scaleTime().domain([tMin, tMax]).range([innerLeft, innerRight])
    const y = scaleLinear().domain(vExtent).nice().range([innerBottom, innerTop])
    return {
      innerLeft,
      innerRight,
      innerTop,
      innerBottom,
      x,
      y,
      linePath: line<Point>()
        .x((point) => x(point.t))
        .y((point) => y(point.v))
        .curve(curveMonotoneX)(series),
      areaPath: area<Point>()
        .x((point) => x(point.t))
        .y0(innerBottom)
        .y1((point) => y(point.v))
        .curve(curveMonotoneX)(series),
      xTicks: x.ticks(Math.max(2, Math.min(6, Math.floor(width / 80)))),
      yTicks: y.ticks(4),
    }
  }, [series, width])

  if (geometry === null) {
    return (
      <div ref={wrapperRef} className="sensor-chart-wrap">
        <div className="sensor-chart--empty" aria-hidden="true" style={{ height: HEIGHT }} />
      </div>
    )
  }

  const { innerLeft, innerRight, innerTop, innerBottom, x, y, linePath, areaPath, xTicks, yTicks } =
    geometry
  // The ?? null guards a hover index that outlived a shrink of the series (merge or pruning).
  const hovered = hover === null ? null : (series[hover] ?? null)

  function onMove(event: React.MouseEvent<SVGSVGElement>) {
    const svg = svgRef.current
    if (svg === null) {
      return
    }
    const px = event.clientX - svg.getBoundingClientRect().left
    const time = x.invert(px)
    const i = bisectTime(series, time, 1)
    const left = series[i - 1]
    const right = series[i] ?? left
    const nearer =
      time.getTime() - left.t.getTime() < right.t.getTime() - time.getTime() ? i - 1 : i
    setHover(Math.min(nearer, series.length - 1))
  }

  return (
    <div ref={wrapperRef} className="sensor-chart-wrap">
      <svg
        ref={svgRef}
        className="sensor-chart"
        width={width}
        height={HEIGHT}
        role="img"
        aria-label={`${sensorKey} history, ${series.length} readings`}
        onMouseMove={onMove}
        onMouseLeave={() => setHover(null)}
      >
        {yTicks.map((tick) => (
          <g key={`y-${tick}`} className="sensor-chart__grid">
            <line x1={innerLeft} x2={innerRight} y1={y(tick)} y2={y(tick)} />
            <text x={innerLeft - 6} y={y(tick)} dy="0.32em" textAnchor="end">
              {tick}
            </text>
          </g>
        ))}
        {xTicks.map((tick) => (
          <text
            key={`x-${tick.getTime()}`}
            className="sensor-chart__xtick"
            x={x(tick)}
            y={HEIGHT - 6}
            textAnchor="middle"
          >
            {formatTime(tick)}
          </text>
        ))}
        <path className="sensor-chart__area" d={areaPath ?? undefined} />
        <path className="sensor-chart__line" d={linePath ?? undefined} />
        {hovered !== null && (
          <g className="sensor-chart__focus">
            <line x1={x(hovered.t)} x2={x(hovered.t)} y1={innerTop} y2={innerBottom} />
            <circle cx={x(hovered.t)} cy={y(hovered.v)} r={3.5} />
            <text
              x={Math.min(Math.max(x(hovered.t), innerLeft + 2), innerRight - 2)}
              y={innerTop + 2}
              textAnchor={x(hovered.t) > (innerLeft + innerRight) / 2 ? 'end' : 'start'}
            >
              {`${hovered.v.toFixed(1)} ${sensor.unit} · ${formatTime(hovered.t)}`}
            </text>
          </g>
        )}
      </svg>
    </div>
  )
}

/** Merges a loaded window with the current series, keeping live points that landed between the fetch request and its response. */
function mergeSeries(loaded: ReadingPoint[], current: ReadingPoint[]): ReadingPoint[] {
  const lastLoaded =
    loaded.length > 0 ? Date.parse(loaded[loaded.length - 1].timestamp) : Number.NEGATIVE_INFINITY
  return [...loaded, ...current.filter((point) => Date.parse(point.timestamp) > lastLoaded)]
}

function parseReading(value: string | null, at: string | null): ReadingPoint | null {
  if (at == null || value == null || value.trim() === '') {
    return null
  }
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || Number.isNaN(Date.parse(at))) {
    return null
  }
  return { timestamp: at, value: parsed }
}

/** Appends a strictly newer reading and prunes points that have left the lookback window. */
function appendReading(current: ReadingPoint[], reading: ReadingPoint): ReadingPoint[] {
  const at = Date.parse(reading.timestamp)
  const last = current[current.length - 1]
  if (last !== undefined && Date.parse(last.timestamp) >= at) {
    return current
  }
  const cutoff = at - LOOKBACK_MS
  return [...current.filter((point) => Date.parse(point.timestamp) >= cutoff), reading]
}
