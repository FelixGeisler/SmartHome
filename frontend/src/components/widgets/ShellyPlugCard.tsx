import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plug, Loader2, WifiOff, Zap } from 'lucide-react'
import { useState, useEffect } from 'react'
import { api } from '@/api/client'
import { cn } from '@/lib/utils'

interface Props { config: Record<string, unknown> }

export function ShellyPlugCard({ config }: Props) {
  const deviceId = Number(config.deviceId)
  const qc = useQueryClient()

  const { data: devices = [] } = useQuery({ queryKey: ['devices'], queryFn: api.devices.list })
  const device = devices.find(d => d.id === deviceId)

  const { data: state } = useQuery({
    queryKey: ['deviceState', deviceId],
    queryFn: () => api.devices.getState(deviceId),
    enabled: !!deviceId,
    refetchInterval: 15_000,
  })

  const [pendingOn, setPendingOn] = useState<boolean | null>(null)

  const cmd = useMutation({
    mutationFn: (payload: Record<string, unknown>) => api.devices.command(deviceId, payload),
    onSuccess: (_, variables) => {
      qc.setQueryData(
        ['deviceState', deviceId],
        (old: Record<string, unknown> | undefined) => old ? { ...old, ...variables } : old,
      )
      setTimeout(() => qc.invalidateQueries({ queryKey: ['deviceState', deviceId] }), 3_000)
    },
    onError: () => setPendingOn(null),
  })

  const busy = cmd.isPending

  useEffect(() => {
    if (!busy) setPendingOn(null)
  }, [busy])

  const isOn      = pendingOn ?? (state?.on as boolean | undefined)
  const power     = state?.power as number | undefined
  const reachable = (state?.reachable as boolean | undefined) ?? true

  function sendToggle() {
    const n = !isOn
    setPendingOn(n)
    cmd.mutate({ on: n })
  }

  if (!device) {
    return <div className="p-4 text-sm text-[hsl(var(--muted-foreground))]">Device #{deviceId} not found</div>
  }

  return (
    <div className="relative flex flex-col h-full p-4 gap-3 select-none">

      {busy && (
        <div className="absolute inset-0 z-10 rounded-xl bg-[hsl(var(--card))]/70 flex items-center justify-center">
          <Loader2 size={20} className="animate-spin text-[hsl(var(--muted-foreground))]" />
        </div>
      )}

      {/* Header */}
      <div className="flex items-center gap-2 min-w-0">
        <Plug
          size={16}
          className={cn('shrink-0', isOn ? 'text-green-400' : 'text-[hsl(var(--muted-foreground))]')}
        />
        <span className="font-medium text-sm truncate flex-1">{device.name}</span>
        {!reachable && <WifiOff size={13} className="shrink-0 text-[hsl(var(--muted-foreground))]" />}
        <button
          onClick={sendToggle}
          aria-label={isOn ? 'Turn off' : 'Turn on'}
          className={cn(
            'shrink-0 w-10 h-5 rounded-full transition-colors duration-200 flex items-center px-0.5',
            isOn ? 'bg-green-400 justify-end' : 'bg-zinc-600 justify-start'
          )}
        >
          <span className="block w-4 h-4 rounded-full bg-white shadow-sm" />
        </button>
      </div>

      {device.room && (
        <p className="text-xs text-[hsl(var(--muted-foreground))] -mt-1">{device.room}</p>
      )}

      {/* Power reading */}
      {power !== undefined && (
        <div className="mt-auto flex items-center gap-1.5">
          <Zap size={14} className={cn(isOn && power > 0 ? 'text-green-400' : 'text-[hsl(var(--muted-foreground))]')} />
          <span className={cn(
            'text-sm font-medium tabular-nums',
            isOn && power > 0 ? 'text-green-400' : 'text-[hsl(var(--muted-foreground))]'
          )}>
            {power.toFixed(1)} W
          </span>
        </div>
      )}
    </div>
  )
}
