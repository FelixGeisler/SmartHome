import type { Layout } from 'react-grid-layout'
import type { CardLayout } from './api/dashboard'
import type { Device } from './api/devices'

/** Grid geometry shared with the grid config in DashboardPage. */
export const GRID_COLS = 12
/** Default placement for a newly added card. */
const DEFAULT_W = 4
const DEFAULT_H = 7
/** Smallest a card may be resized to, so it never collapses to nothing. */
const MIN_W = 2
const MIN_H = 3

/** Places every device in a tidy grid; the default view before the dashboard has been arranged. */
export function placeAll(devices: Device[]): CardLayout[] {
  const cards: CardLayout[] = []
  let x = 0
  let y = 0
  for (const device of devices) {
    if (x + DEFAULT_W > GRID_COLS) {
      x = 0
      y += DEFAULT_H
    }
    cards.push({ deviceId: device.id, x, y, w: DEFAULT_W, h: DEFAULT_H })
    x += DEFAULT_W
  }
  return cards
}

/** Keeps only the cards whose device still exists, preserving their saved placement. */
export function curate(devices: Device[], cards: CardLayout[]): CardLayout[] {
  const live = new Set(devices.map((device) => device.id))
  return cards.filter((card) => live.has(card.deviceId))
}

/**
 * The cards to show for a committed layout: the curated set once the dashboard has been arranged,
 * or every device (a tidy default) before then. An empty saved layout means "not arranged yet".
 *
 * @param devices the live devices
 * @param saved the saved card placements
 * @returns the cards to show
 */
export function displayCards(devices: Device[], saved: CardLayout[]): CardLayout[] {
  return saved.length > 0 ? curate(devices, saved) : placeAll(devices)
}

/** The registered devices not currently on the dashboard; the choices the add-card picker offers. */
export function available(devices: Device[], cards: CardLayout[]): Device[] {
  const shown = new Set(cards.map((card) => card.deviceId))
  return devices.filter((device) => !shown.has(device.id))
}

/** Adds a card for a device at the bottom of the layout, unless it is already placed. */
export function addCard(cards: CardLayout[], device: Device): CardLayout[] {
  if (cards.some((card) => card.deviceId === device.id)) {
    return cards
  }
  const y = cards.reduce((max, card) => Math.max(max, card.y + card.h), 0)
  return [...cards, { deviceId: device.id, x: 0, y, w: DEFAULT_W, h: DEFAULT_H }]
}

/** Removes a device's card from the layout (the device itself stays registered). */
export function removeCard(cards: CardLayout[], deviceId: number): CardLayout[] {
  return cards.filter((card) => card.deviceId !== deviceId)
}

/** Maps card placements to a react-grid-layout layout (keyed by device id, with a size floor). */
export function toGridLayout(cards: CardLayout[]): Layout {
  return cards.map((card) => ({
    i: String(card.deviceId),
    x: card.x,
    y: card.y,
    w: card.w,
    h: card.h,
    minW: MIN_W,
    minH: MIN_H,
  }))
}

/** Maps a react-grid-layout layout back to saveable card placements. */
export function fromGridLayout(layout: Layout): CardLayout[] {
  return layout.map((item) => ({
    deviceId: Number(item.i),
    x: item.x,
    y: item.y,
    w: item.w,
    h: item.h,
  }))
}
