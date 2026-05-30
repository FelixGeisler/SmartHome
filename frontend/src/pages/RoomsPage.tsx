/**
 * House floor-plan view.
 *
 * Floors (Stockwerke) are tabs at the top.
 * Each floor shows a 16:9 canvas where rooms are draggable / resizable rectangles.
 * Device icons appear inside rooms at their (roomX%, roomY%) position.
 * Clicking a device shows an inline control popover.
 * Dragging a device from the right panel onto a room places it in one API call (fixes the double-drag bug).
 * Edit-mode (pencil button) unlocks room drag + resize handles.
 */

import { useState, useRef, useCallback, Fragment } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Plus, X, Pencil, Trash2,
  Lightbulb, Thermometer, Plug, Activity, Sun, Zap, BarChart2, Home,
  Map as MapIcon, Box,
} from 'lucide-react'
import { toast } from 'sonner'
import { api } from '@/api/client'
import type { Device, Floor, Room } from '@/api/client'
import { cn } from '@/lib/utils'
import { FloorPlan3D } from '@/components/floorplan3d/FloorPlan3D'

type ViewMode = '2d' | '3d'
const VIEW_MODE_KEY = 'roomsPage.viewMode'

// ── Snap grid ─────────────────────────────────────────────────────────────────
// Rooms snap to 2 % steps so edges align perfectly (no gaps, no overlap).
const SNAP = 2
function snap(v: number) { return Math.round(v / SNAP) * SNAP }

// ── Overlap detection ─────────────────────────────────────────────────────────
function rectsOverlap(
  ax: number, ay: number, aw: number, ah: number,
  bx: number, by: number, bw: number, bh: number,
): boolean {
  return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by
}

/**
 * Returns true if the proposed rectangle would overlap any placed rect
 * in otherRooms (checks both primary and L-extension segments).
 */
function wouldOverlapOthers(
  px: number, py: number, pw: number, ph: number,
  otherRooms: Room[],
): boolean {
  return otherRooms.some(r => {
    if (r.planX == null) return false
    if (rectsOverlap(px, py, pw, ph, r.planX, r.planY ?? 0, r.planW ?? 0, r.planH ?? 0)) return true
    if (r.planX2 != null &&
        rectsOverlap(px, py, pw, ph, r.planX2, r.planY2 ?? 0, r.planW2 ?? 0, r.planH2 ?? 0)) return true
    return false
  })
}

// ── Palette: one colour ring per room index ───────────────────────────────────

const ROOM_COLORS = [
  'border-blue-400/60   bg-blue-400/8',
  'border-green-400/60  bg-green-400/8',
  'border-purple-400/60 bg-purple-400/8',
  'border-orange-400/60 bg-orange-400/8',
  'border-pink-400/60   bg-pink-400/8',
  'border-teal-400/60   bg-teal-400/8',
  'border-yellow-400/60 bg-yellow-400/8',
  'border-red-400/60    bg-red-400/8',
]

const ROOM_ICONS = [
  '🛋️','🍳','🛏️','🚿','🏠','🚗','💼','🏃',
  '🌿','📺','🎮','🔧','🧺','☀️','🌙','🔑',
]

// Suggested room presets shown below the canvas in edit mode
const ROOM_PRESETS = [
  { name: 'Living Room',  icon: '🛋️' },
  { name: 'Kitchen',      icon: '🍳' },
  { name: 'Bedroom',      icon: '🛏️' },
  { name: 'Bathroom',     icon: '🚿' },
  { name: 'Hallway',      icon: '🚪' },
  { name: 'Office',       icon: '💼' },
  { name: 'Garage',       icon: '🚗' },
  { name: 'Garden',       icon: '🌿' },
  { name: 'Laundry',      icon: '🧺' },
  { name: 'Storage',      icon: '📦' },
]

// ── Device helpers ────────────────────────────────────────────────────────────

const DEVICE_ICON_MAP: Partial<Record<Device['type'], typeof Lightbulb>> = {
  HUE_LIGHT: Lightbulb, HOMEMATIC_RADIATOR: Thermometer,
  SHELLY_PLUG: Plug, MQTT_SENSOR: BarChart2,
  SOLAKON_ONE: Sun, SOLAKON_METER: Activity, SOLAKON_INVERTER: Zap,
}

function parseState(d: Device): Record<string, unknown> {
  try { return JSON.parse(d.lastStateJson ?? '{}') } catch { return {} }
}
function isActive(d: Device, s: Record<string, unknown>) {
  switch (d.type) {
    case 'HUE_LIGHT':   return !!s.on
    case 'SHELLY_PLUG': return !!(s.relay ?? s.on)
    case 'SOLAKON_ONE': return !!s.generating
    default:            return d.online
  }
}
function statusLine(d: Device, s: Record<string, unknown>) {
  switch (d.type) {
    case 'HUE_LIGHT':
      return s.on ? (typeof s.brightness === 'number' ? `On · ${Math.round(s.brightness)}%` : 'On') : 'Off'
    case 'HOMEMATIC_RADIATOR': {
      const act = s.actualTemperature  ?? s.actual_temperature
      const tgt = s.setPointTemperature ?? s.set_point_temperature
      return act != null ? `${act}°C → ${tgt}°C` : (typeof tgt === 'number' ? `${tgt}°C` : '—')
    }
    case 'SHELLY_PLUG': {
      if (!(s.relay ?? s.on)) return 'Off'
      const pw = s.power ?? s.power_w
      return typeof pw === 'number' ? `${Number(pw).toFixed(0)} W` : 'On'
    }
    case 'SOLAKON_ONE': {
      const pw = s.power_w
      return typeof pw === 'number' ? `${pw} W` : (s.reachable ? 'Standby' : 'Offline')
    }
    case 'SOLAKON_METER': {
      const pw = s.power_w ?? s.activePower
      return typeof pw === 'number' ? `${Number(pw).toFixed(0)} W` : '—'
    }
    default: return d.online ? 'Online' : 'Offline'
  }
}

// ── Device controls in the popover ───────────────────────────────────────────

function DeviceControls({ device }: { device: Device }) {
  const qc  = useQueryClient()
  const s   = parseState(device)
  const cmd = useMutation({
    mutationFn: (p: Record<string, unknown>) => api.devices.command(device.id, p),
    // Optimistic update: flip the state immediately in the cache so the toggle
    // responds instantly, without waiting for the Hue SSE loop to update the DB.
    onMutate: (p) => {
      const snapshot = qc.getQueryData<Device[]>(['devices'])
      qc.setQueryData<Device[]>(['devices'], prev =>
        prev?.map(d => d.id === device.id
          ? { ...d, lastStateJson: JSON.stringify({ ...parseState(d), ...p }) }
          : d
        )
      )
      // Return snapshot so we can rollback if the command fails
      return { snapshot }
    },
    // Do NOT invalidate on success — the DB still holds the old state at this
    // point (the Hue SSE hasn't fired yet). Invalidating would undo the optimistic
    // update. The 10-second polling interval will sync the real state once the
    // SSE cycle completes.
    onError: (_err, _vars, ctx) => {
      if (ctx?.snapshot) qc.setQueryData(['devices'], ctx.snapshot)
      toast.error('Command failed')
    },
  })
  switch (device.type) {
    case 'HUE_LIGHT':
    case 'SHELLY_PLUG': {
      const on  = device.type === 'HUE_LIGHT' ? !!s.on : !!(s.relay ?? s.on)
      const key = device.type === 'HUE_LIGHT' ? 'on' : 'relay'
      return (
        <div className="flex items-center justify-between gap-4">
          <span className="text-sm text-[hsl(var(--muted-foreground))]">{on ? 'On' : 'Off'}</span>
          <button onClick={() => cmd.mutate({ [key]: !on })}
            className={cn('relative w-12 h-6 rounded-full transition-colors shrink-0',
              on ? 'bg-[hsl(var(--primary))]' : 'bg-[hsl(var(--muted))]')}>
            <span className={cn('absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform', on && 'translate-x-6')} />
          </button>
        </div>
      )
    }
    case 'HOMEMATIC_RADIATOR': {
      const tgt = (typeof s.setPointTemperature === 'number' ? s.setPointTemperature
                 : typeof s.set_point_temperature === 'number' ? s.set_point_temperature : 20) as number
      const act = s.actualTemperature ?? s.actual_temperature
      return (
        <div className="space-y-1.5">
          {act != null && <p className="text-xs text-[hsl(var(--muted-foreground))]">Room: {String(act)} °C</p>}
          <div className="flex items-center gap-2">
            <button onClick={() => cmd.mutate({ setPointTemperature: Math.max(5, tgt - 0.5) })}
              className="w-8 h-8 rounded-lg bg-[hsl(var(--muted))] hover:bg-[hsl(var(--accent))] flex items-center justify-center text-lg font-semibold transition-colors">−</button>
            <span className="text-base font-semibold w-14 text-center tabular-nums">{tgt} °C</span>
            <button onClick={() => cmd.mutate({ setPointTemperature: Math.min(30, tgt + 0.5) })}
              className="w-8 h-8 rounded-lg bg-[hsl(var(--muted))] hover:bg-[hsl(var(--accent))] flex items-center justify-center text-lg font-semibold transition-colors">+</button>
          </div>
        </div>
      )
    }
    default:
      return <p className="text-sm text-[hsl(var(--muted-foreground))]">{statusLine(device, s)}</p>
  }
}

