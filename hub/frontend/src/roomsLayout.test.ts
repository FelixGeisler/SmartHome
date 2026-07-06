import { describe, expect, it } from 'vitest'
import type { DevicePlacement, Room, RoomBox } from './api/rooms'
import {
  addRoomBox,
  clampFraction,
  curate,
  defaultSlot,
  displayBoxes,
  fromGridLayout,
  placeAllRooms,
  placementsToSave,
  pointToFraction,
  reconcilePlacements,
  removeRoomBox,
  roundFraction,
  toGridLayout,
} from './roomsLayout'

function room(id: number): Room {
  return { id, name: `Room ${id}` }
}

describe('placeAllRooms', () => {
  it('lays every room out in a grid without overlaps', () => {
    const boxes = placeAllRooms([room(1), room(2), room(3)])

    expect(boxes.map((box) => box.roomId)).toEqual([1, 2, 3])
    expect(boxes[0]).toMatchObject({ x: 0, y: 0 })
    // Two per row at width 6 in a 12-column grid, so the third wraps to a new row.
    expect(boxes[2].x).toBe(0)
    expect(boxes[2].y).toBeGreaterThan(0)
    expect(new Set(boxes.map((box) => `${box.x},${box.y}`)).size).toBe(3)
  })
})

describe('curate', () => {
  it('keeps only boxes whose room still exists, preserving placement', () => {
    const saved: RoomBox[] = [
      { roomId: 9, x: 0, y: 0, w: 6, h: 8 },
      { roomId: 1, x: 6, y: 2, w: 3, h: 5 },
    ]

    expect(curate([room(1)], saved)).toEqual([{ roomId: 1, x: 6, y: 2, w: 3, h: 5 }])
  })
})

describe('displayBoxes', () => {
  it('places every room when no layout has been saved (null)', () => {
    expect(displayBoxes([room(1), room(2)], null).map((box) => box.roomId)).toEqual([1, 2])
  })

  it('keeps a saved box and appends a default box for a room added since', () => {
    const saved: RoomBox[] = [{ roomId: 1, x: 3, y: 4, w: 6, h: 8 }]

    const boxes = displayBoxes([room(1), room(2)], saved)

    // The room that has a saved box keeps its exact placement...
    expect(boxes.find((box) => box.roomId === 1)).toEqual({ roomId: 1, x: 3, y: 4, w: 6, h: 8 })
    // ...and the room added after the layout was saved still gets a box (below the kept ones).
    const appended = boxes.find((box) => box.roomId === 2)
    expect(appended).toBeDefined()
    expect(appended?.y).toBeGreaterThanOrEqual(4 + 8)
  })

  it('drops a saved box whose room was deleted', () => {
    const saved: RoomBox[] = [
      { roomId: 1, x: 0, y: 0, w: 6, h: 8 },
      { roomId: 2, x: 6, y: 0, w: 6, h: 8 },
    ]

    expect(displayBoxes([room(1)], saved).map((box) => box.roomId)).toEqual([1])
  })
})

describe('addRoomBox / removeRoomBox', () => {
  it('adds a box for a room at the bottom of the layout', () => {
    const boxes: RoomBox[] = [{ roomId: 1, x: 0, y: 0, w: 6, h: 8 }]

    const next = addRoomBox(boxes, room(2))

    expect(next.map((box) => box.roomId)).toEqual([1, 2])
    expect(next[1].y).toBeGreaterThanOrEqual(8)
  })

  it('does not add a room already placed', () => {
    const boxes: RoomBox[] = [{ roomId: 1, x: 0, y: 0, w: 6, h: 8 }]

    expect(addRoomBox(boxes, room(1))).toBe(boxes)
  })

  it('removes a room box by id', () => {
    const boxes: RoomBox[] = [
      { roomId: 1, x: 0, y: 0, w: 6, h: 8 },
      { roomId: 2, x: 6, y: 0, w: 6, h: 8 },
    ]

    expect(removeRoomBox(boxes, 1).map((box) => box.roomId)).toEqual([2])
  })
})

describe('grid layout mapping', () => {
  it('toGridLayout keys items by room id with a size floor', () => {
    const layout = toGridLayout([{ roomId: 7, x: 1, y: 2, w: 6, h: 8 }])

    expect(layout[0]).toMatchObject({ i: '7', x: 1, y: 2, w: 6, h: 8 })
    expect(layout[0].minW).toBeDefined()
  })

  it('round-trips placements through the grid layout unchanged', () => {
    const boxes: RoomBox[] = [{ roomId: 3, x: 0, y: 0, w: 6, h: 5 }]

    expect(fromGridLayout(toGridLayout(boxes))).toEqual(boxes)
  })
})

describe('clampFraction', () => {
  it('passes an in-range value through', () => {
    expect(clampFraction(0.3)).toBe(0.3)
  })

  it('clamps below 0 and above 1', () => {
    expect(clampFraction(-1)).toBe(0)
    expect(clampFraction(2)).toBe(1)
  })

  it('maps NaN to the center', () => {
    expect(clampFraction(Number.NaN)).toBe(0.5)
  })
})

