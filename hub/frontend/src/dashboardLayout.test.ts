import { describe, expect, it } from 'vitest'
import type { CardLayout } from './api/dashboard'
import type { Device } from './api/devices'
import {
  addCard,
  available,
  curate,
  displayCards,
  fromGridLayout,
  placeAll,
  removeCard,
  toGridLayout,
} from './dashboardLayout'

function device(id: number): Device {
  return {
    id,
    externalId: `ext-${id}`,
    name: `Device ${id}`,
    type: 'SHELLY_PLUG',
    capabilities: ['SWITCHABLE'],
    adapterType: 'shelly',
    state: {},
    sensors: [],
  }
}

describe('placeAll', () => {
  it('lays every device out in a grid without overlaps', () => {
    const cards = placeAll([device(1), device(2), device(3), device(4)])

    expect(cards.map((card) => card.deviceId)).toEqual([1, 2, 3, 4])
    expect(cards[0]).toMatchObject({ x: 0, y: 0 })
    // Three per row at width 4 in a 12-column grid, so the fourth wraps to a new row.
    expect(cards[3].x).toBe(0)
    expect(cards[3].y).toBeGreaterThan(0)
    expect(new Set(cards.map((card) => `${card.x},${card.y}`)).size).toBe(4)
  })
})

describe('curate', () => {
  it('keeps only cards whose device still exists, preserving placement', () => {
    const saved: CardLayout[] = [
      { deviceId: 9, x: 0, y: 0, w: 4, h: 7 },
      { deviceId: 1, x: 4, y: 2, w: 3, h: 5 },
    ]

    expect(curate([device(1)], saved)).toEqual([{ deviceId: 1, x: 4, y: 2, w: 3, h: 5 }])
  })

  it('does not add devices that are missing from the layout', () => {
    const saved: CardLayout[] = [{ deviceId: 1, x: 0, y: 0, w: 4, h: 7 }]

    expect(curate([device(1), device(2)], saved).map((card) => card.deviceId)).toEqual([1])
  })
})

describe('displayCards', () => {
  it('shows every device when no layout has been saved (null)', () => {
    expect(displayCards([device(1), device(2)], null).map((card) => card.deviceId)).toEqual([1, 2])
  })

  it('shows an empty dashboard for a saved-but-empty layout', () => {
    expect(displayCards([device(1), device(2)], [])).toEqual([])
  })

  it('shows only the curated cards once a layout is saved', () => {
    const saved: CardLayout[] = [{ deviceId: 2, x: 0, y: 0, w: 4, h: 7 }]

    expect(displayCards([device(1), device(2)], saved).map((card) => card.deviceId)).toEqual([2])
  })
})

describe('available', () => {
  it('lists the devices not already on the dashboard', () => {
    const cards: CardLayout[] = [{ deviceId: 1, x: 0, y: 0, w: 4, h: 7 }]

    expect(available([device(1), device(2), device(3)], cards).map((d) => d.id)).toEqual([2, 3])
  })
})

describe('addCard / removeCard', () => {
  it('adds a card for a device at the bottom of the layout', () => {
    const cards: CardLayout[] = [{ deviceId: 1, x: 0, y: 0, w: 4, h: 7 }]

    const next = addCard(cards, device(2))

    expect(next.map((card) => card.deviceId)).toEqual([1, 2])
    expect(next[1].y).toBeGreaterThanOrEqual(7)
  })

  it('does not add a device already on the dashboard', () => {
    const cards: CardLayout[] = [{ deviceId: 1, x: 0, y: 0, w: 4, h: 7 }]

    expect(addCard(cards, device(1))).toBe(cards)
  })

  it('removes a device card by id', () => {
    const cards: CardLayout[] = [
      { deviceId: 1, x: 0, y: 0, w: 4, h: 7 },
      { deviceId: 2, x: 4, y: 0, w: 4, h: 7 },
    ]

    expect(removeCard(cards, 1).map((card) => card.deviceId)).toEqual([2])
  })
})

describe('grid layout mapping', () => {
  it('toGridLayout keys items by device id with a size floor', () => {
    const layout = toGridLayout([{ deviceId: 7, x: 1, y: 2, w: 4, h: 7 }])

    expect(layout[0]).toMatchObject({ i: '7', x: 1, y: 2, w: 4, h: 7 })
    expect(layout[0].minW).toBeDefined()
  })

  it('round-trips placements through the grid layout unchanged', () => {
    const cards: CardLayout[] = [{ deviceId: 3, x: 0, y: 0, w: 6, h: 4 }]

    expect(fromGridLayout(toGridLayout(cards))).toEqual(cards)
  })
})
