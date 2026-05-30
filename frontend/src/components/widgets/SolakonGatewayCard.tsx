import { useQuery } from '@tanstack/react-query'
import { Sun, WifiOff, Radio } from 'lucide-react'
import { api } from '@/api/client'
import { cn } from '@/lib/utils'

interface Props { config: Record<string, unknown> }

export function SolakonGatewayCard({ config }: Props) {
  const deviceId = Number(config.deviceId)

  const { data: devices = [] } = useQuery({ queryKey: ['devices'], queryFn: api.devices.list })
  const device = devices.find(d => d.id === deviceId)

  const { data: state } = useQuery({
    queryKey: ['deviceState', deviceId],
    queryFn:  () => api.devices.getState(deviceId),
    enabled:  !!deviceId,
    refetchInterval: 30_000,
  })

  if (!device) {
    return <div className="p-4 text-sm text-[hsl(var(--muted-foreground))]">Device #{deviceId} not found</div>
  }

  const reachable        = (state?.reachable         as boolean | undefined) ?? true
  const online           = state?.online              as boolean | undefined
  const gatewayConnected = state?.gateway_connected   as boolean | undefined
  const ratedPowerW      = state?.rated_power_w       as number  | undefined

  // Three-state status: no data yet / online / offline
  const statusLabel = online === undefined ? 'Polling…'
                    : online               ? 'Online'
                    :                        'Offline'
  const statusColor = online === undefined ? 'text-[hsl(var(--muted-foreground))]'
                    : online               ? 'text-green-400'
                    :                        'text-zinc-500'
  const dotColor    = online === undefined ? 'bg-[hsl(var(--muted-foreground))]'
                    : online               ? 'bg-green-400'
                    :                        'bg-zinc-500'

  return (
    <div className="flex flex-col h-full p-4 gap-3 select-none">

      {/* Header */}
      <div className="flex items-center gap-2 min-w-0">
        <Sun
          size={16}
          className={cn('shrink-0', online ? 'text-yellow-400' : 'text-[hsl(var(--muted-foreground))]')}
        />
        <span className="font-medium text-sm truncate flex-1">{device.name}</span>
        {!reachable && <WifiOff size={13} className="shrink-0 text-[hsl(var(--muted-foreground))]" />}
      </div>

      {device.room && (
        <p className="text-xs text-[hsl(var(--muted-foreground))] -mt-1">{device.room}</p>
      )}

      {/* Status — large centred indicator */}
      <div className="flex-1 flex flex-col items-center justify-center gap-2">
        <div className="flex items-center gap-2">
          <span className={cn('w-2.5 h-2.5 rounded-full shrink-0', dotColor)} />
          <span className={cn('text-2xl font-semibold', statusColor)}>{statusLabel}</span>
        </div>
        {ratedPowerW !== undefined && (
          <p className="text-xs text-[hsl(var(--muted-foreground))]">
            {ratedPowerW} W rated capacity
          </p>
        )}
      </div>

      {/* Gateway link status */}
      <div className={cn(
        'flex items-center gap-1.5 text-xs',
        gatewayConnected === true  ? 'text-[hsl(var(--muted-foreground))]' :
        gatewayConnected === false ? 'text-amber-400' :
                                     'text-[hsl(var(--muted-foreground))]'
      )}>
        <Radio size={12} className="shrink-0" />
        <span>
          {gatewayConnected === true  ? 'Gateway RF link up' :
           gatewayConnected === false ? 'Gateway RF link down' :
                                        'Gateway link unknown'}
        </span>
      </div>
    </div>
  )
}
