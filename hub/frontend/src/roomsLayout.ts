import type { Layout } from 'react-grid-layout'
import type { DevicePlacement, Room, RoomBox } from './api/rooms'

/** Grid geometry shared with the grid config in RoomsFloorPlan. */
export const GRID_COLS = 12
/** Default placement for a newly added room box. Rooms are wider and taller than device cards,
 * since a box holds a name, aggregated readings, and its devices. */
const DEFAULT_W = 6
const DEFAULT_H = 8
/** Smallest a box may be resized to, so a room never collapses too small to hold its contents. */
const MIN_W = 3
const MIN_H = 5

/** Places rooms in a tidy grid, flowing left to right from the given top row. */
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

/** Places every room in a tidy grid; the default view before the floor plan has been arranged. */
export function placeAllRooms(rooms: Room[]): RoomBox[] {
  return flow(rooms, 0)
}

/** Keeps only the boxes whose room still exists, preserving their saved placement. */
export function curate(rooms: Room[], boxes: RoomBox[]): RoomBox[] {
  const live = new Set(rooms.map((room) => room.id))
  return boxes.filter((box) => live.has(box.roomId))
}

/**
 * The boxes to show for a committed layout. Unlike the dashboard (where a card exists only because
 * it was added), a room exists independently of the floor plan, so every room must appear: keep the
 * saved box for a room that has one, and append a default box for any room without one (a room added
 * after the layout was saved still shows up). Boxes for deleted rooms are dropped.
 *
 * @param rooms the live rooms
 * @param saved the saved box placements, or null when nothing has been saved
 * @returns a box for every live room
 */
export function displayBoxes(rooms: Room[], saved: RoomBox[] | null): RoomBox[] {
  const kept = curate(rooms, saved ?? [])
  const placed = new Set(kept.map((box) => box.roomId))
  const missing = rooms.filter((room) => !placed.has(room.id))
  const bottom = kept.reduce((max, box) => Math.max(max, box.y + box.h), 0)
  return [...kept, ...flow(missing, bottom)]
}

/** Adds a box for a room at the bottom of the layout, unless it is already placed. */
export function addRoomBox(boxes: RoomBox[], room: Room): RoomBox[] {
  if (boxes.some((box) => box.roomId === room.id)) {
    return boxes
  }
  const y = boxes.reduce((max, box) => Math.max(max, box.y + box.h), 0)
  return [...boxes, { roomId: room.id, x: 0, y, w: DEFAULT_W, h: DEFAULT_H }]
}

/** Removes a room's box from the layout (used when the room is deleted). */
export function removeRoomBox(boxes: RoomBox[], roomId: number): RoomBox[] {
  return boxes.filter((box) => box.roomId !== roomId)
}

/** Maps box placements to a react-grid-layout layout (keyed by room id, with a size floor). */
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

/** Maps a react-grid-layout layout back to saveable box placements. */
export function fromGridLayout(layout: Layout): RoomBox[] {
  return layout.map((item) => ({
    roomId: Number(item.i),
    x: item.x,
    y: item.y,
    w: item.w,
    h: item.h,
  }))
}

// --- Device placement geometry (a device icon's spot within its room) ---

/** How many columns the default-slot fan uses before wrapping to the next row. */
const SLOT_COLS = 4

/** Clamps a fraction into [0, 1]; maps NaN to 0.5 so a corrupt value still renders on the floor. */
export function clampFraction(n: number): number {
  if (Number.isNaN(n)) {
    return 0.5
  }
  return Math.min(1, Math.max(0, n))
}

/** Rounds a fraction to three decimals, capping each coordinate's serialized width on save. */
export function roundFraction(n: number): number {
  return Math.round(n * 1000) / 1000
}

/**
 * Maps a client point to a 0..1 fraction of a rect (the icon center). A zero-size rect (as jsdom
 * reports) yields the center rather than NaN, and both coordinates are clamped into range.
 *
 * @param point the client point (e.g. a pointer or drop position)
 * @param rect the room content rect the point falls in
 * @returns the point as a fraction of the rect
 */
export function pointToFraction(
  point: { x: number; y: number },
  rect: { left: number; top: number; width: number; height: number },
): { fx: number; fy: number } {
  const fx = rect.width === 0 ? 0.5 : (point.x - rect.left) / rect.width
  const fy = rect.height === 0 ? 0.5 : (point.y - rect.top) / rect.height
  return { fx: clampFraction(fx), fy: clampFraction(fy) }
}

/** A deterministic default slot for the i-th unplaced device, fanned across the floor. */
export function defaultSlot(index: number): { fx: number; fy: number } {
  const col = index % SLOT_COLS
  const row = Math.floor(index / SLOT_COLS) % SLOT_COLS
  return { fx: (col + 0.5) / SLOT_COLS, fy: (row + 0.5) / SLOT_COLS }
}

/**
 * Returns exactly one placement per device in the room: keep and clamp a saved placement, de-dup a
 * repeated device id (first wins), drop a placement whose device is no longer here, and fill a
 * device without a saved placement with its default slot. The output order matches {@code
 * roomDevices}, so a device's default slot is stable for a given room composition.
 *
 * @param roomDevices the devices currently assigned to the room, in display order
 * @param saved the saved placements to reconcile against
 * @returns one placement per room device
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
 * The placement list to persist for a room: every device's reconciled position, rounded to three
 * decimals. Storing an explicit position for each device (rather than omitting one left at a default
 * slot, whose default depends on the device's index in the room) keeps a saved layout stable, so
 * removing or reassigning one device never shifts where another device's icon sits.
 *
 * @param roomDevices the devices currently assigned to the room, in display order
 * @param saved the saved placements to reconcile against
 * @returns one placement to store per device
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
