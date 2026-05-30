import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts'
import { api } from '@/api/client'
import { useLiveStore } from '@/store/liveStore'

interface Props { config: Record<string, unknown> }

const RANGES = [
  { label: '1h',  hours: 1 },
  { label: '6h',  hours: 6 },
  { label: '24h', hours: 24 },
  { label: '7d',  hours: 168 },
]

function fmtTime(iso: string, hours: number): string {
  const d = new Date(iso)
  if (hours <= 24) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  return d.toLocaleDateString([], { weekday: 'short', hour: '2-digit', minute: '2-digit' })
}

export function SensorChartWidget({ config }: Props) {
  const room   = String(config.room   ?? '')
  const metric = String(config.metric ?? 'temperature')
  // hours is the default from widget config; user can override with the range buttons
  const [hours, setHours] = useState(Number(config.hours ?? 24))

  // Latest value from Zustand (updated by WebSocket)
  const live = useLiveStore(s => s.sensorReadings[`${room}/${metric}`])

  const since = new Date(Date.now() - hours * 3_600_000).toISOString()

  const { data: history = [] } = useQuery({
    queryKey: ['sensorHistory', room, metric, hours],
    queryFn:  () => api.sensors.history(room, metric, since),
    enabled:  !!room && !!metric,
    refetchInterval: 5 * 60_000,  // full refresh every 5 min; WS invalidates on new readings
  })

  const UNITS:  Record<string, string> = { temperature: '°C', humidity: '%', co2: ' ppm', pressure: ' hPa', gas: ' Ω' }
  const COLORS: Record<string, string> = { temperature: '#f97316', humidity: '#3b82f6', co2: '#10b981', pressure: '#a855f7', gas: '#eab308' }
  const unit   = UNITS[metric]  ?? ''
  const color  = COLORS[metric] ?? '#8b5cf6'
  const digits = metric === 'co2' || metric === 'gas' ? 0 : 1
  const latest = live?.value ?? history[history.length - 1]?.value

  const points = history.map(r => ({
    t: fmtTime(r.recordedAt, hours),
    v: r.value,
  }))

  return (
    <div className="flex flex-col h-full p-4 gap-2">
      {/* Header */}
      <div className="flex items-baseline justify-between">
        <div>
          <p className="font-medium text-sm">{room}</p>
          <p className="text-xs text-[hsl(var(--muted-foreground))] capitalize">{metric}</p>
        </div>
        {latest != null && (
          <p className="text-2xl font-bold tabular-nums" style={{ color }}>
            {latest.toFixed(digits)}<span className="text-sm font-normal ml-0.5">{unit}</span>
          </p>
        )}
      </div>

      {/* Chart */}
      <div className="flex-1 min-h-0">
        {points.length === 0 ? (
          <div className="h-full flex items-center justify-center text-xs text-[hsl(var(--muted-foreground))]">
            No data for this period
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={points} margin={{ top: 4, right: 8, bottom: 0, left: 8 }}>
              <XAxis
                dataKey="t"
                tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
                tickLine={false}
                axisLine={false}
                interval="preserveStartEnd"
              />
              <YAxis
                width={48}
                tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
                tickLine={false}
                axisLine={false}
                domain={['auto', 'auto']}
                tickFormatter={v => `${Number(v).toFixed(digits)}${unit}`}
              />
              <Tooltip
                contentStyle={{
                  background: 'hsl(var(--card))',
                  border: '1px solid hsl(var(--border))',
                  borderRadius: '0.5rem',
                  fontSize: '0.75rem',
                }}
                formatter={(v: unknown) => {
                  const n = typeof v === 'number' ? v : Number(v)
                  return [`${n.toFixed(digits)}${unit}`, metric] as [string, string]
                }}
                labelStyle={{ color: 'hsl(var(--muted-foreground))' }}
              />
              <Line
                type="monotone"
                dataKey="v"
                stroke={color}
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 4 }}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>

      {/* Time range selector */}
      <div className="flex gap-1 justify-end">
        {RANGES.map(r => (
          <button
            key={r.label}
            type="button"
            onClick={() => setHours(r.hours)}
            className={`text-[10px] px-1.5 py-0.5 rounded font-medium transition-colors ${
              r.hours === hours
                ? 'bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))]'
                : 'text-[hsl(var(--muted-foreground))] hover:text-[hsl(var(--foreground))]'
            }`}
          >
            {r.label}
          </button>
        ))}
      </div>
    </div>
  )
}
