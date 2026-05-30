/**
 * Floor Plan — interactive bird's-eye view of the home, one floor at a time.
 *
 * ┌─ Toolbar ─────────────────────────────────────────────────────────────────┐
 * │  Floor tabs  (one per Floor entity, sorted by sortOrder)   [Edit] [Save]  │
 * └─ Canvas ─────────────────────────── Sidebar ──────────────────────────────┘
 * │  Rooms on this floor drawn as         Edit: room list + resize + L-shape  │
 * │  coloured rects.  L-rooms = 2 rects.  View: click room → device list      │
 * └───────────────────────────────────────────────────────────────────────────┘
 *
 * Data model:
 *   Room.floorId  — which floor the room belongs to (null = unassigned)
 *   Room.planX/Y/W/H    — primary rect, 0–100% of canvas
 *   Room.planX2/Y2/W2/H2 — optional second rect (L-extension), same %
 *
 * Rooms without a floorId appear under the "Unassigned" tab only.
 */

import { useState, useRef, useCallback, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil, Save, X, Plus, Minus, Map } from 'lucide-react'
import { api } from '@/api/client'
import type { Room, Device } from '@/api/client'
import { useLiveStore } from '@/store/liveStore'
import { cn } from '@/lib/utils'
import { toast } from 'sonner'

// ── Colour palette ────────────────────────────────────────────────────────────

const PALETTE = [
  { bg: 'hsl(217 91% 60% / 0.18)', border: 'hsl(217 91% 60% / 0.55)' },
  { bg: 'hsl(142 71% 45% / 0.18)', border: 'hsl(142 71% 45% / 0.55)' },
  { bg: 'hsl(38  92% 50% / 0.18)', border: 'hsl(38  92% 50% / 0.55)' },
  { bg: 'hsl(280 65% 60% / 0.18)', border: 'hsl(280 65% 60% / 0.55)' },
  { bg: 'hsl(0   84% 60% / 0.18)', border: 'hsl(0   84% 60% / 0.55)' },
  { bg: 'hsl(186 94% 41% / 0.18)', border: 'hsl(186 94% 41% / 0.55)' },
  { bg: 'hsl(330 82% 60% / 0.18)', border: 'hsl(330 82% 60% / 0.55)' },
  { bg: 'hsl(48  96% 53% / 0.18)', border: 'hsl(48  96% 53% / 0.55)' },
]
function roomColor(idx: number) { return PALETTE[idx % PALETTE.length] }

// ── Helpers ───────────────────────────────────────────────────────────────────

function clamp(v: number, lo: number, hi: number) { return Math.max(lo, Math.min(hi, v)) }

function roomTemp(room: Room, devices: Device[], live: Record<string, { value: number }>): number | null {
  const r = live[`${room.name}/temperature`]
  if (r) return r.value
  const rad = devices.find(d => d.room === room.name && d.type === 'HOMEMATIC_RADIATOR')
  if (!rad?.lastStateJson) return null
  try {
    const s = JSON.parse(rad.lastStateJson)
    return s.actualTemperature ?? s.setPointTemperature ?? null
  } catch { return null }
}

/** Returns true if rect A overlaps rect B (both in % coords). */
function rectsOverlap(
  ax: number, ay: number, aw: number, ah: number,
  bx: number, by: number, bw: number, bh: number,
): boolean {
  return !(ax + aw <= bx || bx + bw <= ax || ay + ah <= by || by + bh <= ay)
}

/** IDs of rooms that overlap any other room on this canvas. */
function computeOverlaps(rooms: Room[]): Set<number> {
  const placed = rooms.filter(r => r.planX != null)
  const overlapping = new Set<number>()

  // Collect all rects for each room (primary + optional secondary)
  const rects = placed.map(r => {
    const segs: { x: number; y: number; w: number; h: number }[] = [
      { x: r.planX!, y: r.planY!, w: r.planW!, h: r.planH! },
    ]
    if (r.planX2 != null) segs.push({ x: r.planX2, y: r.planY2!, w: r.planW2!, h: r.planH2! })
    return { id: r.id, segs }
  })

  for (let i = 0; i < rects.length; i++) {
    for (let j = i + 1; j < rects.length; j++) {
      for (const sa of rects[i].segs) {
        for (const sb of rects[j].segs) {
          if (rectsOverlap(sa.x, sa.y, sa.w, sa.h, sb.x, sb.y, sb.w, sb.h)) {
            overlapping.add(rects[i].id)
            overlapping.add(rects[j].id)
          }
        }
      }
    }
  }
  return overlapping
}

