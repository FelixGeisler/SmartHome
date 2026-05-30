/**
 * Scenes management page.
 * A scene is a named button that fires multiple device commands at once.
 */

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2, Pencil, Play, X, Layers } from 'lucide-react'
import { api } from '@/api/client'
import type { Device, Scene, SceneAction } from '@/api/client'
import { toast } from 'sonner'
import { cn } from '@/lib/utils'

// ── Action helpers ────────────────────────────────────────────────────────────

type ActionType = 'turn-on' | 'turn-off' | 'set-brightness' | 'set-temperature'

const ACTIONABLE_TYPES: Device['type'][] = [
  'HUE_LIGHT', 'SHELLY_PLUG', 'HOMEMATIC_RADIATOR',
]

function actionLabel(t: ActionType) {
  switch (t) {
    case 'turn-on':         return 'Turn on'
    case 'turn-off':        return 'Turn off'
    case 'set-brightness':  return 'Set brightness'
    case 'set-temperature': return 'Set temperature'
  }
}

function actionsForDevice(device: Device): ActionType[] {
  switch (device.type) {
    case 'HUE_LIGHT':          return ['turn-on', 'turn-off', 'set-brightness']
    case 'SHELLY_PLUG':        return ['turn-on', 'turn-off']
    case 'HOMEMATIC_RADIATOR': return ['set-temperature']
    default:                   return ['turn-on', 'turn-off']
  }
}

function buildPayload(type: ActionType, value: number): string {
  switch (type) {
    case 'turn-on':         return '{"on":true}'
    case 'turn-off':        return '{"on":false}'
    case 'set-brightness':  return JSON.stringify({ brightness: value })
    case 'set-temperature': return JSON.stringify({ setPointTemperature: value })
  }
}

function describeAction(action: SceneAction, devices: Device[]): string {
  const device = devices.find(d => d.id === action.deviceId)
  const name   = device?.name ?? `Device #${action.deviceId}`
  try {
    const p = JSON.parse(action.payloadJson)
    if (p.on === true)                   return `${name}: Turn on`
    if (p.on === false)                  return `${name}: Turn off`
    if (p.brightness != null)            return `${name}: Brightness → ${p.brightness}%`
    if (p.setPointTemperature != null)   return `${name}: Set → ${p.setPointTemperature}°C`
    return `${name}: ${action.payloadJson}`
  } catch { return `${name}: ${action.payloadJson}` }
}

// ── Editable action row ───────────────────────────────────────────────────────

interface EditableAction {
  deviceId: number
  actionType: ActionType
  value: number
}

function ActionRow({
  row, devices, onChange, onRemove,
}: {
  row: EditableAction
  devices: Device[]
  onChange: (r: EditableAction) => void
  onRemove: () => void
}) {
  const device     = devices.find(d => d.id === row.deviceId)
  const validTypes = device ? actionsForDevice(device) : (['turn-on', 'turn-off'] as ActionType[])
  const needsValue = row.actionType === 'set-brightness' || row.actionType === 'set-temperature'

  const selectCls = 'px-2 py-1.5 rounded-lg border border-[hsl(var(--border))] bg-[hsl(var(--background))] text-xs'

  return (
    <div className="flex items-center gap-2 rounded-lg bg-[hsl(var(--accent)/0.4)] px-3 py-2">
      {/* Device picker */}
      <select
        className={cn(selectCls, 'flex-1 min-w-0')}
        value={row.deviceId || ''}
        onChange={e => {
          const newDeviceId = Number(e.target.value)
          const newDevice   = devices.find(d => d.id === newDeviceId)
          const newTypes    = newDevice ? actionsForDevice(newDevice) : ['turn-on' as ActionType]
          const newType     = newTypes[0]
          onChange({ ...row, deviceId: newDeviceId, actionType: newType })
        }}
      >
        <option value="">— device —</option>
        {devices.filter(d => ACTIONABLE_TYPES.includes(d.type)).map(d => (
          <option key={d.id} value={d.id}>{d.name}{d.room ? ` (${d.room})` : ''}</option>
        ))}
      </select>

      {/* Action type picker */}
      <select
        className={selectCls}
        value={row.actionType}
        onChange={e => onChange({ ...row, actionType: e.target.value as ActionType })}
      >
        {validTypes.map(t => <option key={t} value={t}>{actionLabel(t)}</option>)}
      </select>

      {/* Value input */}
      {needsValue && (
        <input
          type="number"
          className={cn(selectCls, 'w-20')}
          value={row.value}
          min={row.actionType === 'set-brightness' ? 1 : 5}
          max={row.actionType === 'set-brightness' ? 100 : 30}
          step={row.actionType === 'set-brightness' ? 5 : 0.5}
          onChange={e => onChange({ ...row, value: Number(e.target.value) })}
        />
      )}

      <button
        type="button"
        onClick={onRemove}
        className="p-1 rounded hover:text-red-400 text-[hsl(var(--muted-foreground))] transition-colors shrink-0"
      >
        <X size={13} />
      </button>
    </div>
  )
}