// ── Device pin inside a room ──────────────────────────────────────────────────

const PIN_R = 14

interface PinProps {
  device:   Device
  roomRef:  React.RefObject<HTMLDivElement | null>
  selected: boolean
  onSelect: () => void
  onMoved:  (id: number, x: number, y: number) => void
}

function DevicePin({ device, roomRef, selected, onSelect, onMoved }: PinProps) {
  const Icon = DEVICE_ICON_MAP[device.type] ?? Home
  const s    = parseState(device)
  const act  = isActive(device, s)

  const [local, setLocal]   = useState<{ x: number; y: number } | null>(null)
  const pdRef  = useRef<{ cx: number; cy: number } | null>(null)
  const moved  = useRef(false)

  const toRel = (clientX: number, clientY: number) => {
    const el = roomRef.current; if (!el) return null
    const r  = el.getBoundingClientRect()
    return {
      x: Math.max(2, Math.min(98, ((clientX - r.left)  / r.width)  * 100)),
      y: Math.max(2, Math.min(98, ((clientY - r.top)   / r.height) * 100)),
    }
  }

  const dx = local?.x ?? device.roomX ?? 50
  const dy = local?.y ?? device.roomY ?? 50

  return (
    <div
      style={{ position: 'absolute', left: `calc(${dx}% - ${PIN_R}px)`, top: `calc(${dy}% - ${PIN_R}px)`,
               width: PIN_R * 2, zIndex: selected ? 20 : 10, touchAction: 'none', userSelect: 'none',
               cursor: local ? 'grabbing' : 'grab' }}
      className="flex flex-col items-center group"
      onPointerDown={e => {
        e.stopPropagation()
        e.currentTarget.setPointerCapture(e.pointerId)
        pdRef.current = { cx: e.clientX, cy: e.clientY }
        moved.current = false
        setLocal({ x: dx, y: dy })
      }}
      onPointerMove={e => {
        if (!pdRef.current) return
        const dd = Math.hypot(e.clientX - pdRef.current.cx, e.clientY - pdRef.current.cy)
        if (!moved.current && dd > 4) moved.current = true
        if (moved.current) { const p = toRel(e.clientX, e.clientY); if (p) setLocal(p) }
      }}
      onPointerUp={e => {
        if (!pdRef.current) return
        const wasDrag = moved.current
        pdRef.current = null; moved.current = false; setLocal(null)
        if (wasDrag) { const p = toRel(e.clientX, e.clientY); if (p) onMoved(device.id, p.x, p.y) }
        else onSelect()
      }}
    >
      <div className={cn('rounded-full flex items-center justify-center shadow border-2 transition-all',
        `w-[${PIN_R * 2}px] h-[${PIN_R * 2}px]`,
        selected ? 'scale-125 shadow-lg border-[hsl(var(--primary))]' : 'border-white/60',
        act ? 'bg-yellow-400 text-yellow-900' : 'bg-[hsl(var(--card))] text-[hsl(var(--muted-foreground))] border-[hsl(var(--border))]',
      )} style={{ width: PIN_R * 2, height: PIN_R * 2 }}>
        <Icon size={11} />
      </div>
      <span className={cn(
        'mt-0.5 text-[9px] font-medium whitespace-nowrap px-1 rounded backdrop-blur-sm pointer-events-none',
        'bg-[hsl(var(--card)/0.9)] leading-tight transition-opacity',
        selected ? 'opacity-100' : 'opacity-0 group-hover:opacity-100',
      )}>
        {device.name.length > 12 ? device.name.slice(0, 11) + '…' : device.name}
      </span>
    </div>
  )
}

// ── Device popover ────────────────────────────────────────────────────────────

function DevicePopover({ device, onClose, onRemove }: {
  device: Device; onClose: () => void; onRemove: () => void
}) {
  const Icon   = DEVICE_ICON_MAP[device.type] ?? Home
  const s      = parseState(device)
  const active = isActive(device, s)
  const px     = device.roomX ?? 50
  const left   = px > 55
    ? `calc(${px}% - ${PIN_R + 8 + 216}px)` : `calc(${px}% + ${PIN_R + 8}px)`
  const top    = `max(6px, min(calc(100% - 200px), calc(${device.roomY ?? 50}% - 70px)))`

  return (
    <div style={{ position: 'absolute', left, top, width: 216, zIndex: 30 }}
      className="bg-[hsl(var(--card))] border border-[hsl(var(--border))] rounded-xl shadow-2xl p-3.5 space-y-3"
      onClick={e => e.stopPropagation()} onPointerDown={e => e.stopPropagation()}>
      <div className="flex items-center gap-2">
        <div className={cn('w-6 h-6 rounded-full flex items-center justify-center shrink-0',
          active ? 'bg-yellow-400 text-yellow-900' : 'bg-[hsl(var(--muted))] text-[hsl(var(--muted-foreground))]')}>
          <Icon size={12} />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-xs font-semibold truncate">{device.name}</p>
          <p className="text-[10px] text-[hsl(var(--muted-foreground))] truncate">{statusLine(device, s)}</p>
        </div>
        <button onClick={onClose} className="p-1 rounded hover:bg-[hsl(var(--accent))] transition-colors">
          <X size={12} />
        </button>
      </div>
      <DeviceControls device={device} />
      <div className="pt-2 border-t border-[hsl(var(--border))]">
        <button onClick={onRemove}
          className="flex items-center gap-1 text-[10px] text-[hsl(var(--muted-foreground))] hover:text-red-400 transition-colors">
          <Trash2 size={10} /> Remove from floor plan
        </button>
      </div>
    </div>
  )
}

// ── Room rectangle ────────────────────────────────────────────────────────────

type ResizeEdge = 'nw' | 'ne' | 'sw' | 'se'

interface RoomRectProps {
  room:       Room
  colorClass: string
  devices:    Device[]
  editMode:   boolean
  selected:   boolean
  selectedDeviceId: number | null
  canvasRef:  React.RefObject<HTMLDivElement | null>
  /** All placed rooms on this floor except this one — used for overlap prevention. */
  otherRooms: Room[]
  onRoomClick:    () => void
  onDeviceSelect: (id: number | null) => void
  onDeviceMoved:  (id: number, x: number, y: number) => void
  onRoomMoved:    (id: number, x: number, y: number, w: number, h: number) => void
  onDrop:         (e: React.DragEvent, roomId: number) => void
  onEdit:         () => void
  onDelete:       () => void
  /** Toggle the L-extension on / off for this room. */
  onToggleL:      () => void
}

