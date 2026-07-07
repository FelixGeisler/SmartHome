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
 * The cards to show for a committed layout: the curated set once the dashboard has been arranged
 * (`saved` is a list, even an empty one), or every device (a tidy default) before then (`saved` is
 * null, meaning nothing has been saved yet).
 *
 * @param devices the live devices
 * @param saved the saved card placements, or null when nothing has been saved
 * @returns the cards to show
 */
export function displayCards(devices: Device[], saved: CardLayout[] | null): CardLayout[] {
  return saved === null ? placeAll(devices) : curate(devices, saved)
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

/**
 * Maps a react-grid-layout layout back to saveable card placements. The grid layout carries only
 * geometry, so each card's hidden-chart selection is carried over from the previous cards by device
 * id; without this a drag or resize would silently clear it.
 *
 * @param layout the react-grid-layout layout after a drag or resize
 * @param previous the cards before the change, holding each card's hidden-chart selection
 * @returns the saveable cards with geometry from the grid and selections preserved
 */
export function fromGridLayout(layout: Layout, previous: CardLayout[]): CardLayout[] {
  const hidden = new Map(previous.map((card) => [card.deviceId, card.hiddenSensors ?? []]))
  return layout.map((item) => {
    const deviceId = Number(item.i)
    return {
      deviceId,
      x: item.x,
      y: item.y,
      w: item.w,
      h: item.h,
      hiddenSensors: hidden.get(deviceId) ?? [],
    }
  })
}

/** Hides or shows a device's sensor chart on its card, toggling the key in that card's hidden set. */
export function toggleHiddenSensor(
  cards: CardLayout[],
  deviceId: number,
  sensorKey: string,
): CardLayout[] {
  return cards.map((card) => {
    if (card.deviceId !== deviceId) {
      return card
    }
    const hidden = card.hiddenSensors ?? []
    const next = hidden.includes(sensorKey)
      ? hidden.filter((key) => key !== sensorKey)
      : [...hidden, sensorKey]
    return { ...card, hiddenSensors: next }
  })
}