// ── Scene editor modal ────────────────────────────────────────────────────────

function SceneModal({
  scene, devices, onClose,
}: {
  scene: Scene | null   // null = create new
  devices: Device[]
  onClose: () => void
}) {
  const qc = useQueryClient()
  const isNew = scene === null

  // Parse existing actions into editable form
  const initialActions: EditableAction[] = isNew ? [] : scene.actions.map(a => {
    let actionType: ActionType = 'turn-on'
    let value = 100
    try {
      const p = JSON.parse(a.payloadJson)
      if (p.on === false)                { actionType = 'turn-off'; }
      else if (p.brightness != null)     { actionType = 'set-brightness'; value = p.brightness }
      else if (p.setPointTemperature != null) { actionType = 'set-temperature'; value = p.setPointTemperature }
    } catch { /* keep defaults */ }
    return { deviceId: a.deviceId, actionType, value }
  })

  const [name,    setName]    = useState(scene?.name ?? '')
  const [icon,    setIcon]    = useState(scene?.icon ?? '🎬')
  const [actions, setActions] = useState<EditableAction[]>(initialActions)

  function toSceneActions(): SceneAction[] {
    return actions
      .filter(a => a.deviceId > 0)
      .map(a => ({
        deviceId:    a.deviceId,
        payloadJson: buildPayload(a.actionType, a.value),
      }))
  }

  const save = useMutation({
    mutationFn: () => {
      const data = { name, icon, actions: toSceneActions() }
      return isNew
        ? api.scenes.create(data)
        : api.scenes.update(scene!.id, data)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['scenes'] })
      toast.success(isNew ? 'Scene created' : 'Scene updated')
      onClose()
    },
    onError: () => toast.error('Failed to save scene'),
  })

  function addAction() {
    const first = devices.find(d => ACTIONABLE_TYPES.includes(d.type))
    setActions(prev => [...prev, {
      deviceId:   first?.id ?? 0,
      actionType: first ? actionsForDevice(first)[0] : 'turn-on',
      value:      100,
    }])
  }

  const inputCls = 'w-full px-3 py-2 rounded-lg border border-[hsl(var(--border))] bg-[hsl(var(--background))] text-sm'

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-[hsl(var(--card))] rounded-xl shadow-xl w-full max-w-lg mx-4 flex flex-col max-h-[90dvh]">
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-[hsl(var(--border))] shrink-0">
          <h2 className="font-semibold text-sm">{isNew ? 'New Scene' : `Edit "${scene!.name}"`}</h2>
          <button onClick={onClose} className="p-1.5 rounded-md hover:bg-[hsl(var(--accent))] transition-colors">
            <X size={15} />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-5 space-y-5">
          <div className="flex gap-3">
            {/* Icon */}
            <label className="block space-y-1.5 w-24 shrink-0">
              <span className="text-xs font-medium text-[hsl(var(--muted-foreground))]">Icon</span>
              <input
                className={cn(inputCls, 'text-center text-xl')}
                value={icon}
                maxLength={4}
                onChange={e => setIcon(e.target.value)}
              />
            </label>
            {/* Name */}
            <label className="block space-y-1.5 flex-1">
              <span className="text-xs font-medium text-[hsl(var(--muted-foreground))]">Name</span>
              <input
                className={inputCls}
                placeholder="Movie Night"
                value={name}
                onChange={e => setName(e.target.value)}
              />
            </label>
          </div>

          {/* Actions */}
          <div className="space-y-2">
            <p className="text-xs font-medium text-[hsl(var(--muted-foreground))]">Actions</p>
            {actions.length === 0 && (
              <p className="text-xs text-[hsl(var(--muted-foreground))] italic">No actions yet.</p>
            )}
            {actions.map((row, i) => (
              <ActionRow
                key={i}
                row={row}
                devices={devices}
                onChange={updated => setActions(prev => prev.map((r, j) => j === i ? updated : r))}
                onRemove={() => setActions(prev => prev.filter((_, j) => j !== i))}
              />
            ))}
            <button
              type="button"
              onClick={addAction}
              className="flex items-center gap-1.5 text-xs text-[hsl(var(--muted-foreground))] hover:text-[hsl(var(--foreground))] transition-colors"
            >
              <Plus size={13} /> Add action
            </button>
          </div>
        </div>

        {/* Footer */}
        <div className="flex justify-end gap-2 p-4 border-t border-[hsl(var(--border))] shrink-0">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-lg text-sm hover:bg-[hsl(var(--accent))] transition-colors"
          >
            Cancel
          </button>
          <button
            disabled={!name.trim() || save.isPending}
            onClick={() => save.mutate()}
            className="px-4 py-2 rounded-lg text-sm bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] hover:opacity-90 disabled:opacity-40 transition-opacity"
          >
            {save.isPending ? 'Saving…' : isNew ? 'Create Scene' : 'Save'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export function ScenesPage() {
  const qc = useQueryClient()
  const [editing, setEditing] = useState<Scene | null | 'new'>()

  const { data: scenes  = [] } = useQuery({ queryKey: ['scenes'],  queryFn: api.scenes.list  })
  const { data: devices = [] } = useQuery({ queryKey: ['devices'], queryFn: api.devices.list })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.scenes.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['scenes'] })
      toast.success('Scene deleted')
    },
    onError: () => toast.error('Failed to delete scene'),
  })

  const activateMutation = useMutation({
    mutationFn: (id: number) => api.scenes.activate(id),
    onSuccess: () => toast.success('Scene activated'),
    onError: () => toast.error('Failed to activate scene'),
  })

  return (
    <div className="p-6 max-w-2xl space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">Scenes</h1>
          <p className="text-sm text-[hsl(var(--muted-foreground))] mt-0.5">
            One-tap shortcuts that send commands to multiple devices at once.
          </p>
        </div>
        <button
          onClick={() => setEditing('new')}
          className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] hover:opacity-90 transition-opacity"
        >
          <Plus size={14} /> New Scene
        </button>
      </div>

      {/* List */}
      {scenes.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-16 text-[hsl(var(--muted-foreground))]">
          <Layers size={40} className="opacity-20" />
          <p className="text-sm">No scenes yet. Create one to get started.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {scenes.map(scene => (
            <div
              key={scene.id}
              className="flex items-center gap-4 rounded-xl border border-[hsl(var(--border))] bg-[hsl(var(--card))] px-4 py-3"
            >
              <span className="text-2xl leading-none shrink-0">{scene.icon ?? '🎬'}</span>

              <div className="flex-1 min-w-0">
                <p className="font-medium text-sm">{scene.name}</p>
                {scene.actions.length === 0 ? (
                  <p className="text-xs text-[hsl(var(--muted-foreground))] italic">No actions</p>
                ) : (
                  <ul className="mt-0.5 space-y-0.5">
                    {scene.actions.map((a, i) => (
                      <li key={i} className="text-xs text-[hsl(var(--muted-foreground))]">
                        {describeAction(a, devices)}
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <div className="flex items-center gap-1 shrink-0">
                <button
                  title="Activate"
                  onClick={() => activateMutation.mutate(scene.id)}
                  disabled={activateMutation.isPending}
                  className="p-2 rounded-lg text-green-500 hover:bg-green-500/10 transition-colors disabled:opacity-40"
                >
                  <Play size={14} />
                </button>
                <button
                  title="Edit"
                  onClick={() => setEditing(scene)}
                  className="p-2 rounded-lg text-[hsl(var(--muted-foreground))] hover:bg-[hsl(var(--accent))] transition-colors"
                >
                  <Pencil size={14} />
                </button>
                <button
                  title="Delete"
                  onClick={() => { if (confirm(`Delete scene "${scene.name}"?`)) deleteMutation.mutate(scene.id) }}
                  disabled={deleteMutation.isPending}
                  className="p-2 rounded-lg text-[hsl(var(--muted-foreground))] hover:text-red-400 hover:bg-red-400/10 transition-colors disabled:opacity-40"
                >
                  <Trash2 size={14} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Modal */}
      {editing !== undefined && (
        <SceneModal
          scene={editing === 'new' ? null : editing}
          devices={devices}
          onClose={() => setEditing(undefined)}
        />
      )}
    </div>
  )
}