describe('roundFraction', () => {
  it('rounds to three decimals', () => {
    expect(roundFraction(0.123456)).toBe(0.123)
  })
})

describe('pointToFraction', () => {
  const rect = { left: 0, top: 0, width: 200, height: 100 }

  it('maps the center of a rect to the middle', () => {
    expect(pointToFraction({ x: 100, y: 50 }, rect)).toEqual({ fx: 0.5, fy: 0.5 })
  })

  it('clamps a point past the far edge to 1', () => {
    expect(pointToFraction({ x: 250, y: 130 }, rect)).toEqual({ fx: 1, fy: 1 })
  })

  it('clamps a point before the near edge to 0', () => {
    expect(pointToFraction({ x: -10, y: -10 }, rect)).toEqual({ fx: 0, fy: 0 })
  })

  it('returns the center for a zero-size rect rather than NaN', () => {
    expect(pointToFraction({ x: 5, y: 5 }, { left: 0, top: 0, width: 0, height: 0 })).toEqual({
      fx: 0.5,
      fy: 0.5,
    })
  })
})

describe('defaultSlot', () => {
  it('gives distinct in-range slots for the first few indices', () => {
    const slots = [defaultSlot(0), defaultSlot(1), defaultSlot(2)]

    for (const slot of slots) {
      expect(slot.fx).toBeGreaterThan(0)
      expect(slot.fx).toBeLessThan(1)
      expect(slot.fy).toBeGreaterThan(0)
      expect(slot.fy).toBeLessThan(1)
    }
    expect(new Set(slots.map((slot) => `${slot.fx},${slot.fy}`)).size).toBe(3)
  })
})

describe('reconcilePlacements', () => {
  it('keeps and clamps a saved placement for an assigned device', () => {
    const saved: DevicePlacement[] = [{ deviceId: 1, fx: 1.5, fy: 0.3 }]

    expect(reconcilePlacements([{ id: 1 }], saved)).toEqual([{ deviceId: 1, fx: 1, fy: 0.3 }])
  })

  it('drops a placement whose device is no longer in the room', () => {
    const saved: DevicePlacement[] = [{ deviceId: 2, fx: 0.1, fy: 0.1 }]

    expect(reconcilePlacements([{ id: 1 }], saved).map((p) => p.deviceId)).toEqual([1])
  })

  it('de-dups a repeated device id, keeping the first', () => {
    const saved: DevicePlacement[] = [
      { deviceId: 1, fx: 0.2, fy: 0.2 },
      { deviceId: 1, fx: 0.9, fy: 0.9 },
    ]

    expect(reconcilePlacements([{ id: 1 }], saved)).toEqual([{ deviceId: 1, fx: 0.2, fy: 0.2 }])
  })

  it('fills an unplaced device with its default slot, one placement per device in order', () => {
    const result = reconcilePlacements([{ id: 5 }, { id: 6 }], [])

    expect(result.map((p) => p.deviceId)).toEqual([5, 6])
    expect(result[0]).toEqual({ deviceId: 5, ...defaultSlot(0) })
    expect(result[1]).toEqual({ deviceId: 6, ...defaultSlot(1) })
  })
})

describe('placementsToSave', () => {
  it('rounds a placement to three decimals', () => {
    const saved: DevicePlacement[] = [{ deviceId: 1, fx: 0.123456, fy: 0.5 }]

    expect(placementsToSave([{ id: 1 }], saved)).toEqual([{ deviceId: 1, fx: 0.123, fy: 0.5 }])
  })

  it('persists a device left at its default slot with an explicit position', () => {
    expect(placementsToSave([{ id: 1 }], [])).toEqual([{ deviceId: 1, ...defaultSlot(0) }])
  })

  it('keeps a device moved off its default slot', () => {
    const saved: DevicePlacement[] = [{ deviceId: 1, fx: 0.7, fy: 0.7 }]

    expect(placementsToSave([{ id: 1 }], saved)).toEqual([{ deviceId: 1, fx: 0.7, fy: 0.7 }])
  })

  it('keeps an unmoved device in place when a lower-indexed device leaves the room', () => {
    const roomDevices = [{ id: 1 }, { id: 2 }]
    // Move device 1; device 2 stays at its default slot but is now persisted explicitly.
    const saved = placementsToSave(roomDevices, [{ deviceId: 1, fx: 0.9, fy: 0.9 }])
    const twoBefore = reconcilePlacements(roomDevices, saved).find((p) => p.deviceId === 2)

    // Device 1 leaves the room; device 2 must not shift to a different default slot.
    const twoAfter = reconcilePlacements([{ id: 2 }], saved).find((p) => p.deviceId === 2)

    expect(twoAfter).toEqual(twoBefore)
  })
})
