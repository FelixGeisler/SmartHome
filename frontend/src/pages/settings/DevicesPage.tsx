import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { RefreshCw, Wifi, WifiOff, Trash2 } from 'lucide-react'
import { api } from '@/api/client'
import type { Device } from '@/api/client'
import { cn } from '@/lib/utils'
import { toast } from 'sonner'

export function DevicesPage() {
  const qc = useQueryClient()
  const [editingRoom, setEditingRoom] = useState<Record<number, string>>({})

  const { data: devices = [], isLoading } = useQuery({
    queryKey: ['devices'],
    queryFn: api.devices.list,
  })

  // Use rooms from DB so DevicesPage dropdown stays in sync with RoomsPage
  const { data: rooms = [] } = useQuery({
    queryKey: ['rooms'],
    queryFn: api.rooms.list,
  })

  const discover = useMutation({
    mutationFn: api.devices.discover,
    onSuccess: (found) => {
      qc.invalidateQueries({ queryKey: ['devices'] })
      const total = Object.values(found).reduce((a, b) => a + b, 0)
      toast.success(`Discovered ${total} device(s)`)
    },
    onError: () => toast.error('Discovery failed'),
  })

  const deleteDevice = useMutation({
    mutationFn: (id: number) => api.devices.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['devices'] })
      toast.success('Device removed')
    },
    onError: () => toast.error('Delete failed'),
  })

  const updateDevice = useMutation({
    mutationFn: ({ id, room }: { id: number; room: string }) =>
      api.devices.update(id, { room }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['devices'] })
      toast.success('Room updated')
    },
    onError: () => toast.error('Update failed'),
  })

  const handleRoomSave = (device: Device) => {
    const room = editingRoom[device.id]
    if (room !== undefined && room !== (device.room ?? '')) {
      updateDevice.mutate({ id: device.id, room })
    }
    setEditingRoom(prev => { const n = { ...prev }; delete n[device.id]; return n })
  }

  const typeLabel: Record<string, string> = {
    HUE_LIGHT:          'Hue Light',
    HOMEMATIC_RADIATOR: 'Radiator',
    MQTT_SENSOR:        'MQTT Sensor',
    SHELLY_PLUG:        'Shelly Plug',
    SOLAKON_METER:      'Energy Meter',
    SOLAKON_INVERTER:   'Solar Inverter',
    SOLAKON_ONE:        'Solakon ONE',
  }

  return (
    <div className="p-6 max-w-4xl">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold">Devices</h1>
          <p className="text-sm text-[hsl(var(--muted-foreground))] mt-0.5">{devices.length} device(s) registered</p>
        </div>
        <button
          onClick={() => discover.mutate()}
          disabled={discover.isPending}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] text-sm hover:opacity-90 transition-opacity disabled:opacity-50"
        >
          <RefreshCw size={14} className={cn(discover.isPending && 'animate-spin')} />
          {discover.isPending ? 'Discovering…' : 'Discover'}
        </button>
      </div>

      {isLoading ? (
        <div className="text-sm text-[hsl(var(--muted-foreground))]">Loading…</div>
      ) : devices.length === 0 ? (
        <div className="text-sm text-[hsl(var(--muted-foreground))]">
          No devices found. Click Discover to scan for all configured integrations.
        </div>
      ) : (
        <div className="rounded-lg border border-[hsl(var(--border))] overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-[hsl(var(--muted))]">
              <tr>
                <th className="text-left px-4 py-2.5 font-medium text-[hsl(var(--muted-foreground))]">Name</th>
                <th className="text-left px-4 py-2.5 font-medium text-[hsl(var(--muted-foreground))]">Type</th>
                <th className="text-left px-4 py-2.5 font-medium text-[hsl(var(--muted-foreground))]">Room</th>
                <th className="text-left px-4 py-2.5 font-medium text-[hsl(var(--muted-foreground))]">Status</th>
                <th className="px-4 py-2.5" />
              </tr>
            </thead>
            <tbody className="divide-y divide-[hsl(var(--border))]">
              {devices.map(d => (
                <tr key={d.id} className="hover:bg-[hsl(var(--accent))] transition-colors">
                  <td className="px-4 py-3 font-medium">{d.name}</td>
                  <td className="px-4 py-3 text-[hsl(var(--muted-foreground))]">
                    {typeLabel[d.type] ?? d.type}
                  </td>
                  <td className="px-4 py-3">
                    {editingRoom[d.id] !== undefined ? (
                      <div className="flex gap-2 items-center">
                        <select
                          value={editingRoom[d.id]}
                          onChange={e => setEditingRoom(prev => ({ ...prev, [d.id]: e.target.value }))}
                          className="text-sm border border-[hsl(var(--border))] rounded px-2 py-1 bg-[hsl(var(--background))]"
                        >
                          <option value="">— unassigned —</option>
                          {rooms.map(r => (
                            <option key={r.id} value={r.name}>{r.icon} {r.name}</option>
                          ))}
                        </select>
                        <button
                          onClick={() => handleRoomSave(d)}
                          className="text-xs px-2 py-1 rounded bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))]"
                        >
                          Save
                        </button>
                        <button
                          onClick={() => setEditingRoom(prev => { const n = { ...prev }; delete n[d.id]; return n })}
                          className="text-xs px-2 py-1 rounded bg-[hsl(var(--muted))]"
                        >
                          Cancel
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={() => setEditingRoom(prev => ({ ...prev, [d.id]: d.room ?? '' }))}
                        className="text-[hsl(var(--muted-foreground))] hover:text-[hsl(var(--foreground))] transition-colors"
                      >
                        {d.room
                          ? <span>{rooms.find(r => r.name === d.room)?.icon ?? ''} {d.room}</span>
                          : <span className="italic text-xs">unassigned</span>}
                      </button>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <span className={cn('flex items-center gap-1.5 text-xs', d.online ? 'text-green-500' : 'text-[hsl(var(--muted-foreground))]')}>
                      {d.online ? <Wifi size={12} /> : <WifiOff size={12} />}
                      {d.online ? 'Online' : 'Offline'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => deleteDevice.mutate(d.id)}
                      disabled={deleteDevice.isPending}
                      className="p-1.5 rounded-md text-[hsl(var(--muted-foreground))] hover:text-red-400 hover:bg-[hsl(var(--accent))] transition-colors"
                      title="Delete device"
                    >
                      <Trash2 size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
