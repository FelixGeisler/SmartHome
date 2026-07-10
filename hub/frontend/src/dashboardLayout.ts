import type { Layout } from 'react-grid-layout'
import type { CardLayout } from './api/dashboard'
import type { Device } from './api/devices'

/** Grid geometry shared with the grid config in DashboardPage. */
export const GRID_COLS = 12
const DEFAULT_W = 4
const DEFAULT_H = 7
const MIN_W = 2
const MIN_H = 3

/** Default grid before the dashboard has been arranged. */
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

/** Keeps only cards whose device still exists. */
export function curate(devices: Device[], cards: CardLayout[]): CardLayout[] {
  const live = new Set(devices.map((device) => device.id))
  return cards.filter((card) => live.has(card.deviceId))
}

/**
 * The curated set once arranged (saved is a list, even empty), or every device as a default before
 * then (saved is null).
 */
export function displayCards(devices: Device[], saved: CardLayout[] | null): CardLayout[] {
  return saved === null ? placeAll(devices) : curate(devices, saved)
}

/** Devices not on the dashboard; the add-card picker's choices. */
export function available(devices: Device[], cards: CardLayout[]): Device[] {
  const shown = new Set(cards.map((card) => card.deviceId))
  return devices.filter((device) => !shown.has(device.id))
}

/** Appends a card at the bottom, unless already placed. */
export function addCard(cards: CardLayout[], device: Device): CardLayout[] {
  if (cards.some((card) => card.deviceId === device.id)) {
    return cards
  }
  const y = cards.reduce((max, card) => Math.max(max, card.y + card.h), 0)
  return [...cards, { deviceId: device.id, x: 0, y, w: DEFAULT_W, h: DEFAULT_H }]
}

/** Removes a device's card. */
export function removeCard(cards: CardLayout[], deviceId: number): CardLayout[] {
  return cards.filter((card) => card.deviceId !== deviceId)
}

/** Maps cards to a react-grid-layout layout. */
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
 * Maps a grid layout back to cards. The grid carries only geometry, so each card's hidden-chart
 * selection is carried over by device id; without this a drag or resize would silently clear it.
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

/** Toggles a device's sensor chart in that card's hidden set. */
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
