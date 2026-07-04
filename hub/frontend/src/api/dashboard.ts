import { request } from './devices'

/** One saved card: which device it shows and its position and size in grid units. */
export interface CardLayout {
  deviceId: number
  x: number
  y: number
  w: number
  h: number
}

/** A saved dashboard arrangement: each card's device and its grid placement. */
export interface DashboardLayout {
  cards: CardLayout[]
}

/** Reads the saved dashboard layout. */
export function getLayout(): Promise<DashboardLayout> {
  return request<DashboardLayout>('/api/dashboard/layout')
}

/**
 * Replaces the saved dashboard layout.
 *
 * @param layout the layout to persist
 * @returns the saved layout
 */
export function saveLayout(layout: DashboardLayout): Promise<DashboardLayout> {
  return request<DashboardLayout>('/api/dashboard/layout', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(layout),
  })
}
