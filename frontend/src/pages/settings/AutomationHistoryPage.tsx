import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Clock, Trash2, CheckCircle2 } from 'lucide-react'
import { api } from '@/api/client'
import { toast } from 'sonner'

function describePayload(json: string): string {
  try {
    const p = JSON.parse(json)
    if (p.on === true)  return 'Turned on'
    if (p.on === false) return 'Turned off'
    if (p.brightness != null) return `Brightness → ${p.brightness}%`
    if (p.setPointTemperature != null) return `Temp → ${p.setPointTemperature}°C`
    return json
  } catch { return json }
}

export function AutomationHistoryPage() {
  const qc = useQueryClient()

  const { data: events = [], isLoading } = useQuery({
    queryKey: ['automationHistory'],
    queryFn:  () => api.automationHistory.list(200),
    refetchInterval: 60_000,
  })

  const clearMutation = useMutation({
    mutationFn: api.automationHistory.clear,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['automationHistory'] })
      toast.success('History cleared')
    },
    onError: () => toast.error('Failed to clear history'),
  })

  return (
    <div className="p-6 max-w-2xl space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">Automation History</h1>
          <p className="text-sm text-[hsl(var(--muted-foreground))] mt-0.5">
            Log of every automation rule that fired.
          </p>
        </div>
        {events.length > 0 && (
          <button
            onClick={() => { if (confirm('Clear all history?')) clearMutation.mutate() }}
            disabled={clearMutation.isPending}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm text-red-400 border border-red-400/30 hover:bg-red-400/10 transition-colors disabled:opacity-40"
          >
            <Trash2 size={13} /> Clear
          </button>
        )}
      </div>

      {isLoading ? (
        <div className="text-sm text-[hsl(var(--muted-foreground))]">Loading…</div>
      ) : events.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-12 text-[hsl(var(--muted-foreground))]">
          <Clock size={36} className="opacity-20" />
          <p className="text-sm">No automations have fired yet.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {events.map(e => (
            <div
              key={e.id}
              className="flex items-start gap-3 rounded-xl border border-[hsl(var(--border))] bg-[hsl(var(--card))] px-4 py-3"
            >
              <CheckCircle2 size={15} className="text-green-500 shrink-0 mt-0.5" />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium">{e.ruleName}</p>
                <p className="text-xs text-[hsl(var(--muted-foreground))] mt-0.5">
                  {e.deviceName ?? `Device #${e.deviceId}`}
                  <span className="mx-1.5 opacity-40">·</span>
                  {describePayload(e.payloadJson)}
                </p>
              </div>
              <time className="text-[11px] text-[hsl(var(--muted-foreground))] shrink-0 tabular-nums">
                {new Date(e.firedAt).toLocaleString()}
              </time>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