// ── Drag state ────────────────────────────────────────────────────────────────

interface DragState {
  roomId: number
  segment: 'primary' | 'secondary'
  startMouseX: number
  startMouseY: number
  startX: number
  startY: number
}

// ── Room shape ────────────────────────────────────────────────────────────────

function RoomShape({
  room, colorIdx, devices, editMode, selected, overlapping, onSelect, onDragStart, live,
}: {
  room: Room
  colorIdx: number
  devices: Device[]
  editMode: boolean
  selected: boolean
  overlapping: boolean
  onSelect: () => void
  onDragStart: (seg: 'primary' | 'secondary', e: React.PointerEvent) => void
  live: Record<string, { value: number }>
}) {
  if (room.planX == null) return null

  const { bg, border } = roomColor(colorIdx)
  const temp = roomTemp(room, devices, live)
  const online = devices.filter(d => d.room === room.name && d.online).length

  const borderColor = selected
    ? 'hsl(var(--primary))'
    : overlapping
    ? 'hsl(0 84% 60% / 0.9)'
    : border

  const rectStyle = (x: number, y: number, w: number, h: number): React.CSSProperties => ({
    position: 'absolute',
    left:     `${x}%`,
    top:      `${y}%`,
    width:    `${w}%`,
    height:   `${h}%`,
    background: overlapping ? 'hsl(0 84% 60% / 0.12)' : bg,
    border:   `2px solid ${borderColor}`,
    boxSizing: 'border-box',
    cursor:   editMode ? 'move' : 'pointer',
    borderRadius: '4px',
    userSelect: 'none',
  })

  return (
    <>
      {/* Primary rect — carries the room label */}
      <div
        style={rectStyle(room.planX, room.planY!, room.planW!, room.planH!)}
        onClick={e => { e.stopPropagation(); onSelect() }}
        onPointerDown={e => editMode && onDragStart('primary', e)}
      >
        <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none p-1">
          <span className="text-base leading-none">{room.icon}</span>
          <span className="text-[10px] font-semibold mt-0.5 text-center leading-tight opacity-90">
            {room.name}
          </span>
          {temp != null && (
            <span className="text-[10px] font-bold mt-0.5 opacity-70">{temp.toFixed(1)}°</span>
          )}
          {online > 0 && (
            <span className="text-[9px] opacity-50 mt-0.5">
              {online} device{online !== 1 ? 's' : ''}
            </span>
          )}
        </div>
      </div>

      {/* Secondary rect (L-extension) — drag handle only, no label */}
      {room.planX2 != null && (
        <div
          style={{
            ...rectStyle(room.planX2, room.planY2!, room.planW2!, room.planH2!),
            // Slightly darker so it's visually distinct from the primary
            opacity: 0.85,
          }}
          onClick={e => { e.stopPropagation(); onSelect() }}
          onPointerDown={e => editMode && onDragStart('secondary', e)}
        >
          {editMode && (
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
              <span className="text-[9px] opacity-40 font-medium">L</span>
            </div>
          )}
        </div>
      )}
    </>
  )
}

// ── Number input ──────────────────────────────────────────────────────────────

function NumField({ label, value, min = 1, max = 99, onChange }: {
  label: string; value: number | null; min?: number; max?: number
  onChange: (v: number) => void
}) {
  if (value == null) return null
  return (
    <label className="flex items-center gap-2 text-xs">
      <span className="w-6 text-[hsl(var(--muted-foreground))] shrink-0">{label}</span>
      <input
        type="number" min={min} max={max} step={1}
        value={Math.round(value)}
        onChange={e => onChange(Number(e.target.value))}
        className="w-16 px-2 py-1 rounded border border-[hsl(var(--border))] bg-[hsl(var(--background))] text-xs"
      />
      <span className="text-[hsl(var(--muted-foreground))]">%</span>
    </label>
  )
}

// ── Virtual floor tab ─────────────────────────────────────────────────────────

const UNASSIGNED_ID = -1

// ── Page ──────────────────────────────────────────────────────────────────────

