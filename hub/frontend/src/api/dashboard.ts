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

/**
 * Reads the saved dashboard layout, or null when none has been saved yet. The hub answers a
 * never-arranged dashboard with 204 (an empty body); surfacing that as null lets the caller tell a
 * first-run dashboard apart from one a user intentionally emptied.
 */
export async function getLayout(): Promise<DashboardLayout | null> {
  const layout = await request<DashboardLayout | undefined>('/api/dashboard/layout')
  return layout ?? null
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
