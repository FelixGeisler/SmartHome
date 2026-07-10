import type { Layout } from 'react-grid-layout'
import type { DevicePlacement, Room, RoomBox } from './api/rooms'

/** Grid geometry shared with the grid config in RoomsFloorPlan. */
export const GRID_COLS = 12
const DEFAULT_W = 6
const DEFAULT_H = 8
const MIN_W = 3
const MIN_H = 5

/** Flows rooms left to right into a grid from the given top row. */
function flow(rooms: Room[], startY: number): RoomBox[] {
  const boxes: RoomBox[] = []
  let x = 0
  let y = startY
  for (const room of rooms) {
    if (x + DEFAULT_W > GRID_COLS) {
      x = 0
      y += DEFAULT_H
    }
    boxes.push({ roomId: room.id, x, y, w: DEFAULT_W, h: DEFAULT_H })
    x += DEFAULT_W
  }
  return boxes
}

/** Default grid before the floor plan has been arranged. */
export function placeAllRooms(rooms: Room[]): RoomBox[] {
  return flow(rooms, 0)
}

/** Keeps only boxes whose room still exists. */
export function curate(rooms: Room[], boxes: RoomBox[]): RoomBox[] {
  const live = new Set(rooms.map((room) => room.id))
  return boxes.filter((box) => live.has(box.roomId))
}

/**
 * A box for every live room, since a room exists independently of the floor plan: keep saved boxes,
 * append defaults for unplaced rooms, drop boxes for deleted rooms.
 */
export function displayBoxes(rooms: Room[], saved: RoomBox[] | null): RoomBox[] {
  const kept = curate(rooms, saved ?? [])
  const placed = new Set(kept.map((box) => box.roomId))
  const missing = rooms.filter((room) => !placed.has(room.id))
  const bottom = kept.reduce((max, box) => Math.max(max, box.y + box.h), 0)
  return [...kept, ...flow(missing, bottom)]
}

/** Appends a box for a room at the bottom, unless already placed. */
export function addRoomBox(boxes: RoomBox[], room: Room): RoomBox[] {
  if (boxes.some((box) => box.roomId === room.id)) {
    return boxes
  }
  const y = boxes.reduce((max, box) => Math.max(max, box.y + box.h), 0)
  return [...boxes, { roomId: room.id, x: 0, y, w: DEFAULT_W, h: DEFAULT_H }]
}

/** Removes a room's box. */
export function removeRoomBox(boxes: RoomBox[], roomId: number): RoomBox[] {
  return boxes.filter((box) => box.roomId !== roomId)
}

/** Maps boxes to a react-grid-layout layout. */
export function toGridLayout(boxes: RoomBox[]): Layout {
  return boxes.map((box) => ({
    i: String(box.roomId),
    x: box.x,
    y: box.y,
    w: box.w,
    h: box.h,
    minW: MIN_W,
    minH: MIN_H,
  }))
}

/** Maps a react-grid-layout layout back to boxes. */
export function fromGridLayout(layout: Layout): RoomBox[] {
  return layout.map((item) => ({
    roomId: Number(item.i),
    x: item.x,
    y: item.y,
    w: item.w,
    h: item.h,
  }))
}

// Device placement geometry (a device icon's spot within its room).

const SLOT_COLS = 4

/** Clamps a fraction into [0, 1]; NaN maps to 0.5 so a corrupt value still renders. */
export function clampFraction(n: number): number {
  if (Number.isNaN(n)) {
    return 0.5
  }
  return Math.min(1, Math.max(0, n))
}

/** Rounds a fraction to three decimals for saving. */
export function roundFraction(n: number): number {
  return Math.round(n * 1000) / 1000
}

/**
 * Maps a client point to a 0..1 fraction of a rect. A zero-size rect (as jsdom reports) yields the
 * center rather than NaN.
 */
export function pointToFraction(
  point: { x: number; y: number },
  rect: { left: number; top: number; width: number; height: number },
): { fx: number; fy: number } {
  const fx = rect.width === 0 ? 0.5 : (point.x - rect.left) / rect.width
  const fy = rect.height === 0 ? 0.5 : (point.y - rect.top) / rect.height
  return { fx: clampFraction(fx), fy: clampFraction(fy) }
}

/** A deterministic default slot for the i-th unplaced device. */
export function defaultSlot(index: number): { fx: number; fy: number } {
  const col = index % SLOT_COLS
  const row = Math.floor(index / SLOT_COLS) % SLOT_COLS
  return { fx: (col + 0.5) / SLOT_COLS, fy: (row + 0.5) / SLOT_COLS }
}

/**
 * One placement per room device: keep and clamp a saved one (first wins on a repeated id), else its
 * default slot. Output order matches roomDevices, so a default slot is stable for a room.
 */
export function reconcilePlacements(
  roomDevices: { id: number }[],
  saved: DevicePlacement[],
): DevicePlacement[] {
  const byId = new Map<number, DevicePlacement>()
  for (const placement of saved) {
    if (!byId.has(placement.deviceId)) {
      byId.set(placement.deviceId, placement)
    }
  }
  return roomDevices.map((device, index) => {
    const existing = byId.get(device.id)
    if (existing !== undefined) {
      return {
        deviceId: device.id,
        fx: clampFraction(existing.fx),
        fy: clampFraction(existing.fy),
      }
    }
    const slot = defaultSlot(index)
    return { deviceId: device.id, fx: slot.fx, fy: slot.fy }
  })
}

/**
 * Every device's reconciled position, rounded. Storing an explicit position per device (rather than
 * omitting one left at a default slot, whose default depends on device index) keeps a saved layout
 * stable, so removing or reassigning one device never shifts another device's icon.
 */
export function placementsToSave(
  roomDevices: { id: number }[],
  saved: DevicePlacement[],
): DevicePlacement[] {
  return reconcilePlacements(roomDevices, saved).map((placement) => ({
    deviceId: placement.deviceId,
    fx: roundFraction(placement.fx),
    fy: roundFraction(placement.fy),
  }))
}