export function FloorPlanPage() {
  const qc = useQueryClient()

  const { data: floors   = [] } = useQuery({ queryKey: ['floors'],  queryFn: api.floors.list })
  const { data: rooms    = [] } = useQuery({ queryKey: ['rooms'],   queryFn: api.rooms.list  })
  const { data: devices  = [] } = useQuery({ queryKey: ['devices'], queryFn: api.devices.list })
  const live = useLiveStore(s => s.sensorReadings)

  const sortedFloors = [...floors].sort((a, b) => a.sortOrder - b.sortOrder)
  const hasUnassigned = rooms.some(r => r.floorId == null)

  // Active floor tab — default to first floor (or unassigned if none)
  const [activeFloorId, setActiveFloorId] = useState<number>(UNASSIGNED_ID)
  useEffect(() => {
    if (sortedFloors.length > 0 && activeFloorId === UNASSIGNED_ID) {
      setActiveFloorId(sortedFloors[0].id)
    }
  }, [sortedFloors.length]) // eslint-disable-line react-hooks/exhaustive-deps

  const [editMode,   setEditMode]   = useState(false)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [localRooms, setLocalRooms] = useState<Room[] | null>(null)
  const canvasRef = useRef<HTMLDivElement>(null)
  const dragRef   = useRef<DragState | null>(null)

  const allRooms     = localRooms ?? rooms
  // Rooms shown on the active floor's canvas
  const floorRooms   = allRooms.filter(r =>
    activeFloorId === UNASSIGNED_ID ? r.floorId == null : r.floorId === activeFloorId
  )

  const overlaps = editMode ? computeOverlaps(floorRooms) : new Set<number>()
  const hasOverlap = overlaps.size > 0

  // ── Persistence ──────────────────────────────────────────────────────────────

  const saveMutation = useMutation({
    mutationFn: async (rooms: Room[]) => {
      const placed = rooms.filter(r => r.planX != null)
      await Promise.all(placed.map(r => api.rooms.update(r.id, {
        planX: r.planX, planY: r.planY, planW: r.planW, planH: r.planH,
        planX2: r.planX2, planY2: r.planY2, planW2: r.planW2, planH2: r.planH2,
      })))
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['rooms'] })
      setLocalRooms(null)
      setEditMode(false)
      setSelectedId(null)
      toast.success('Floor plan saved')
    },
    onError: () => toast.error('Failed to save'),
  })

  function enterEdit() {
    setLocalRooms(allRooms.map(r => ({ ...r })))
    setEditMode(true)
  }

  function cancelEdit() {
    setLocalRooms(null)
    setEditMode(false)
    setSelectedId(null)
  }

  // ── Drag ──────────────────────────────────────────────────────────────────

  const patchRoom = useCallback((id: number, patch: Partial<Room>) => {
    setLocalRooms(prev => prev?.map(r => r.id === id ? { ...r, ...patch } : r) ?? null)
  }, [])

  function onDragStart(room: Room, seg: 'primary' | 'secondary', e: React.PointerEvent) {
    if (!editMode) return
    e.currentTarget.setPointerCapture(e.pointerId)
    dragRef.current = {
      roomId: room.id, segment: seg,
      startMouseX: e.clientX, startMouseY: e.clientY,
      startX: seg === 'primary' ? room.planX! : room.planX2!,
      startY: seg === 'primary' ? room.planY! : room.planY2!,
    }
  }

  function onPointerMove(e: React.PointerEvent) {
    const drag = dragRef.current
    if (!drag || !canvasRef.current) return
    const rect = canvasRef.current.getBoundingClientRect()
    const dx = ((e.clientX - drag.startMouseX) / rect.width)  * 100
    const dy = ((e.clientY - drag.startMouseY) / rect.height) * 100
    const room = floorRooms.find(r => r.id === drag.roomId)
    if (!room) return

    if (drag.segment === 'primary') {
      const w = room.planW ?? 20, h = room.planH ?? 15
      patchRoom(room.id, {
        planX: clamp(drag.startX + dx, 0, 100 - w),
        planY: clamp(drag.startY + dy, 0, 100 - h),
      })
    } else {
      const w = room.planW2 ?? 15, h = room.planH2 ?? 15
      patchRoom(room.id, {
        planX2: clamp(drag.startX + dx, 0, 100 - w),
        planY2: clamp(drag.startY + dy, 0, 100 - h),
      })
    }
  }

  function onPointerUp() { dragRef.current = null }

  // ── Add / remove from plan ────────────────────────────────────────────────

  function addToPlan(room: Room) {
    // Place in center, assign to active floor if needed
    patchRoom(room.id, {
      planX: 15, planY: 15, planW: 25, planH: 20,
      // If the room has no floorId yet, assign it to the active floor
      ...(room.floorId == null && activeFloorId !== UNASSIGNED_ID
        ? { floorId: activeFloorId } : {}),
    })
    setSelectedId(room.id)  // auto-select so controls appear immediately
  }

  function removeFromPlan(room: Room) {
    patchRoom(room.id, {
      planX: null, planY: null, planW: null, planH: null,
      planX2: null, planY2: null, planW2: null, planH2: null,
    })
    if (selectedId === room.id) setSelectedId(null)
  }

  // ── L-extension ───────────────────────────────────────────────────────────

  function addLExtension(room: Room) {
    // Place to the right of the primary rect by default
    const x = clamp((room.planX ?? 10) + (room.planW ?? 25), 0, 80)
    patchRoom(room.id, { planX2: x, planY2: room.planY ?? 10, planW2: 15, planH2: room.planH ?? 20 })
  }

  function removeLExtension(room: Room) {
    patchRoom(room.id, { planX2: null, planY2: null, planW2: null, planH2: null })
  }

  // ── Render ────────────────────────────────────────────────────────────────

  const selected = selectedId != null ? (floorRooms.find(r => r.id === selectedId) ?? null) : null

  const tabBase = 'px-3 py-1.5 text-xs font-medium rounded-md transition-colors cursor-pointer whitespace-nowrap'
  const tabActive = 'bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))]'
  const tabInactive = 'text-[hsl(var(--muted-foreground))] hover:bg-[hsl(var(--accent))] hover:text-[hsl(var(--foreground))]'

  return (
    <div className="flex flex-col h-full">
      {/* ── Toolbar ──────────────────────────────────────────────────── */}
      <div className="flex items-center gap-3 px-4 py-2.5 border-b border-[hsl(var(--border))] bg-[hsl(var(--card))] shrink-0 overflow-x-auto">
        <span className="font-semibold text-sm shrink-0 mr-1">Floor Plan</span>

        {/* Floor tabs */}
        <div className="flex items-center gap-1 flex-1 overflow-x-auto">
          {sortedFloors.map(f => (
            <button
              key={f.id}
              type="button"
              onClick={() => { setActiveFloorId(f.id); setSelectedId(null) }}
              className={cn(tabBase, activeFloorId === f.id ? tabActive : tabInactive)}
            >
              {f.name}
            </button>
          ))}
          {hasUnassigned && (
            <button
              type="button"
              onClick={() => { setActiveFloorId(UNASSIGNED_ID); setSelectedId(null) }}
              className={cn(tabBase, activeFloorId === UNASSIGNED_ID ? tabActive : tabInactive)}
            >
              Unassigned
            </button>
          )}
          {sortedFloors.length === 0 && !hasUnassigned && (
            <span className="text-xs text-[hsl(var(--muted-foreground))]">
              No floors — create them in Rooms settings
            </span>
          )}
        </div>

        {/* Overlap warning */}
        {editMode && hasOverlap && (
          <span className="text-xs text-red-400 shrink-0">⚠ Rooms overlap</span>
        )}

        {/* Edit / Save / Cancel */}
        <div className="flex items-center gap-2 shrink-0">
          {editMode ? (
            <>
              <button onClick={cancelEdit}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm text-[hsl(var(--muted-foreground))] hover:bg-[hsl(var(--accent))] transition-colors">
                <X size={14} /> Cancel
              </button>
              <button
                onClick={() => saveMutation.mutate(allRooms)}
                disabled={saveMutation.isPending}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] hover:opacity-90 transition-opacity disabled:opacity-50">
                <Save size={14} /> {saveMutation.isPending ? 'Saving…' : 'Save'}
              </button>
            </>
          ) : (
            <button onClick={enterEdit}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm text-[hsl(var(--muted-foreground))] hover:bg-[hsl(var(--accent))] transition-colors">
              <Pencil size={14} /> Edit
            </button>
          )}
        </div>
      </div>

      {/* ── Body ─────────────────────────────────────────────────────── */}
      <div className="flex flex-1 min-h-0 overflow-hidden">

        {/* Canvas */}
        <div className="flex-1 p-4 overflow-hidden">
          <div
            ref={canvasRef}
            className={cn(
              'relative w-full h-full rounded-xl border-2 bg-[hsl(var(--muted)/0.25)] overflow-hidden',
              editMode ? 'border-[hsl(var(--primary)/0.35)]' : 'border-[hsl(var(--border))]'
            )}
            onClick={() => setSelectedId(null)}
            onPointerMove={onPointerMove}
            onPointerUp={onPointerUp}
          >
            {floorRooms.map((room, i) => (
              <RoomShape
                key={room.id}
                room={room}
                colorIdx={i}
                devices={devices}
                editMode={editMode}
                selected={selectedId === room.id}
                overlapping={overlaps.has(room.id)}
                live={live}
                onSelect={() => setSelectedId(room.id)}
                onDragStart={(seg, e) => onDragStart(room, seg, e)}
              />
            ))}

            {floorRooms.every(r => r.planX == null) && (
              <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-[hsl(var(--muted-foreground))]">
                <Map size={40} className="opacity-20" />
                <p className="text-sm">
                  {editMode
                    ? 'Click the + next to a room in the panel to place it here'
                    : 'No rooms placed — click Edit to start'}
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Side panel */}
        <div className="w-60 shrink-0 border-l border-[hsl(var(--border))] bg-[hsl(var(--card))] flex flex-col overflow-y-auto">
          {editMode ? (
            <EditPanel
              floorRooms={floorRooms}
              selectedId={selectedId}
              setSelectedId={setSelectedId}
              patchRoom={patchRoom}
              addToPlan={addToPlan}
              removeFromPlan={removeFromPlan}
              addLExtension={addLExtension}
              removeLExtension={removeLExtension}
            />
          ) : (
            <ViewPanel
              selected={selected}
              devices={devices}
              floorRooms={floorRooms}
              setSelectedId={setSelectedId}
            />
          )}
        </div>
      </div>
    </div>
  )
}