function RoomRect({
  room, colorClass, devices, editMode, selected, selectedDeviceId,
  canvasRef, otherRooms, onRoomClick, onDeviceSelect, onDeviceMoved,
  onRoomMoved, onDrop, onEdit, onDelete, onToggleL,
}: RoomRectProps) {
  const roomRef = useRef<HTMLDivElement>(null)

  // ── drag-to-move state ────────────────────────────────────────────────────
  const movePd  = useRef<{ cx: number; cy: number; ox: number; oy: number } | null>(null)
  const moveMov = useRef(false)
  const [localPos, setLocalPos] = useState<{ x: number; y: number } | null>(null)

  // ── resize state ──────────────────────────────────────────────────────────
  const resizePd = useRef<{
    edge: ResizeEdge; cx: number; cy: number
    ox: number; oy: number; ow: number; oh: number
  } | null>(null)
  const [localSize, setLocalSize] = useState<{ x: number; y: number; w: number; h: number } | null>(null)

  const toCanvas = (clientX: number, clientY: number) => {
    const el = canvasRef.current; if (!el) return null
    const r  = el.getBoundingClientRect()
    return { x: ((clientX - r.left) / r.width) * 100, y: ((clientY - r.top) / r.height) * 100 }
  }

  const px = localSize?.x ?? localPos?.x ?? room.planX ?? 5
  const py = localSize?.y ?? localPos?.y ?? room.planY ?? 5
  const pw = localSize?.w ?? room.planW ?? 25
  const ph = localSize?.h ?? room.planH ?? 30

  // ── body pointer handlers (move) ──────────────────────────────────────────
  const onBodyDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!editMode) return
    e.stopPropagation()
    e.currentTarget.setPointerCapture(e.pointerId)
    const cp = toCanvas(e.clientX, e.clientY); if (!cp) return
    movePd.current  = { cx: e.clientX, cy: e.clientY, ox: px, oy: py }
    moveMov.current = false
  }
  const onBodyMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!movePd.current) return
    const dd = Math.hypot(e.clientX - movePd.current.cx, e.clientY - movePd.current.cy)
    if (!moveMov.current && dd > 4) moveMov.current = true
    if (moveMov.current) {
      const cp = toCanvas(e.clientX, e.clientY); if (!cp) return
      const origCP = toCanvas(movePd.current.cx, movePd.current.cy); if (!origCP) return
      const nx = snap(Math.max(0, Math.min(100 - pw, movePd.current.ox + (cp.x - origCP.x))))
      const ny = snap(Math.max(0, Math.min(100 - ph, movePd.current.oy + (cp.y - origCP.y))))
      // Hard stop: only move if the new position doesn't overlap another room
      if (!wouldOverlapOthers(nx, ny, pw, ph, otherRooms)) setLocalPos({ x: nx, y: ny })
    }
  }
  const onBodyUp = (_e: React.PointerEvent<HTMLDivElement>) => {
    if (!movePd.current) return
    const wasDrag = moveMov.current
    if (wasDrag && localPos) onRoomMoved(room.id, localPos.x, localPos.y, pw, ph)
    else if (!wasDrag) onRoomClick()
    movePd.current = null; moveMov.current = false; setLocalPos(null)
  }

  // ── resize handle pointer handlers ───────────────────────────────────────
  const onHandleDown = (e: React.PointerEvent<HTMLDivElement>, edge: ResizeEdge) => {
    e.stopPropagation()
    e.currentTarget.setPointerCapture(e.pointerId)
    resizePd.current = { edge, cx: e.clientX, cy: e.clientY, ox: px, oy: py, ow: pw, oh: ph }
  }
  const onHandleMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!resizePd.current) return
    const cp = toCanvas(e.clientX, e.clientY); if (!cp) return
    const { edge, ox, oy, ow, oh } = resizePd.current

    // Keep the OPPOSITE edge fixed; move only the dragged edge toward the cursor.
    // This prevents the opposite side from growing when the dragged side hits a boundary.
    const fixedRight  = ox + ow   // held fixed when dragging left edges  (nw / sw)
    const fixedBottom = oy + oh   // held fixed when dragging top  edges  (nw / ne)
    let nx = ox, ny = oy, nw = ow, nh = oh

    if (edge === 'nw' || edge === 'sw') {
      nx = snap(Math.max(0, Math.min(fixedRight - SNAP, cp.x)))
      nw = fixedRight - nx
    } else {
      const newRight = snap(Math.min(100, Math.max(ox + SNAP, cp.x)))
      nw = newRight - ox
    }

    if (edge === 'nw' || edge === 'ne') {
      ny = snap(Math.max(0, Math.min(fixedBottom - SNAP, cp.y)))
      nh = fixedBottom - ny
    } else {
      const newBottom = snap(Math.min(100, Math.max(oy + SNAP, cp.y)))
      nh = newBottom - oy
    }

    setLocalSize({ x: nx, y: ny, w: nw, h: nh })
  }
  const onHandleUp = () => {
    if (resizePd.current && localSize) onRoomMoved(room.id, localSize.x, localSize.y, localSize.w, localSize.h)
    resizePd.current = null; setLocalSize(null)
  }

  // Handles are only visible while hovering the room (or while a resize is active).
  // This avoids the visual pile-up where adjacent rooms would show two circles at the
  // shared corner. `-top-2 / -left-2` = -8px, which centers a 12px circle exactly on
  // the outer border corner (padding-box origin is 2px inside the 2px border).
  const handleBase = cn(
    'absolute w-3 h-3 rounded-full z-30 shadow-md',
    'bg-[hsl(var(--primary))] border-2 border-white',
    localSize == null && 'opacity-0 group-hover:opacity-100',
    'transition-opacity',
  )

  return (
    <div
      ref={roomRef}
      style={{
        position: 'absolute',
        left: `${px}%`, top: `${py}%`, width: `${pw}%`, height: `${ph}%`,
        cursor: editMode ? (moveMov.current ? 'grabbing' : 'grab') : 'default',
        // Lift above sibling rooms when a device popover is open so it's never covered
        zIndex: selectedDeviceId != null ? 40 : 1,
      }}
      className={cn('group rounded-lg border-2 overflow-visible transition-shadow', colorClass,
        selected && !editMode && 'ring-2 ring-[hsl(var(--primary))] ring-offset-1')}
      onPointerDown={onBodyDown}
      onPointerMove={e => { onBodyMove(e); onHandleMove(e) }}
      onPointerUp={e => { onBodyUp(e); onHandleUp() }}
      onDragOver={e => { e.preventDefault(); e.stopPropagation() }}
      onDrop={e => { e.stopPropagation(); onDrop(e, room.id) }}
      onClick={e => e.stopPropagation()}
    >
      {/* Room label */}
      <div className={cn(
        'absolute top-1.5 left-2 right-2 flex items-center gap-1 text-xs font-semibold select-none pointer-events-none',
        editMode && 'pointer-events-auto',
      )}>
        <span className="text-sm leading-none">{room.icon}</span>
        <span className="truncate leading-tight">{room.name}</span>
        {editMode && (
          <div className="ml-auto flex gap-0.5 pointer-events-auto">
            <button
              onPointerDown={e => e.stopPropagation()}
              onClick={e => { e.stopPropagation(); onToggleL() }}
              title={room.planX2 != null ? 'Remove L-extension' : 'Add L-extension'}
              className={cn(
                'px-1 py-0.5 rounded hover:bg-white/40 transition-colors text-[9px] font-bold leading-none',
                room.planX2 != null ? 'text-[hsl(var(--primary))]' : 'opacity-60',
              )}>L</button>
            <button onPointerDown={e => e.stopPropagation()} onClick={e => { e.stopPropagation(); onEdit() }}
              className="p-0.5 rounded hover:bg-white/40 transition-colors"><Pencil size={10} /></button>
            <button onPointerDown={e => e.stopPropagation()} onClick={e => { e.stopPropagation(); onDelete() }}
              className="p-0.5 rounded hover:bg-white/40 text-red-500 transition-colors"><Trash2 size={10} /></button>
          </div>
        )}
      </div>

      {/* Device pins */}
      {!editMode && devices.map(d => (
        <DevicePin
          key={d.id}
          device={d}
          roomRef={roomRef}
          selected={selectedDeviceId === d.id}
          onSelect={() => onDeviceSelect(selectedDeviceId === d.id ? null : d.id)}
          onMoved={onDeviceMoved}
        />
      ))}

      {/* Popover for selected device */}
      {!editMode && selectedDeviceId != null && (() => {
        const dev = devices.find(d => d.id === selectedDeviceId); if (!dev) return null
        return (
          <DevicePopover device={dev} onClose={() => onDeviceSelect(null)}
            onRemove={() => { onDeviceMoved(dev.id, -1, -1); onDeviceSelect(null) }} />
        )
      })()}

      {/* Resize handles (edit mode only) */}
      {editMode && (
        <>
          {(['nw','ne','sw','se'] as ResizeEdge[]).map(edge => (
            <div key={edge}
              className={cn(handleBase,
                edge === 'nw' && '-top-2 -left-2 cursor-nw-resize',
                edge === 'ne' && '-top-2 -right-2 cursor-ne-resize',
                edge === 'sw' && '-bottom-2 -left-2 cursor-sw-resize',
                edge === 'se' && '-bottom-2 -right-2 cursor-se-resize',
              )}
              onPointerDown={e => { e.stopPropagation(); onHandleDown(e, edge) }}
            />
          ))}
        </>
      )}
    </div>
  )
}

