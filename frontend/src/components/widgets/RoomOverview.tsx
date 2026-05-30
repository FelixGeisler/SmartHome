import { useQuery, useMutation } from '@tanstack/react-query'
import { Lightbulb, Thermometer, Plug, Activity, Sun, Box } from 'lucide-react'
import { api } from '@/api/client'
import type { Device } from '@/api/client'
import { cn } from '@/lib/utils'

interface Props { config: Record<string, unknown> }

// ── Toggle row (Hue lights & Shelly plugs) ────────────────────────────────────

function ToggleRow({ device, isOn, onToggle }: {
  device: Device
  isOn: boolean
  onToggle: () => void
}) {
  const Icon = device.type === 'SHELLY_PLUG' ? Plug : Lightbulb
  const onColor = device.type === 'SHELLY_PLUG' ? 'text-green-400' : 'text-yellow-400'
  const trackOn = device.type === 'SHELLY_PLUG' ? 'bg-green-400' : 'bg-yellow-400'

  return (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-1.5 text-sm">
        <Icon size={13} className={cn(isOn ? onColor : 'text-[hsl(var(--muted-foreground))]')} />
        <span className="truncate max-w-[120px]">{device.name}</span>
      </div>
      <button
        onClick={onToggle}
        className={cn(
          'w-8 h-4 rounded-full transition-colors relative shrink-0',
          isOn ? trackOn : 'bg-[hsl(var(--muted))]'
        )}
      >
        <span className={cn(
          'absolute top-0.5 w-3 h-3 rounded-full bg-white shadow transition-transform',
          isOn ? 'translate-x-4' : 'translate-x-0.5'
        )} />
      </button>
    </div>
  )
}

// ── Section wrapper ───────────────────────────────────────────────────────────

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <p className="text-xs text-[hsl(var(--muted-foreground))] uppercase tracking-wide">{title}</p>
      {children}
    </div>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

export function RoomOverview({ config }: Props) {
  const room = String(config.room ?? '')

  const { data: devices = [], refetch } = useQuery({
    queryKey: ['devices'],
    queryFn: api.devices.list,
  })

  const cmd = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: Record<string, unknown> }) =>
      api.devices.command(id, payload),
    onSuccess: () => refetch(),
  })

  const toggle = (device: Device, isOn: boolean) =>
    cmd.mutate({ id: device.id, payload: { on: !isOn } })

  const roomDevices = devices.filter(d => d.room === room)

  const lights     = roomDevices.filter(d => d.type === 'HUE_LIGHT')
  const plugs      = roomDevices.filter(d => d.type === 'SHELLY_PLUG')
  const radiators  = roomDevices.filter(d => d.type === 'HOMEMATIC_RADIATOR')
  const meters     = roomDevices.filter(d => d.type === 'SOLAKON_METER')
  const inverters  = roomDevices.filter(d => d.type === 'SOLAKON_INVERTER')
  const knownTypes = new Set(['HUE_LIGHT', 'SHELLY_PLUG', 'HOMEMATIC_RADIATOR', 'SOLAKON_METER', 'SOLAKON_INVERTER'])
  const others     = roomDevices.filter(d => !knownTypes.has(d.type))

  return (
    <div className="flex flex-col h-full p-4 gap-3 overflow-y-auto">
      <p className="font-semibold text-sm shrink-0">{room || 'Room'}</p>

      {/* Lights */}
      {lights.length > 0 && (
        <Section title="Lights">
          {lights.map(d => {
            const state = d.lastStateJson ? JSON.parse(d.lastStateJson) : {}
            return (
              <ToggleRow
                key={d.id}
                device={d}
                isOn={Boolean(state.on)}
                onToggle={() => toggle(d, Boolean(state.on))}
              />
            )
          })}
        </Section>
      )}

      {/* Shelly plugs */}
      {plugs.length > 0 && (
        <Section title="Plugs">
          {plugs.map(d => {
            const state = d.lastStateJson ? JSON.parse(d.lastStateJson) : {}
            const isOn = Boolean(state.on)
            return (
              <div key={d.id} className="flex items-center justify-between">
                <div>
                  <ToggleRow device={d} isOn={isOn} onToggle={() => toggle(d, isOn)} />
                </div>
                {state.power != null && (
                  <span className="text-xs text-[hsl(var(--muted-foreground))] ml-2 shrink-0">
                    {Number(state.power).toFixed(1)} W
                  </span>
                )}
              </div>
            )
          })}
        </Section>
      )}

      {/* Radiators */}
      {radiators.length > 0 && (
        <Section title="Radiators">
          {radiators.map(d => {
            const state = d.lastStateJson ? JSON.parse(d.lastStateJson) : {}
            return (
              <div key={d.id} className="flex items-center gap-1.5 text-sm">
                <Thermometer size={13} className="text-blue-400" />
                <span className="truncate max-w-[100px]">{d.name}</span>
                {state.setPointTemperature != null && (
                  <span className="ml-auto text-xs text-[hsl(var(--muted-foreground))]">
                    {state.setPointTemperature}°C
                  </span>
                )}
              </div>
            )
          })}
        </Section>
      )}

      {/* Solakon meters */}
      {meters.length > 0 && (
        <Section title="Energy Meters">
          {meters.map(d => {
            const state = d.lastStateJson ? JSON.parse(d.lastStateJson) : {}
            const power = state.gridPower ?? state.power ?? null
            return (
              <div key={d.id} className="flex items-center gap-1.5 text-sm">
                <Activity size={13} className="text-purple-400" />
                <span className="truncate max-w-[100px]">{d.name}</span>
                {power != null && (
                  <span className="ml-auto text-xs text-[hsl(var(--muted-foreground))]">
                    {Number(power).toFixed(0)} W
                  </span>
                )}
              </div>
            )
          })}
        </Section>
      )}

      {/* Solakon inverters */}
      {inverters.length > 0 && (
        <Section title="Solar Inverters">
          {inverters.map(d => {
            const state = d.lastStateJson ? JSON.parse(d.lastStateJson) : {}
            const power = state.totalPower ?? state.power ?? null
            return (
              <div key={d.id} className="flex items-center gap-1.5 text-sm">
                <Sun size={13} className="text-yellow-400" />
                <span className="truncate max-w-[100px]">{d.name}</span>
                {power != null && (
                  <span className="ml-auto text-xs text-[hsl(var(--muted-foreground))]">
                    {Number(power).toFixed(0)} W
                  </span>
                )}
              </div>
            )
          })}
        </Section>
      )}

      {/* Generic fallback for any unrecognised device type */}
      {others.length > 0 && (
        <Section title="Other">
          {others.map(d => (
            <div key={d.id} className="flex items-center gap-1.5 text-sm">
              <Box size={13} className={cn(d.online ? 'text-[hsl(var(--primary))]' : 'text-[hsl(var(--muted-foreground))]')} />
              <span className="truncate max-w-[120px]">{d.name}</span>
              <span className="ml-auto text-[10px] text-[hsl(var(--muted-foreground))]">
                {d.online ? 'online' : 'offline'}
              </span>
            </div>
          ))}
        </Section>
      )}

      {roomDevices.length === 0 && (
        <p className="text-xs text-[hsl(var(--muted-foreground))]">No devices in "{room}"</p>
      )}
    </div>
  )
}
