import { useEffect, useRef, useState } from 'react'
import { bisector, extent } from 'd3-array'
import { scaleLinear, scaleTime } from 'd3-scale'
import { area, curveMonotoneX, line } from 'd3-shape'
import { timeFormat } from 'd3-time-format'
import { fetchSensorHistory, type ReadingPoint } from '../api/telemetry'

interface SensorChartProps {
  /** The device's external id, as published onto the telemetry topic. */
  deviceExternalId: string
  /** The sensor's key within its device. */
  sensorKey: string
  /** The reading unit, shown on the value axis and tooltip. */
  unit: string
}

/** One plotted reading: a numeric value at a moment in time. */
interface Point {
  t: Date
  v: number
}

/** How far back the chart reads, in hours. */
const LOOKBACK_HOURS = 24
/** How often the chart re-reads its history, matching the dashboard's device poll. */
const REFRESH_MS = 5000

const HEIGHT = 150
const FALLBACK_WIDTH = 320
const MARGIN = { top: 10, right: 14, bottom: 22, left: 40 }

const formatTime = timeFormat('%H:%M')
const bisectTime = bisector<Point, Date>((point) => point.t).left

/**
 * An always-on, d3-powered line chart of a sensor's persisted history. d3 owns the maths (time and
 * value scales, the line and area generators, axis ticks, nearest-point lookup) while React renders
 * the SVG, so the two never fight over the DOM. The series is read from Elasticsearch via the hub
 * and refreshed on an interval, and hovering reveals the reading under the cursor. Until two
 * readings exist it shows a baseline placeholder, since one point is not a line.
 */
export function SensorChart({ deviceExternalId, sensorKey, unit }: SensorChartProps) {
  const [points, setPoints] = useState<ReadingPoint[]>([])
  const [width, setWidth] = useState(FALLBACK_WIDTH)
  const [hover, setHover] = useState<number | null>(null)
  const wrapperRef = useRef<HTMLDivElement>(null)
  const svgRef = useRef<SVGSVGElement>(null)

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

  // Track the container width so the chart fills the card without distorting its ticks and labels.
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

  const series: Point[] = points
    .map((point) => ({ t: new Date(point.timestamp), v: point.value }))
    .filter((point) => !Number.isNaN(point.t.getTime()) && !Number.isNaN(point.v))

  if (series.length < 2) {
    return (
      <div ref={wrapperRef} className="sensor-chart-wrap">
        <div className="sensor-chart--empty" aria-hidden="true" style={{ height: HEIGHT }} />
      </div>
    )
  }

  const innerLeft = MARGIN.left
  const innerRight = width - MARGIN.right
  const innerTop = MARGIN.top
  const innerBottom = HEIGHT - MARGIN.bottom

  const [tMin, tMax] = extent(series, (point) => point.t) as [Date, Date]
  const vExtent = extent(series, (point) => point.v) as [number, number]
  const x = scaleTime().domain([tMin, tMax]).range([innerLeft, innerRight])
  const y = scaleLinear().domain(vExtent).nice().range([innerBottom, innerTop])

  const linePath = line<Point>()
    .x((point) => x(point.t))
    .y((point) => y(point.v))
    .curve(curveMonotoneX)(series)
  const areaPath = area<Point>()
    .x((point) => x(point.t))
    .y0(innerBottom)
    .y1((point) => y(point.v))
    .curve(curveMonotoneX)(series)

  const xTicks = x.ticks(Math.max(2, Math.min(6, Math.floor(width / 80))))
  const yTicks = y.ticks(4)
  const hovered = hover === null ? null : series[hover]

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
              {`${hovered.v.toFixed(1)} ${unit} · ${formatTime(hovered.t)}`}
            </text>
          </g>
        )}
      </svg>
    </div>
  )
}