// ── L-shaped extension rectangle ─────────────────────────────────────────────
// Renders the second segment of an L-shaped room.  Dashed border of the same
// colour as the primary rect.  Independent drag + resize; overlap-safe.

interface LExtRectProps {
  room:       Room
  colorClass: string
  editMode:   boolean
  canvasRef:  React.RefObject<HTMLDivElement | null>
  otherRooms: Room[]
  onExtensionMoved: (id: number, x: number, y: number, w: number, h: number) => void
  onRemove:   () => void
}

function LExtensionRect({ room, colorClass, editMode, canvasRef, otherRooms, onExtensionMoved, onRemove }: LExtRectProps) {
  const movePd  = useRef<{ cx: number; cy: number; ox: number; oy: number } | null>(null)
  const moveMov = useRef(false)
  const [localPos,  setLocalPos]  = useState<{ x: number; y: number } | null>(null)

  const resizePd = useRef<{
    edge: ResizeEdge; cx: number; cy: number
    ox: number; oy: number; ow: number; oh: number
  } | null>(null)
  const [localSize, setLocalSize] = useState<{ x: number; y: number; w: number; h: number } | null>(null)

  const toCanvas = (clientX: number, clientY: number) => {
    const el = canvasRef.current; if (!el) return null
    const r  = el.getBoundingClientRect()
    return { x: ((clientX - r.left) / r.width) * 100, y: ((clientY - r.top) / r.height) * 100 }
  }

  const px = localSize?.x ?? localPos?.x ?? (room.planX2 ?? 5)
  const py = localSize?.y ?? localPos?.y ?? (room.planY2 ?? 5)
  const pw = localSize?.w ?? (room.planW2 ?? 14)
  const ph = localSize?.h ?? (room.planH2 ?? 14)

  const onBodyDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!editMode) return
    e.stopPropagation()
    e.currentTarget.setPointerCapture(e.pointerId)
    movePd.current  = { cx: e.clientX, cy: e.clientY, ox: px, oy: py }
    moveMov.current = false
  }
  const onBodyMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!movePd.current) return
    const dd = Math.hypot(e.clientX - movePd.current.cx, e.clientY - movePd.current.cy)
    if (!moveMov.current && dd > 4) moveMov.current = true
    if (moveMov.current) {
      const cp     = toCanvas(e.clientX, e.clientY); if (!cp) return
      const origCP = toCanvas(movePd.current.cx, movePd.current.cy); if (!origCP) return
      const nx = snap(Math.max(0, Math.min(100 - pw, movePd.current.ox + (cp.x - origCP.x))))
      const ny = snap(Math.max(0, Math.min(100 - ph, movePd.current.oy + (cp.y - origCP.y))))
      if (!wouldOverlapOthers(nx, ny, pw, ph, otherRooms)) setLocalPos({ x: nx, y: ny })
    }
  }
  const onBodyUp = () => {
    if (!movePd.current) return
    const wasDrag = moveMov.current
    if (wasDrag && localPos) onExtensionMoved(room.id, localPos.x, localPos.y, pw, ph)
    movePd.current = null; moveMov.current = false; setLocalPos(null)
  }

  const onHandleDown = (e: React.PointerEvent<HTMLDivElement>, edge: ResizeEdge) => {
    e.stopPropagation()
    e.currentTarget.setPointerCapture(e.pointerId)
    resizePd.current = { edge, cx: e.clientX, cy: e.clientY, ox: px, oy: py, ow: pw, oh: ph }
  }
  const onHandleMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!resizePd.current) return
    const cp = toCanvas(e.clientX, e.clientY); if (!cp) return
    const { edge, ox, oy, ow, oh } = resizePd.current
    const fixedRight  = ox + ow
    const fixedBottom = oy + oh
    let nx = ox, ny = oy, nw = ow, nh = oh

    if (edge === 'nw' || edge === 'sw') {
      nx = snap(Math.max(0, Math.min(fixedRight - SNAP, cp.x)))
      nw = fixedRight - nx
    } else {
      const newRight = snap(Math.min(100, Math.max(ox + SNAP, cp.x)))
      nw = newRight - ox
    }
    if (edge === 'nw' || edge === 'ne') {
      ny = snap(Math.max(0, Math.min(fixedBottom - SNAP, cp.y)))
      nh = fixedBottom - ny
    } else {
      const newBottom = snap(Math.min(100, Math.max(oy + SNAP, cp.y)))
      nh = newBottom - oy
    }
    setLocalSize({ x: nx, y: ny, w: nw, h: nh })
  }
  const onHandleUp = () => {
    if (resizePd.current && localSize) onExtensionMoved(room.id, localSize.x, localSize.y, localSize.w, localSize.h)
    resizePd.current = null; setLocalSize(null)
  }

  const handleBase = cn(
    'absolute w-3 h-3 rounded-full z-30 shadow-md',
    'bg-[hsl(var(--primary))] border-2 border-white',
    localSize == null && 'opacity-0 group-hover:opacity-100',
    'transition-opacity',
  )

  return (
    <div
      style={{
        position: 'absolute',
        left: `${px}%`, top: `${py}%`, width: `${pw}%`, height: `${ph}%`,
        cursor: editMode ? 'grab' : 'default',
        zIndex: 0,
      }}
      className={cn('group rounded-lg border-2 overflow-visible border-dashed', colorClass)}
      onPointerDown={onBodyDown}
      onPointerMove={e => { onBodyMove(e); onHandleMove(e) }}
      onPointerUp={() => { onBodyUp(); onHandleUp() }}
      onClick={e => e.stopPropagation()}
      onDragOver={e => { e.preventDefault(); e.stopPropagation() }}
    >
      {/* Remove button — visible on hover in edit mode */}
      {editMode && (
        <button
          onPointerDown={e => e.stopPropagation()}
          onClick={e => { e.stopPropagation(); onRemove() }}
          title="Remove L-extension"
          className="absolute top-0.5 right-0.5 p-0.5 rounded opacity-0 group-hover:opacity-100 hover:bg-white/40 text-red-400 transition-all z-10"
        >
          <X size={9} />
        </button>
      )}

      {/* Resize handles (edit mode only) */}
      {editMode && (
        <>
          {(['nw','ne','sw','se'] as ResizeEdge[]).map(edge => (
            <div key={edge}
              className={cn(handleBase,
                edge === 'nw' && '-top-2 -left-2 cursor-nw-resize',
                edge === 'ne' && '-top-2 -right-2 cursor-ne-resize',
                edge === 'sw' && '-bottom-2 -left-2 cursor-sw-resize',
                edge === 'se' && '-bottom-2 -right-2 cursor-se-resize',
              )}
              onPointerDown={e => { e.stopPropagation(); onHandleDown(e, edge) }}
            />
          ))}
        </>
      )}
    </div>
  )
}

