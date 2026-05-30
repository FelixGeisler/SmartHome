/**
 * Time-based automation rules.
 *
 * Each rule fires at a specific HH:mm time on selected days of the week
 * and sends a command to one device (light, plug, or thermostat).
 *
 * The form is deliberately simple — no sensor thresholds, no JSON editing.
 */

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2, Pencil, Clock, X, CheckCircle2 } from 'lucide-react'
import { api } from '@/api/client'
import type { Rule, Device } from '@/api/client'
import { cn } from '@/lib/utils'
import { toast } from 'sonner'

// ── History helpers ───────────────────────────────────────────────────────────

function describePayload(json: string): string {
  try {
    const p = JSON.parse(json)
    if (p.on === true)                   return 'Turned on'
    if (p.on === false)                  return 'Turned off'
    if (p.brightness != null)            return `Brightness → ${p.brightness}%`
    if (p.setPointTemperature != null)   return `Temp → ${p.setPointTemperature}°C`
    return json
  } catch { return json }
}

// ── History tab content ───────────────────────────────────────────────────────

function HistoryTab() {
  const qc = useQueryClient()

  const { data: events = [], isLoading } = useQuery({
    queryKey: ['automationHistory'],
    queryFn:  () => api.automationHistory.list(200),
    refetchInterval: 60_000,
  })

  const clearMutation = useMutation({
    mutationFn: api.automationHistory.clear,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['automationHistory'] }); toast.success('History cleared') },
    onError: () => toast.error('Failed to clear history'),
  })

  if (isLoading) return <div className="text-sm text-[hsl(var(--muted-foreground))]">Loading…</div>

  return (
    <div className="space-y-4">
      {events.length > 0 && (
        <div className="flex justify-end">
          <button
            onClick={() => { if (confirm('Clear all history?')) clearMutation.mutate() }}
            disabled={clearMutation.isPending}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm text-red-400 border border-red-400/30 hover:bg-red-400/10 transition-colors disabled:opacity-40"
          >
            <Trash2 size={13} /> Clear
          </button>
        </div>
      )}

      {events.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-12 text-[hsl(var(--muted-foreground))]">
          <Clock size={36} className="opacity-20" />
          <p className="text-sm">No automations have fired yet.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {events.map(e => (
            <div key={e.id}
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

// ── Day helpers ───────────────────────────────────────────────────────────────

const ALL_DAYS = [
  { value: 1, short: 'Mo' },
  { value: 2, short: 'Tu' },
  { value: 3, short: 'We' },
  { value: 4, short: 'Th' },
  { value: 5, short: 'Fr' },
  { value: 6, short: 'Sa' },
  { value: 7, short: 'Su' },
]

function parseDays(triggerDays: string | null): number[] {
  if (!triggerDays?.trim()) return ALL_DAYS.map(d => d.value)
  return triggerDays.split(',').map(Number).filter(n => n >= 1 && n <= 7)
}

function formatDays(days: number[]): string | null {
  if (days.length === 7) return null // every day → store null
  return days.sort((a, b) => a - b).join(',')
}

function describeDays(triggerDays: string | null): string {
  const days = parseDays(triggerDays)
  if (days.length === 7) return 'every day'
  if (days.length === 5 && !days.includes(6) && !days.includes(7)) return 'weekdays'
  if (days.length === 2 && days.includes(6) && days.includes(7)) return 'weekends'
  return days.map(v => ALL_DAYS.find(d => d.value === v)?.short ?? v).join(', ')
}

// ── Action helpers ────────────────────────────────────────────────────────────

type ActionType = 'turn-on' | 'turn-off' | 'set-brightness' | 'set-temperature'

interface ActionOption {
  value: ActionType
  label: string
  hasValue?: boolean
  unit?: string
  defaultVal?: number
  min?: number
  max?: number
  step?: number
}

function actionsForDevice(device: Device | undefined): ActionOption[] {
  if (!device) return []
  switch (device.type) {
    case 'HUE_LIGHT':
      return [
        { value: 'turn-on',         label: 'Turn on' },
        { value: 'turn-off',        label: 'Turn off' },
        { value: 'set-brightness',  label: 'Set brightness', hasValue: true, unit: '%', defaultVal: 80, min: 1, max: 100, step: 1 },
      ]
    case 'SHELLY_PLUG':
      return [
        { value: 'turn-on',  label: 'Turn on' },
        { value: 'turn-off', label: 'Turn off' },
      ]
    case 'HOMEMATIC_RADIATOR':
      return [
        { value: 'set-temperature', label: 'Set temperature', hasValue: true, unit: '°C', defaultVal: 20, min: 5, max: 30, step: 0.5 },
      ]
    default:
      return []
  }
}

function buildPayload(actionType: ActionType, actionValue: number, _device: Device): string {
  switch (actionType) {
    case 'turn-on':          return '{"on":true}'
    case 'turn-off':         return '{"on":false}'
    case 'set-brightness':   return JSON.stringify({ brightness: actionValue })
    case 'set-temperature':  return JSON.stringify({ setPointTemperature: actionValue })
    default:                 return '{}'
  }
}

function parseActionFromPayload(payload: string, device: Device): { type: ActionType; value: number } {
  try {
    const p = JSON.parse(payload)
    if (p.on === true  || p.relay === true)  return { type: 'turn-on',  value: 0 }
    if (p.on === false || p.relay === false) return { type: 'turn-off', value: 0 }
    if (p.brightness != null) return { type: 'set-brightness', value: p.brightness }
    if (p.setPointTemperature != null) return { type: 'set-temperature', value: p.setPointTemperature }
  } catch { /* fall through */ }
  // Guess a default based on device type
  return device?.type === 'HOMEMATIC_RADIATOR'
    ? { type: 'set-temperature', value: 20 }
    : { type: 'turn-on', value: 0 }
}

function describeAction(rule: Rule, device: Device | undefined): string {
  if (!device) return rule.actionPayloadJson
  try {
    const p = JSON.parse(rule.actionPayloadJson)
    if (p.on === true  || p.relay === true)  return 'Turn on'
    if (p.on === false || p.relay === false) return 'Turn off'
    if (p.brightness != null) return `Set brightness to ${p.brightness}%`
    if (p.setPointTemperature != null) return `Set temp to ${p.setPointTemperature}°C`
  } catch { /* */ }
  return rule.actionPayloadJson
}

// ── Rule form ─────────────────────────────────────────────────────────────────

const CONTROLLABLE = new Set<Device['type']>(['HUE_LIGHT', 'SHELLY_PLUG', 'HOMEMATIC_RADIATOR'])

interface RuleFormProps {
  initial?: Rule
  devices: Device[]
  onSave: (rule: Omit<Rule, 'id' | 'lastTriggered'>) => void
  onCancel: () => void
  saving: boolean
}

function RuleForm({ initial, devices, onSave, onCancel, saving }: RuleFormProps) {
  const controllable = devices.filter(d => CONTROLLABLE.has(d.type))

  // Initialise from existing rule or defaults
  const initDevice = initial ? controllable.find(d => d.id === initial.targetDeviceId) : undefined
  const initAction = initial && initDevice
    ? parseActionFromPayload(initial.actionPayloadJson, initDevice)
    : { type: 'turn-on' as ActionType, value: 0 }
  const initDays = parseDays(initial?.triggerDays ?? null)

  const [name,         setName]        = useState(initial?.name ?? '')
  const [time,         setTime]        = useState(initial?.triggerTime ?? '07:00')
  const [selectedDays, setSelectedDays]= useState<number[]>(initDays)
  const [deviceId,     setDeviceId]    = useState<number>(initial?.targetDeviceId ?? 0)
  const [actionType,   setActionType]  = useState<ActionType>(initAction.type)
  const [actionValue,  setActionValue] = useState<number>(initAction.value)

  const device = controllable.find(d => d.id === deviceId)
  const actions = actionsForDevice(device)
  const currentAction = actions.find(a => a.value === actionType) ?? actions[0]

  // When device changes, reset action to the first available
  function handleDeviceChange(id: number) {
    setDeviceId(id)
    const newDevice = controllable.find(d => d.id === id)
    const newActions = actionsForDevice(newDevice)
    if (newActions.length > 0) {
      setActionType(newActions[0].value)
      setActionValue(newActions[0].defaultVal ?? 0)
    }
  }

  function toggleDay(v: number) {
    setSelectedDays(prev =>
      prev.includes(v) ? prev.filter(d => d !== v) : [...prev, v]
    )
  }

  function handleSave() {
    if (!name.trim() || !time || !device || selectedDays.length === 0) return
    const payload = buildPayload(actionType, actionValue, device)
    onSave({
      name: name.trim(),
      enabled: initial?.enabled ?? true,
      triggerTime: time,
      triggerDays: formatDays(selectedDays),
      targetDeviceId: device.id,
      actionPayloadJson: payload,
      cooldownMs: 60_000,
    })
  }

  const inputCls = 'w-full text-sm px-3 py-2 rounded-md border border-[hsl(var(--border))] bg-[hsl(var(--background))] focus:outline-none focus:ring-2 focus:ring-[hsl(var(--ring))]'
  const canSave  = name.trim() && time && deviceId && selectedDays.length > 0 && actions.length > 0

  return (
    <div className="rounded-xl border border-[hsl(var(--primary)/0.5)] bg-[hsl(var(--card))] p-5 space-y-5">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold text-sm">{initial ? 'Edit Automation' : 'New Automation'}</h3>
        <button onClick={onCancel} className="p-1 rounded hover:bg-[hsl(var(--accent))] transition-colors">
          <X size={14} />
        </button>
      </div>

      {/* Name */}
      <div className="space-y-1.5">
        <label className="text-xs font-medium text-[hsl(var(--muted-foreground))] uppercase tracking-wide">Name</label>
        <input
          value={name} onChange={e => setName(e.target.value)}
          placeholder="e.g. Morning lights on"
          className={inputCls}
        />
      </div>

      {/* Time */}
      <div className="space-y-1.5">
        <label className="text-xs font-medium text-[hsl(var(--muted-foreground))] uppercase tracking-wide">Time</label>
        <input
          type="time" value={time} onChange={e => setTime(e.target.value)}
          className={cn(inputCls, 'w-auto')}
        />
      </div>

      {/* Days */}
      <div className="space-y-2">
        <label className="text-xs font-medium text-[hsl(var(--muted-foreground))] uppercase tracking-wide">Days</label>
        <div className="flex gap-1.5 flex-wrap">
          {ALL_DAYS.map(d => (
            <button
              key={d.value}
              type="button"
              onClick={() => toggleDay(d.value)}
              className={cn(
                'w-10 h-10 rounded-lg text-xs font-semibold border-2 transition-all',
                selectedDays.includes(d.value)
                  ? 'bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] border-[hsl(var(--primary))]'
                  : 'border-[hsl(var(--border))] text-[hsl(var(--muted-foreground))] hover:border-[hsl(var(--primary)/0.5)]'
              )}
            >
              {d.short}
            </button>
          ))}
        </div>
        {selectedDays.length === 0 && (
          <p className="text-xs text-red-400">Select at least one day</p>
        )}
      </div>

      {/* Device */}
      <div className="space-y-1.5">
        <label className="text-xs font-medium text-[hsl(var(--muted-foreground))] uppercase tracking-wide">Device</label>
        {controllable.length === 0
          ? <p className="text-xs text-[hsl(var(--muted-foreground))]">No controllable devices found. Discover devices first.</p>
          : (
            <select value={deviceId} onChange={e => handleDeviceChange(Number(e.target.value))} className={inputCls}>
              <option value={0}>— select a device —</option>
              {controllable.map(d => (
                <option key={d.id} value={d.id}>
                  {d.name}{d.room ? ` (${d.room})` : ''}
                </option>
              ))}
            </select>
          )
        }
      </div>

      {/* Action */}
      {device && actions.length > 0 && (
        <div className="space-y-2">
          <label className="text-xs font-medium text-[hsl(var(--muted-foreground))] uppercase tracking-wide">Action</label>
          <div className="flex gap-2 flex-wrap">
            {actions.map(a => (
              <button
                key={a.value}
                type="button"
                onClick={() => { setActionType(a.value); setActionValue(a.defaultVal ?? 0) }}
                className={cn(
                  'px-3 py-1.5 rounded-lg text-sm border-2 transition-all',
                  actionType === a.value
                    ? 'bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] border-[hsl(var(--primary))]'
                    : 'border-[hsl(var(--border))] text-[hsl(var(--muted-foreground))] hover:border-[hsl(var(--primary)/0.5)]'
                )}
              >
                {a.label}
              </button>
            ))}
          </div>
          {currentAction?.hasValue && (
            <div className="flex items-center gap-3">
              <input
                type="number"
                value={actionValue}
                min={currentAction.min}
                max={currentAction.max}
                step={currentAction.step}
                onChange={e => setActionValue(Number(e.target.value))}
                className={cn(inputCls, 'w-28')}
              />
              <span className="text-sm text-[hsl(var(--muted-foreground))]">{currentAction.unit}</span>
            </div>
          )}
        </div>
      )}

      <div className="flex justify-end gap-2 pt-1">
        <button onClick={onCancel} className="px-4 py-2 rounded-lg text-sm bg-[hsl(var(--muted))] hover:opacity-80 transition-opacity">
          Cancel
        </button>
        <button
          onClick={handleSave}
          disabled={saving || !canSave}
          className="px-4 py-2 rounded-lg text-sm bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] hover:opacity-90 transition-opacity disabled:opacity-40"
        >
          {saving ? 'Saving…' : 'Save'}
        </button>
      </div>
    </div>
  )
}

// ── Rule card ─────────────────────────────────────────────────────────────────

function RuleCard({ rule, devices, onEdit, onDelete, onToggle }: {
  rule: Rule
  devices: Device[]
  onEdit: () => void
  onDelete: () => void
  onToggle: () => void
}) {
  const device = devices.find(d => d.id === rule.targetDeviceId)
  const deviceIcon =
    device?.type === 'HUE_LIGHT'           ? '💡'
    : device?.type === 'SHELLY_PLUG'        ? '🔌'
    : device?.type === 'HOMEMATIC_RADIATOR' ? '🌡️'
    : '📡'

  return (
    <div className={cn(
      'flex items-center gap-4 rounded-xl border p-4 transition-opacity',
      rule.enabled ? 'border-[hsl(var(--border))] bg-[hsl(var(--card))]' : 'border-[hsl(var(--border))] bg-[hsl(var(--card))] opacity-50'
    )}>
      <Clock size={16} className={rule.enabled ? 'text-[hsl(var(--primary))] shrink-0' : 'text-[hsl(var(--muted-foreground))] shrink-0'} />

      <div className="flex-1 min-w-0">
        <p className="font-medium text-sm">{rule.name}</p>
        <p className="text-xs text-[hsl(var(--muted-foreground))] mt-0.5">
          {rule.triggerTime} · {describeDays(rule.triggerDays)}
          {' · '}{deviceIcon} {device?.name ?? `Device #${rule.targetDeviceId}`}
          {' · '}{describeAction(rule, device)}
        </p>
        {rule.lastTriggered && (
          <p className="text-[10px] text-[hsl(var(--muted-foreground))] mt-0.5">
            Last fired: {new Date(rule.lastTriggered).toLocaleString()}
          </p>
        )}
      </div>

      <div className="flex items-center gap-2 shrink-0">
        {/* Enable/disable toggle */}
        <button
          onClick={onToggle}
          className={cn('w-9 h-5 rounded-full transition-colors relative', rule.enabled ? 'bg-[hsl(var(--primary))]' : 'bg-[hsl(var(--muted))]')}
        >
          <span className={cn('absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-transform', rule.enabled ? 'translate-x-4' : 'translate-x-0.5')} />
        </button>
        <button onClick={onEdit} className="p-1.5 rounded-md hover:bg-[hsl(var(--accent))] transition-colors text-[hsl(var(--muted-foreground))]">
          <Pencil size={13} />
        </button>
        <button
          onClick={() => { if (confirm(`Delete "${rule.name}"?`)) onDelete() }}
          className="p-1.5 rounded-md hover:bg-[hsl(var(--accent))] transition-colors text-red-400"
        >
          <Trash2 size={13} />
        </button>
      </div>
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export function AutomationsPage() {
  const qc = useQueryClient()
  const [tab,      setTab]      = useState<'rules' | 'history'>('rules')
  const [showForm, setShowForm] = useState(false)
  const [editing,  setEditing]  = useState<Rule | null>(null)

  const { data: rules   = [], isLoading } = useQuery({ queryKey: ['rules'],   queryFn: api.rules.list })
  const { data: devices = [] }             = useQuery({ queryKey: ['devices'], queryFn: api.devices.list })

  const createRule = useMutation({
    mutationFn: (rule: Omit<Rule, 'id' | 'lastTriggered'>) => api.rules.create(rule),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['rules'] }); setShowForm(false); toast.success('Automation created') },
    onError: () => toast.error('Failed to create automation'),
  })

  const updateRule = useMutation({
    mutationFn: ({ id, rule }: { id: number; rule: Partial<Rule> }) => api.rules.update(id, rule),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['rules'] }); setEditing(null); toast.success('Automation updated') },
    onError: () => toast.error('Failed to update automation'),
  })

  const deleteRule = useMutation({
    mutationFn: (id: number) => api.rules.delete(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['rules'] }); toast.success('Automation deleted') },
    onError: () => toast.error('Failed to delete automation'),
  })

  const toggleRule = useMutation({
    mutationFn: (id: number) => api.rules.toggle(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['rules'] }),
  })

  const showingForm = showForm || editing != null

  const tabCls = (active: boolean) => cn(
    'px-4 py-2 text-sm font-medium border-b-2 transition-colors',
    active
      ? 'border-[hsl(var(--primary))] text-[hsl(var(--foreground))]'
      : 'border-transparent text-[hsl(var(--muted-foreground))] hover:text-[hsl(var(--foreground))]'
  )

  return (
    <div className="p-6 max-w-2xl space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">Automations</h1>
          <p className="text-sm text-[hsl(var(--muted-foreground))] mt-0.5">
            Schedule devices to turn on or off at specific times.
          </p>
        </div>
        {tab === 'rules' && !showingForm && (
          <button
            onClick={() => setShowForm(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] text-sm hover:opacity-90 transition-opacity"
          >
            <Plus size={14} /> New
          </button>
        )}
      </div>

      {/* Tab bar */}
      <div className="flex border-b border-[hsl(var(--border))] -mt-2">
        <button className={tabCls(tab === 'rules')}   onClick={() => setTab('rules')}>Rules</button>
        <button className={tabCls(tab === 'history')} onClick={() => setTab('history')}>History</button>
      </div>

      {/* Rules tab */}
      {tab === 'rules' && (
        <>
          {showForm && (
            <RuleForm
              devices={devices}
              onSave={rule => createRule.mutate(rule)}
              onCancel={() => setShowForm(false)}
              saving={createRule.isPending}
            />
          )}

          {isLoading ? (
            <div className="text-sm text-[hsl(var(--muted-foreground))]">Loading…</div>
          ) : rules.length === 0 && !showingForm ? (
            <div className="flex flex-col items-center gap-3 py-12 text-[hsl(var(--muted-foreground))]">
              <Clock size={36} className="opacity-20" />
              <p className="text-sm">No automations yet.</p>
              <button onClick={() => setShowForm(true)} className="text-sm text-[hsl(var(--primary))] underline underline-offset-2">
                Create your first automation
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              {rules.map(rule =>
                editing?.id === rule.id ? (
                  <RuleForm
                    key={rule.id}
                    initial={rule}
                    devices={devices}
                    onSave={r => updateRule.mutate({ id: rule.id, rule: r })}
                    onCancel={() => setEditing(null)}
                    saving={updateRule.isPending}
                  />
                ) : (
                  <RuleCard
                    key={rule.id}
                    rule={rule}
                    devices={devices}
                    onEdit={() => { setEditing(rule); setShowForm(false) }}
                    onDelete={() => deleteRule.mutate(rule.id)}
                    onToggle={() => toggleRule.mutate(rule.id)}
                  />
                )
              )}
            </div>
          )}
        </>
      )}

      {/* History tab */}
      {tab === 'history' && <HistoryTab />}
    </div>
  )
}