// ── Edit panel ────────────────────────────────────────────────────────────────

function EditPanel({
  floorRooms, selectedId, setSelectedId, patchRoom,
  addToPlan, removeFromPlan, addLExtension, removeLExtension,
}: {
  floorRooms: Room[]
  selectedId: number | null
  setSelectedId: (id: number | null) => void
  patchRoom: (id: number, patch: Partial<Room>) => void
  addToPlan: (r: Room) => void
  removeFromPlan: (r: Room) => void
  addLExtension: (r: Room) => void
  removeLExtension: (r: Room) => void
}) {
  return (
    <div className="p-3 space-y-1">
      <p className="text-[10px] font-semibold uppercase tracking-widest text-[hsl(var(--muted-foreground))] px-1 mb-2">
        Rooms on this floor
      </p>

      {floorRooms.length === 0 && (
        <p className="text-xs text-[hsl(var(--muted-foreground))] italic px-1">No rooms assigned to this floor.</p>
      )}

      {floorRooms.map(room => {
        const placed = room.planX != null
        const hasL   = room.planX2 != null
        const isSel  = selectedId === room.id

        return (
          <div key={room.id}
            className={cn(
              'rounded-lg border px-3 py-2 text-sm cursor-pointer transition-colors',
              isSel
                ? 'border-[hsl(var(--primary))] bg-[hsl(var(--primary)/0.08)]'
                : 'border-[hsl(var(--border))] hover:bg-[hsl(var(--accent))]'
            )}
            onClick={() => setSelectedId(isSel ? null : room.id)}
          >
            {/* Room header row */}
            <div className="flex items-center gap-2">
              <span className="shrink-0">{room.icon}</span>
              <span className="flex-1 truncate font-medium text-xs">{room.name}</span>
              <button
                type="button"
                onClick={e => {
                  e.stopPropagation()
                  placed ? removeFromPlan(room) : addToPlan(room)
                }}
                title={placed ? 'Remove from plan' : 'Place on canvas'}
                className={cn(
                  'shrink-0 p-0.5 rounded transition-colors',
                  placed
                    ? 'text-red-400 hover:bg-red-400/10'
                    : 'text-[hsl(var(--primary))] hover:bg-[hsl(var(--primary)/0.1)]'
                )}
              >
                {placed ? <Minus size={14} /> : <Plus size={14} />}
              </button>
            </div>

            {/* Resize + L-shape controls — only when placed & selected */}
            {placed && isSel && (
              <div className="mt-3 space-y-3 border-t border-[hsl(var(--border))] pt-3">

                {/* Primary rect */}
                <div className="space-y-1.5">
                  <p className="text-[10px] font-semibold text-[hsl(var(--muted-foreground))]">PRIMARY RECT</p>
                  <NumField label="W" value={room.planW} onChange={v => patchRoom(room.id, { planW: v })} />
                  <NumField label="H" value={room.planH} onChange={v => patchRoom(room.id, { planH: v })} />
                </div>

                {/* L-extension */}
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <p className="text-[10px] font-semibold text-[hsl(var(--muted-foreground))]">L-EXTENSION</p>
                    <button
                      type="button"
                      onClick={e => {
                        e.stopPropagation()
                        hasL ? removeLExtension(room) : addLExtension(room)
                      }}
                      className={cn(
                        'text-[10px] px-2 py-0.5 rounded font-semibold transition-colors',
                        hasL
                          ? 'text-red-400 bg-red-400/10 hover:bg-red-400/20'
                          : 'text-[hsl(var(--primary))] bg-[hsl(var(--primary)/0.1)] hover:bg-[hsl(var(--primary)/0.2)]'
                      )}
                    >
                      {hasL ? '− Remove' : '+ Add'}
                    </button>
                  </div>
                  {hasL && (
                    <>
                      <p className="text-[9px] text-[hsl(var(--muted-foreground))] italic">
                        Drag the "L" rect on the canvas to position it.
                      </p>
                      <NumField label="W" value={room.planW2 ?? null} onChange={v => patchRoom(room.id, { planW2: v })} />
                      <NumField label="H" value={room.planH2 ?? null} onChange={v => patchRoom(room.id, { planH2: v })} />
                    </>
                  )}
                </div>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

// ── View panel ────────────────────────────────────────────────────────────────

function ViewPanel({ selected, devices, floorRooms, setSelectedId }: {
  selected: Room | null
  devices: Device[]
  floorRooms: Room[]
  setSelectedId: (id: number | null) => void
}) {
  if (selected) {
    const roomDevices = devices.filter(d => d.room === selected.name)
    return (
      <div className="p-3">
        <div className="flex items-center justify-between mb-3">
          <p className="font-semibold text-sm">{selected.icon} {selected.name}</p>
          <button
            onClick={() => setSelectedId(null)}
            className="p-1 rounded hover:bg-[hsl(var(--accent))] text-[hsl(var(--muted-foreground))] transition-colors"
          >
            <X size={13} />
          </button>
        </div>
        {roomDevices.length === 0 ? (
          <p className="text-xs text-[hsl(var(--muted-foreground))]">No devices in this room.</p>
        ) : (
          <div className="space-y-2">
            {roomDevices.map(d => (
              <div key={d.id} className="flex items-center gap-2 text-sm">
                <span className={cn('w-2 h-2 rounded-full shrink-0',
                  d.online ? 'bg-green-400' : 'bg-[hsl(var(--muted-foreground))]'
                )} />
                <span className="truncate text-xs">{d.name}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="p-3">
      <p className="text-[10px] font-semibold uppercase tracking-widest text-[hsl(var(--muted-foreground))] px-1 mb-2">
        Rooms
      </p>
      {floorRooms.filter(r => r.planX != null).length === 0 ? (
        <p className="text-xs text-[hsl(var(--muted-foreground))] italic">No rooms placed yet.</p>
      ) : (
        <div className="space-y-1">
          {floorRooms.filter(r => r.planX != null).map(r => (
            <button
              key={r.id}
              type="button"
              onClick={() => setSelectedId(r.id)}
              className="w-full flex items-center gap-2 px-2 py-1.5 rounded-lg text-xs hover:bg-[hsl(var(--accent))] transition-colors text-left"
            >
              <span>{r.icon}</span>
              <span className="truncate">{r.name}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