// ── Device panel (right side) ─────────────────────────────────────────────────

function DevicePanel({ devices, rooms }: { devices: Device[]; rooms: Room[] }) {
  return (
    <div className="w-44 shrink-0 flex flex-col border-l border-[hsl(var(--border))] bg-[hsl(var(--card))]">
      <div className="px-3 py-2.5 border-b border-[hsl(var(--border))]">
        <p className="text-xs font-semibold">Devices</p>
        <p className="text-[10px] text-[hsl(var(--muted-foreground))] mt-0.5 leading-tight">Drag into a room</p>
      </div>
      <div className="flex-1 overflow-y-auto p-1.5 space-y-1">
        {devices.length === 0
          ? <p className="text-[11px] text-[hsl(var(--muted-foreground))] italic text-center py-6">All placed ✓</p>
          : devices.map(d => <PanelPin key={d.id} device={d} rooms={rooms} />)
        }
      </div>
    </div>
  )
}

function PanelPin({ device, rooms }: { device: Device; rooms: Room[] }) {
  const Icon  = DEVICE_ICON_MAP[device.type] ?? Home
  const s     = parseState(device)
  const act   = isActive(device, s)
  const room  = rooms.find(r => r.name === device.room)
  return (
    <div draggable
      onDragStart={e => { e.dataTransfer.setData('deviceId', String(device.id)); e.dataTransfer.effectAllowed = 'copy' }}
      className="flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-[hsl(var(--accent))] cursor-grab active:cursor-grabbing transition-colors select-none">
      <div className={cn('w-6 h-6 rounded-full flex items-center justify-center shrink-0',
        act ? 'bg-yellow-400/20 text-yellow-500' : 'bg-[hsl(var(--muted))] text-[hsl(var(--muted-foreground))]')}>
        <Icon size={11} />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-xs font-medium truncate leading-tight">{device.name}</p>
        <p className="text-[10px] text-[hsl(var(--muted-foreground))] truncate">
          {room ? `${room.icon} ${room.name}` : 'Unassigned'}
        </p>
      </div>
    </div>
  )
}

// ── Floor / Room dialogs ──────────────────────────────────────────────────────

function SimpleDialog({ title, placeholder, initial, onSave, onClose }: {
  title: string; placeholder: string; initial?: string
  onSave: (name: string) => void; onClose: () => void
}) {
  const [val, setVal] = useState(initial ?? '')
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-[hsl(var(--card))] rounded-xl shadow-2xl w-72 p-5 space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="font-semibold text-sm">{title}</h3>
          <button onClick={onClose} className="p-1 rounded-md hover:bg-[hsl(var(--accent))] transition-colors"><X size={14} /></button>
        </div>
        <input autoFocus value={val} onChange={e => setVal(e.target.value)}
          placeholder={placeholder}
          onKeyDown={e => e.key === 'Enter' && val.trim() && onSave(val.trim())}
          className="w-full px-3 py-2 rounded-lg border border-[hsl(var(--border))] bg-[hsl(var(--background))] text-sm" />
        <div className="flex gap-2">
          <button onClick={onClose} className="flex-1 px-3 py-2 rounded-lg text-sm border border-[hsl(var(--border))] hover:bg-[hsl(var(--accent))] transition-colors">Cancel</button>
          <button onClick={() => val.trim() && onSave(val.trim())} disabled={!val.trim()}
            className="flex-1 px-3 py-2 rounded-lg text-sm bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] hover:opacity-90 disabled:opacity-40 transition-opacity">Save</button>
        </div>
      </div>
    </div>
  )
}

