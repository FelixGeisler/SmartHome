import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { Thermometer, ChevronUp, ChevronDown } from 'lucide-react'
import { api } from '@/api/client'

interface Props { config: Record<string, unknown> }

export function ThermostatCard({ config }: Props) {
  const deviceId = Number(config.deviceId)
  const { data: devices = [] } = useQuery({ queryKey: ['devices'], queryFn: api.devices.list })
  const device = devices.find(d => d.id === deviceId)

  const { data: state, refetch } = useQuery({
    queryKey: ['deviceState', deviceId],
    queryFn: () => api.devices.getState(deviceId),
    enabled: !!deviceId,
    refetchInterval: 30_000,
  })

  const setPoint = (state?.setPointTemperature as number | undefined) ?? 20
  const actual = (state?.actualTemperature as number | undefined)
  const [draft, setDraft] = useState<number | null>(null)

  const cmd = useMutation({
    mutationFn: (temp: number) => api.devices.command(deviceId, { setPointTemperature: temp }),
    onSuccess: () => { setDraft(null); refetch() },
  })

  const displayed = draft ?? setPoint
  const step = (delta: number) => setDraft(Math.min(30, Math.max(5, (draft ?? setPoint) + delta)))

  if (!device) return <div className="p-4 text-sm text-[hsl(var(--muted-foreground))]">Device #{deviceId} not found</div>

  return (
    <div className="flex flex-col h-full p-4 gap-2">
      <div className="flex items-center gap-2">
        <Thermometer size={16} className="text-blue-400" />
        <span className="font-medium text-sm truncate">{device.name}</span>
      </div>
      {device.room && <p className="text-xs text-[hsl(var(--muted-foreground))]">{device.room}</p>}

      <div className="flex-1 flex flex-col items-center justify-center gap-1">
        {actual !== undefined && (
          <p className="text-xs text-[hsl(var(--muted-foreground))]">Actual: {actual.toFixed(1)}°C</p>
        )}
        <div className="flex items-center gap-3">
          <button onClick={() => step(-0.5)} className="p-1 rounded-md hover:bg-[hsl(var(--accent))] transition-colors">
            <ChevronDown size={18} />
          </button>
          <span className="text-3xl font-bold tabular-nums">{displayed.toFixed(1)}°</span>
          <button onClick={() => step(0.5)} className="p-1 rounded-md hover:bg-[hsl(var(--accent))] transition-colors">
            <ChevronUp size={18} />
          </button>
        </div>
        {draft !== null && (
          <button
            onClick={() => cmd.mutate(draft)}
            disabled={cmd.isPending}
            className="mt-1 px-4 py-1 rounded-lg text-xs bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] hover:opacity-90 transition-opacity"
          >
            {cmd.isPending ? 'Setting…' : 'Apply'}
          </button>
        )}
      </div>
    </div>
  )
}
