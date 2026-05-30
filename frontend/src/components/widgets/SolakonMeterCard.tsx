import { useQuery } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, Zap, WifiOff } from 'lucide-react'
import { api } from '@/api/client'
import { cn } from '@/lib/utils'

interface Props { config: Record<string, unknown> }

export function SolakonMeterCard({ config }: Props) {
  const deviceId = Number(config.deviceId)

  const { data: devices = [] } = useQuery({ queryKey: ['devices'], queryFn: api.devices.list })
  const device = devices.find(d => d.id === deviceId)

  const { data: state } = useQuery({
    queryKey: ['deviceState', deviceId],
    queryFn: () => api.devices.getState(deviceId),
    enabled: !!deviceId,
    refetchInterval: 30_000,
  })

  if (!device) {
    return <div className="p-4 text-sm text-[hsl(var(--muted-foreground))]">Device #{deviceId} not found</div>
  }

  const reachable  = (state?.reachable as boolean | undefined) ?? true
  const powerW     = state?.power_w     as number | undefined
  const energyKwh  = state?.energy_kwh  as number | undefined
  const exportedKwh = state?.exported_kwh as number | undefined

  const importing = powerW !== undefined && powerW >= 0
  const absW = powerW !== undefined ? Math.abs(powerW) : null

  return (
    <div className="flex flex-col h-full p-4 gap-3 select-none">

      {/* Header */}
      <div className="flex items-center gap-2 min-w-0">
        <Zap size={16} className="shrink-0 text-yellow-400" />
        <span className="font-medium text-sm truncate flex-1">{device.name}</span>
        {!reachable && <WifiOff size={13} className="shrink-0 text-[hsl(var(--muted-foreground))]" />}
      </div>

      {device.room && (
        <p className="text-xs text-[hsl(var(--muted-foreground))] -mt-1">{device.room}</p>
      )}

      {/* Current power — big number */}
      <div className="flex-1 flex flex-col items-center justify-center gap-1">
        {absW !== null ? (
          <>
            <div className={cn(
              'flex items-center gap-2 text-3xl font-semibold tabular-nums',
              importing ? 'text-orange-400' : 'text-green-400'
            )}>
              {importing
                ? <ArrowDown size={24} className="shrink-0" />
                : <ArrowUp   size={24} className="shrink-0" />}
              {absW >= 1000
                ? (absW / 1000).toFixed(2) + ' kW'
                : Math.round(absW) + ' W'}
            </div>
            <p className="text-xs text-[hsl(var(--muted-foreground))]">
              {importing ? 'importing from grid' : 'feeding to grid'}
            </p>
          </>
        ) : (
          <p className="text-sm text-[hsl(var(--muted-foreground))]">No reading yet</p>
        )}
      </div>

      {/* Energy totals */}
      <div className="grid grid-cols-2 gap-2 mt-auto">
        <div className="rounded-lg bg-[hsl(var(--muted))] px-3 py-2 text-center">
          <p className="text-xs text-[hsl(var(--muted-foreground))]">Consumed</p>
          <p className="text-sm font-medium tabular-nums">
            {energyKwh !== undefined ? energyKwh.toFixed(1) + ' kWh' : '—'}
          </p>
        </div>
        <div className="rounded-lg bg-[hsl(var(--muted))] px-3 py-2 text-center">
          <p className="text-xs text-[hsl(var(--muted-foreground))]">Exported</p>
          <p className="text-sm font-medium tabular-nums">
            {exportedKwh !== undefined ? exportedKwh.toFixed(1) + ' kWh' : '—'}
          </p>
        </div>
      </div>
    </div>
  )
}