function RoomDialog({ initial, onSave, onClose }: {
  initial?: { name: string; icon: string }
  onSave: (name: string, icon: string) => void; onClose: () => void
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [icon, setIcon] = useState(initial?.icon ?? '🏠')
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-[hsl(var(--card))] rounded-xl shadow-2xl w-80 p-5 space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="font-semibold text-sm">{initial ? 'Edit Room' : 'New Room'}</h3>
          <button onClick={onClose} className="p-1 rounded-md hover:bg-[hsl(var(--accent))] transition-colors"><X size={14} /></button>
        </div>
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-widest text-[hsl(var(--muted-foreground))] mb-2">Icon</p>
          <div className="flex flex-wrap gap-1.5">
            {ROOM_ICONS.map(ic => (
              <button key={ic} onClick={() => setIcon(ic)}
                className={cn('w-9 h-9 rounded-lg text-lg transition-all',
                  icon === ic ? 'bg-[hsl(var(--primary)/0.15)] ring-2 ring-[hsl(var(--primary))]'
                              : 'bg-[hsl(var(--muted))] hover:bg-[hsl(var(--accent))]')}>{ic}</button>
            ))}
          </div>
        </div>
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-widest text-[hsl(var(--muted-foreground))] mb-1">Name</p>
          <input autoFocus value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Living Room"
            onKeyDown={e => e.key === 'Enter' && name.trim() && onSave(name.trim(), icon)}
            className="w-full px-3 py-2 rounded-lg border border-[hsl(var(--border))] bg-[hsl(var(--background))] text-sm" />
        </div>
        <div className="flex gap-2">
          <button onClick={onClose} className="flex-1 px-3 py-2 rounded-lg text-sm border border-[hsl(var(--border))] hover:bg-[hsl(var(--accent))] transition-colors">Cancel</button>
          <button onClick={() => name.trim() && onSave(name.trim(), icon)} disabled={!name.trim()}
            className="flex-1 px-3 py-2 rounded-lg text-sm bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] hover:opacity-90 disabled:opacity-40 transition-opacity">
            {initial ? 'Save' : 'Add Room'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export function RoomsPage() {
  const qc        = useQueryClient()
  const canvasRef = useRef<HTMLDivElement>(null)

  const [activeFloorId,    setActiveFloorId]    = useState<number | null>(null)
  const [editMode,         setEditMode]         = useState(false)
  const [selectedRoomId,   setSelectedRoomId]   = useState<number | null>(null)
  const [selectedDeviceId, setSelectedDeviceId] = useState<number | null>(null)
  const [viewMode,         setViewMode]         = useState<ViewMode>(
    () => (localStorage.getItem(VIEW_MODE_KEY) as ViewMode) || '2d'
  )
  function changeView(next: ViewMode) {
    setViewMode(next)
    localStorage.setItem(VIEW_MODE_KEY, next)
    // 3D inherently exits edit mode (no in-scene editing yet)
    if (next === '3d') setEditMode(false)
  }
  const [showAddFloor,     setShowAddFloor]     = useState(false)
  const [editFloor,        setEditFloor]        = useState<Floor | null>(null)
  const [showAddRoom,      setShowAddRoom]      = useState(false)
  const [editRoom,         setEditRoom]         = useState<Room | null>(null)

  // ── Floor drag-to-reorder ─────────────────────────────────────────────────
  const [dragFloorId, setDragFloorId] = useState<number | null>(null)
  const [dropIndex,   setDropIndex]   = useState<number | null>(null)
  // Refs to each floor card DOM element so we can hit-test during dragOver
  const floorItemRefs = useRef<Map<number, HTMLDivElement>>(new Map())

  // ── Data ──────────────────────────────────────────────────────────────────

  const { data: floors = [] } = useQuery({ queryKey: ['floors'], queryFn: api.floors.list })
  const { data: rooms  = [] } = useQuery({ queryKey: ['rooms'],  queryFn: api.rooms.list  })
  const { data: devices = [] } = useQuery({
    queryKey: ['devices'], queryFn: api.devices.list, refetchInterval: 10_000,
  })

  const activeFloor = floors.find(f => f.id === activeFloorId)
    ?? (floors.length > 0 ? floors[0] : null)

  const floorRooms  = rooms.filter(r => r.floorId === activeFloor?.id)
  const placedRooms = floorRooms.filter(r => r.planX != null)

  // Devices panel: unassigned + placed in a floor room but with no pin position
  const panelDevices = devices.filter(d =>
    !d.room ||
    (rooms.some(r => r.name === d.room && r.floorId === activeFloor?.id) &&
     (d.roomX == null || d.roomY == null))
  )

  // ── Canvas helpers ────────────────────────────────────────────────────────

  const toCanvas = useCallback((clientX: number, clientY: number) => {
    const el = canvasRef.current; if (!el) return null
    const r  = el.getBoundingClientRect()
    return { x: ((clientX - r.left) / r.width) * 100, y: ((clientY - r.top) / r.height) * 100 }
  }, [])

  function roomAt(cx: number, cy: number): Room | null {
    return placedRooms.find(r =>
      cx >= (r.planX ?? 0) && cx <= (r.planX ?? 0) + (r.planW ?? 0) &&
      cy >= (r.planY ?? 0) && cy <= (r.planY ?? 0) + (r.planH ?? 0)
    ) ?? null
  }

  // ── Mutations ─────────────────────────────────────────────────────────────

  const createFloor = useMutation({
    mutationFn: (name: string) => api.floors.create({ name, sortOrder: floors.length }),
    onSuccess: f => { qc.invalidateQueries({ queryKey: ['floors'] }); setShowAddFloor(false); setActiveFloorId(f.id) },
    onError: () => toast.error('Failed to create floor'),
  })

  const updateFloorName = useMutation({
    mutationFn: ({ id, name }: { id: number; name: string }) => api.floors.update(id, { name }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['floors'] }); setEditFloor(null) },
    onError: () => toast.error('Failed to rename floor'),
  })

  const deleteFloor = useMutation({
    mutationFn: (id: number) => api.floors.delete(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['floors'] }); setActiveFloorId(null) },
    onError: () => toast.error('Failed to delete floor'),
  })

  // displayFloors: sorted descending so DOM index 0 = top of building
  const displayFloors = [...floors].sort((a, b) => b.sortOrder - a.sortOrder)

  function calcDropIndex(clientY: number): number {
    let idx = displayFloors.length
    for (let i = 0; i < displayFloors.length; i++) {
      const el = floorItemRefs.current.get(displayFloors[i].id)
      if (!el) continue
      const mid = el.getBoundingClientRect().top + el.getBoundingClientRect().height / 2
      if (clientY < mid) { idx = i; break }
    }
    return idx
  }

  function commitFloorReorder(draggedId: number, targetDropIndex: number) {
    const fromIdx = displayFloors.findIndex(f => f.id === draggedId)
    if (fromIdx === -1) return
    const toIdx = targetDropIndex > fromIdx ? targetDropIndex - 1 : targetDropIndex
    if (fromIdx === toIdx) return

    const reordered = [...displayFloors]
    const [moved] = reordered.splice(fromIdx, 1)
    reordered.splice(toIdx, 0, moved)

    // Index 0 = top of building = highest sortOrder
    const updated = reordered.map((f, i) => ({ ...f, sortOrder: reordered.length - 1 - i }))
    qc.setQueryData<Floor[]>(['floors'], updated)
    updated.forEach(f => {
      if (floors.find(of => of.id === f.id)?.sortOrder !== f.sortOrder)
        api.floors.update(f.id, { sortOrder: f.sortOrder })
          .catch(() => { toast.error('Failed to reorder floors'); qc.invalidateQueries({ queryKey: ['floors'] }) })
    })
  }

  const createRoom = useMutation({
    mutationFn: (data: Partial<Room> & { name: string; icon: string }) => api.rooms.create(data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['rooms'] }); setShowAddRoom(false) },
    onError: () => toast.error('Failed to create room'),
  })

  const updateRoom = useMutation({
    mutationFn: ({ id, ...data }: Partial<Room> & { id: number }) => api.rooms.update(id, data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['rooms'] }); qc.invalidateQueries({ queryKey: ['devices'] }); setEditRoom(null) },
    onError: () => toast.error('Failed to update room'),
  })

  const deleteRoom = useMutation({
    mutationFn: (id: number) => api.rooms.delete(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['rooms'] }); qc.invalidateQueries({ queryKey: ['devices'] }) },
    onError: () => toast.error('Failed to delete room'),
  })

  const setDevicePosition = useMutation({
    mutationFn: ({ id, x, y }: { id: number; x: number | null; y: number | null }) =>
      api.devices.updatePosition(id, x, y),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['devices'] }),
    onError: () => toast.error('Failed to save position'),
  })

  // ── Room layout change (drag/resize) ──────────────────────────────────────

  function handleRoomMoved(id: number, x: number, y: number, w: number, h: number) {
    qc.setQueryData<Room[]>(['rooms'], prev =>
      prev?.map(r => r.id === id ? { ...r, planX: x, planY: y, planW: w, planH: h } : r)
    )
    updateRoom.mutate({ id, planX: x, planY: y, planW: w, planH: h })
  }

  // ── Drop from panel onto canvas ───────────────────────────────────────────

  function handleCanvasDrop(e: React.DragEvent<HTMLDivElement>) {
    e.preventDefault()
    const deviceId = Number(e.dataTransfer.getData('deviceId'))
    if (!deviceId || !activeFloor) return
    const cp = toCanvas(e.clientX, e.clientY); if (!cp) return
    const room = roomAt(cp.x, cp.y)
    if (!room) { toast.error('Drop inside a room'); return }

    const device = devices.find(d => d.id === deviceId); if (!device) return
    const relX = ((cp.x - (room.planX ?? 0)) / (room.planW ?? 25)) * 100
    const relY = ((cp.y - (room.planY ?? 0)) / (room.planH ?? 30)) * 100

    // Single call — fixes the double-drag bug
    api.devices.update(deviceId, {
      room: room.name,
      roomX: Math.max(5, Math.min(95, relX)),
      roomY: Math.max(5, Math.min(95, relY)),
    }).then(() => qc.invalidateQueries({ queryKey: ['devices'] }))
      .catch(() => toast.error('Failed to place device'))
  }

  // Drop directly onto a room rect
  function handleRoomDrop(e: React.DragEvent, roomId: number) {
    e.preventDefault()
    const deviceId = Number(e.dataTransfer.getData('deviceId'))
    if (!deviceId) return
    const room = rooms.find(r => r.id === roomId); if (!room) return

    // position within room rect using its DOM element rect
    const target = e.currentTarget as HTMLElement
    const rect   = target.getBoundingClientRect()
    const relX   = Math.max(5, Math.min(95, ((e.clientX - rect.left)  / rect.width)  * 100))
    const relY   = Math.max(5, Math.min(95, ((e.clientY - rect.top)   / rect.height) * 100))

    api.devices.update(deviceId, { room: room.name, roomX: relX, roomY: relY })
      .then(() => qc.invalidateQueries({ queryKey: ['devices'] }))
      .catch(() => toast.error('Failed to place device'))
  }

  // ── L-shaped room extension ───────────────────────────────────────────────

  function handleToggleL(room: Room) {
    if (room.planX2 != null) {
      // Remove the L-extension
      qc.setQueryData<Room[]>(['rooms'], prev =>
        prev?.map(r => r.id === room.id ? { ...r, planX2: null, planY2: null, planW2: null, planH2: null } : r))
      updateRoom.mutate({ id: room.id, planX2: null, planY2: null, planW2: null, planH2: null })
    } else {
      // Default position: to the right of the primary rect, occupying the bottom half —
      // together they form a classic L shape.
      const bx = room.planX ?? 5, by = room.planY ?? 5
      const bw = room.planW ?? 22, bh = room.planH ?? 28
      const x2 = snap(Math.min(100 - 14, bx + bw))
      const y2 = snap(by + Math.round(bh / 2))
      const w2 = 14
      const h2 = snap(Math.ceil(bh / 2))
      qc.setQueryData<Room[]>(['rooms'], prev =>
        prev?.map(r => r.id === room.id ? { ...r, planX2: x2, planY2: y2, planW2: w2, planH2: h2 } : r))
      updateRoom.mutate({ id: room.id, planX2: x2, planY2: y2, planW2: w2, planH2: h2 })
    }
  }

  function handleLExtensionMoved(id: number, x: number, y: number, w: number, h: number) {
    qc.setQueryData<Room[]>(['rooms'], prev =>
      prev?.map(r => r.id === id ? { ...r, planX2: x, planY2: y, planW2: w, planH2: h } : r))
    updateRoom.mutate({ id, planX2: x, planY2: y, planW2: w, planH2: h })
  }

  // ── Device pin drag within room ───────────────────────────────────────────

  function handleDeviceMoved(id: number, x: number, y: number) {
    if (x === -1) { setDevicePosition.mutate({ id, x: null, y: null }); return }
    qc.setQueryData<Device[]>(['devices'], prev =>
      prev?.map(d => d.id === id ? { ...d, roomX: x, roomY: y } : d)
    )
    setDevicePosition.mutate({ id, x, y })
  }

  // Next free position for a new room dropped onto the canvas (on the snap grid)
  function nextFreeSlot(): { x: number; y: number } {
    const cols = 4, cellW = 24, cellH = 32
    for (let row = 0; row < 3; row++) {
      for (let col = 0; col < cols; col++) {
        const sx = snap(2 + col * (cellW + 2)), sy = snap(8 + row * (cellH + 4))
        const overlaps = placedRooms.some(r =>
          Math.abs((r.planX ?? 0) - sx) < cellW && Math.abs((r.planY ?? 0) - sy) < cellH)
        if (!overlaps) return { x: sx, y: sy }
      }
    }
    return { x: snap(2), y: snap(8) }
  }

  // ── Render ────────────────────────────────────────────────────────────────

  // displayFloors already computed above (desc sort → DOM index 0 = top floor)

  return (
    <div className="flex h-full overflow-hidden">

      {/* ── Floor selector — building cross-section panel ── */}
      <div className="w-44 shrink-0 flex flex-col border-r border-[hsl(var(--border))] bg-[hsl(var(--card))]">
        <div className="px-3 py-2.5 border-b border-[hsl(var(--border))]">
          <p className="text-[10px] font-bold tracking-[0.12em] uppercase text-[hsl(var(--muted-foreground))]">Floors</p>
        </div>

        {/* Floors stack: index 0 = top of building, last = ground floor.
            Drag a floor card anywhere in the list — a line indicator shows
            where it will land before you release. */}
        <div className="flex-1 flex flex-col gap-1.5 p-2 overflow-y-auto"
          onDragOver={e => { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; setDropIndex(calcDropIndex(e.clientY)) }}
          onDrop={e => { e.preventDefault(); if (dragFloorId != null && dropIndex != null) commitFloorReorder(dragFloorId, dropIndex); setDragFloorId(null); setDropIndex(null) }}
          onDragLeave={e => { if (!(e.currentTarget as HTMLElement).contains(e.relatedTarget as Node)) setDropIndex(null) }}
          onDragEnd={() => { setDragFloorId(null); setDropIndex(null) }}
        >
          {displayFloors.map((floor, i) => {
            const isActive   = activeFloor?.id === floor.id
            const isDragging = dragFloorId === floor.id
            const roomCount  = rooms.filter(r => r.floorId === floor.id && r.planX != null).length
            return (
              <Fragment key={floor.id}>
                {/* Drop indicator line above this item */}
                {dropIndex === i && dragFloorId != null && !isDragging && (
                  <div className="h-0.5 rounded-full bg-[hsl(var(--primary))] mx-1 shrink-0 pointer-events-none" />
                )}
                <div
                  ref={el => { if (el) floorItemRefs.current.set(floor.id, el); else floorItemRefs.current.delete(floor.id) }}
                  draggable
                  onDragStart={e => { e.dataTransfer.effectAllowed = 'move'; setDragFloorId(floor.id) }}
                  className={cn(
                    'relative w-full rounded-xl border-2 px-3 py-2.5 transition-all select-none cursor-grab active:cursor-grabbing shrink-0',
                    isActive   && 'border-[hsl(var(--primary))] bg-[hsl(var(--primary)/0.08)] shadow-sm',
                    !isActive  && 'border-[hsl(var(--border))] hover:border-[hsl(var(--primary)/0.5)] hover:bg-[hsl(var(--accent))]',
                    isDragging && 'opacity-30',
                  )}>
                  <button className="w-full text-left" onClick={() => { setActiveFloorId(floor.id); setSelectedRoomId(null); setSelectedDeviceId(null) }}>
                    <p className="text-xs font-semibold leading-tight pr-8 truncate">{floor.name}</p>
                    <p className="text-[10px] text-[hsl(var(--muted-foreground))] mt-0.5">
                      {roomCount} {roomCount === 1 ? 'room' : 'rooms'}
                    </p>
                  </button>
                  {isActive && (
                    <div className="absolute top-1.5 right-1.5 flex gap-0.5">
                      <button onPointerDown={e => e.stopPropagation()}
                        onClick={e => { e.stopPropagation(); setEditFloor(floor) }}
                        className="p-1 rounded hover:bg-[hsl(var(--accent))] text-[hsl(var(--muted-foreground))] transition-colors">
                        <Pencil size={10} />
                      </button>
                      <button onPointerDown={e => e.stopPropagation()}
                        onClick={e => { e.stopPropagation(); window.confirm(`Delete floor "${floor.name}"?`) && deleteFloor.mutate(floor.id) }}
                        className="p-1 rounded hover:bg-[hsl(var(--accent))] text-red-400 transition-colors">
                        <Trash2 size={10} />
                      </button>
                    </div>
                  )}
                </div>
              </Fragment>
            )
          })}
          {/* Drop indicator at the very bottom */}
          {dropIndex === displayFloors.length && dragFloorId != null && (
            <div className="h-0.5 rounded-full bg-[hsl(var(--primary))] mx-1 shrink-0 pointer-events-none" />
          )}
        </div>

        <div className="p-2 border-t border-[hsl(var(--border))]">
          <button onClick={() => setShowAddFloor(true)}
            className="w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-xs
              text-[hsl(var(--muted-foreground))] border border-dashed border-[hsl(var(--border))]
              hover:border-[hsl(var(--primary))] hover:text-[hsl(var(--foreground))] hover:bg-[hsl(var(--accent))]
              transition-all">
            <Plus size={12} /> Add Floor
          </button>
        </div>
      </div>

      {/* ── Main area: toolbar + canvas + devices ── */}
      <div className="flex-1 flex flex-col overflow-hidden">

        {/* Toolbar */}
        <div className="shrink-0 flex items-center justify-between gap-2 px-4 py-2
          border-b border-[hsl(var(--border))] bg-[hsl(var(--card))]">
          <p className="text-sm font-semibold text-[hsl(var(--muted-foreground))]">
            {activeFloor?.name ?? 'No floor selected'}
          </p>
          <div className="flex items-center gap-2">
            {/* 2D / 3D segmented toggle */}
            <div className="flex items-center rounded-lg border border-[hsl(var(--border))] overflow-hidden">
              <button onClick={() => changeView('2d')}
                title="2D plan"
                className={cn('flex items-center gap-1 px-2.5 py-1.5 text-sm transition-colors',
                  viewMode === '2d'
                    ? 'bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))]'
                    : 'text-[hsl(var(--muted-foreground))] hover:bg-[hsl(var(--accent))]')}>
                <MapIcon size={13} /> 2D
              </button>
              <button onClick={() => changeView('3d')}
                title="3D dollhouse"
                className={cn('flex items-center gap-1 px-2.5 py-1.5 text-sm transition-colors',
                  viewMode === '3d'
                    ? 'bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))]'
                    : 'text-[hsl(var(--muted-foreground))] hover:bg-[hsl(var(--accent))]')}>
                <Box size={13} /> 3D
              </button>
            </div>

            {viewMode === '2d' && activeFloor && editMode && (
              <button onClick={() => setShowAddRoom(true)}
                className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm
                  bg-[hsl(var(--secondary))] hover:bg-[hsl(var(--accent))] transition-colors">
                <Plus size={13} /> Room
              </button>
            )}
            {viewMode === '2d' && activeFloor && (
              <button onClick={() => setEditMode(v => !v)}
                className={cn('flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm transition-colors',
                  editMode
                    ? 'bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))]'
                    : 'text-[hsl(var(--muted-foreground))] hover:bg-[hsl(var(--accent))]')}>
                <Pencil size={13} /> {editMode ? 'Done' : 'Edit'}
              </button>
            )}
          </div>
        </div>

        {/* Canvas + device panel */}
        <div className="flex-1 flex overflow-hidden">

          {/* Canvas column: plan on top, unplaced-room shelf below */}
          <div className="flex-1 flex flex-col overflow-hidden">
            {viewMode === '3d' ? (
              <div className="flex-1 overflow-hidden">
                <FloorPlan3D floor={activeFloor ?? null} rooms={rooms} devices={devices} />
              </div>
            ) : (
            <div className="flex-1 flex items-center justify-center bg-[hsl(var(--background))] p-6 overflow-hidden">
              {!activeFloor ? (
                <div className="flex flex-col items-center gap-3 text-[hsl(var(--muted-foreground))]">
                  <Home size={48} className="opacity-20" />
                  <p className="text-sm">No floors yet</p>
                  <button onClick={() => setShowAddFloor(true)}
                    className="text-sm text-[hsl(var(--primary))] underline underline-offset-2">
                    Add a floor (e.g. Erdgeschoss)
                  </button>
                </div>
              ) : (
                <div
                  ref={canvasRef}
                  className="relative w-full rounded-2xl border-2 border-[hsl(var(--border))] shadow-inner bg-[hsl(var(--card))]"
                  style={{
                    aspectRatio: '16 / 9',
                    maxHeight: '100%',
                    maxWidth: '100%',
                    backgroundImage: `
                      linear-gradient(hsl(var(--border) / 0.4) 1px, transparent 1px),
                      linear-gradient(90deg, hsl(var(--border) / 0.4) 1px, transparent 1px),
                      linear-gradient(hsl(var(--border) / 0.1) 1px, transparent 1px),
                      linear-gradient(90deg, hsl(var(--border) / 0.1) 1px, transparent 1px)
                    `,
                    backgroundSize: '25% 33.33%, 25% 33.33%, 5% 5.56%, 5% 5.56%',
                  }}
                  onDragOver={e => e.preventDefault()}
                  onDrop={handleCanvasDrop}
                  onClick={() => { setSelectedRoomId(null); setSelectedDeviceId(null) }}
                >
                  {/* Rooms (L-extension rendered first so primary rect sits on top) */}
                  {placedRooms.map((room, idx) => (
                    <Fragment key={room.id}>
                      {room.planX2 != null && (
                        <LExtensionRect
                          room={room}
                          colorClass={ROOM_COLORS[idx % ROOM_COLORS.length]}
                          editMode={editMode}
                          canvasRef={canvasRef}
                          otherRooms={placedRooms.filter(r => r.id !== room.id)}
                          onExtensionMoved={handleLExtensionMoved}
                          onRemove={() => handleToggleL(room)}
                        />
                      )}
                      <RoomRect
                        room={room}
                        colorClass={ROOM_COLORS[idx % ROOM_COLORS.length]}
                        devices={devices.filter(d => d.room === room.name && d.roomX != null && d.roomY != null)}
                        editMode={editMode}
                        selected={selectedRoomId === room.id}
                        selectedDeviceId={selectedDeviceId}
                        canvasRef={canvasRef}
                        otherRooms={placedRooms.filter(r => r.id !== room.id)}
                        onRoomClick={() => { setSelectedRoomId(r => r === room.id ? null : room.id); setSelectedDeviceId(null) }}
                        onDeviceSelect={id => { setSelectedDeviceId(id); setSelectedRoomId(room.id) }}
                        onDeviceMoved={handleDeviceMoved}
                        onRoomMoved={handleRoomMoved}
                        onDrop={handleRoomDrop}
                        onEdit={() => setEditRoom(room)}
                        onDelete={() => window.confirm(`Delete room "${room.name}"?`) && deleteRoom.mutate(room.id)}
                        onToggleL={() => handleToggleL(room)}
                      />
                    </Fragment>
                  ))}

                  {/* Empty canvas hint */}
                  {placedRooms.length === 0 && (
                    <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                      <p className="text-sm text-[hsl(var(--muted-foreground))] opacity-40 text-center">
                        {editMode ? 'Click "+ Room" to add a room' : 'Enable Edit to start arranging rooms'}
                      </p>
                    </div>
                  )}
                </div>
              )}
            </div>
            )}

            {/* Quick-pick preset rooms — shown in edit mode below the canvas.
                Only presets not yet on this floor are offered. */}
            {viewMode === '2d' && editMode && activeFloor && (() => {
              const floorRoomNames = new Set(floorRooms.map(r => r.name))
              const available = ROOM_PRESETS.filter(p => !floorRoomNames.has(p.name))
              if (available.length === 0) return null
              return (
                <div className="shrink-0 flex flex-wrap gap-2 px-4 py-2 border-t border-[hsl(var(--border))] bg-[hsl(var(--card)/0.6)]">
                  <span className="text-[10px] font-semibold text-[hsl(var(--muted-foreground))] self-center mr-1 uppercase tracking-wide">
                    Quick add:
                  </span>
                  {available.map(preset => (
                    <button key={preset.name}
                      onClick={() => {
                        const slot = nextFreeSlot()
                        createRoom.mutate({
                          name: preset.name, icon: preset.icon,
                          floorId: activeFloor.id,
                          planX: slot.x, planY: slot.y, planW: 22, planH: 28,
                        })
                      }}
                      className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs
                        bg-[hsl(var(--card))] border border-[hsl(var(--border))]
                        hover:border-[hsl(var(--primary))] hover:bg-[hsl(var(--accent))]
                        transition-colors shadow-sm">
                      <span>{preset.icon}</span>
                      <span>{preset.name}</span>
                      <Plus size={10} className="text-[hsl(var(--muted-foreground))]" />
                    </button>
                  ))}
                </div>
              )
            })()}
          </div>

          {/* Device panel */}
          <DevicePanel devices={panelDevices} rooms={rooms} />
        </div>
      </div>

      {/* ── Dialogs ── */}
      {showAddFloor && (
        <SimpleDialog title="New Floor" placeholder="e.g. Erdgeschoss"
          onSave={name => createFloor.mutate(name)} onClose={() => setShowAddFloor(false)} />
      )}
      {editFloor && (
        <SimpleDialog title="Rename Floor" placeholder="Floor name" initial={editFloor.name}
          onSave={name => updateFloorName.mutate({ id: editFloor.id, name })} onClose={() => setEditFloor(null)} />
      )}
      {showAddRoom && (
        <RoomDialog
          onSave={(name, icon) => {
            const slot = nextFreeSlot()
            createRoom.mutate({ name, icon, floorId: activeFloor?.id ?? undefined, planX: slot.x, planY: slot.y, planW: 22, planH: 28 })
          }}
          onClose={() => setShowAddRoom(false)} />
      )}
      {editRoom && (
        <RoomDialog initial={editRoom}
          onSave={(name, icon) => updateRoom.mutate({ id: editRoom.id, name, icon })}
          onClose={() => setEditRoom(null)} />
      )}
    </div>
  )
}
