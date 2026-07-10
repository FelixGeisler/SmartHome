import { request } from './devices'

export interface CardLayout {
  deviceId: number
  x: number
  y: number
  w: number
  h: number
  /** Keys of the device's sensors whose charts are hidden on this card; absent means all shown. */
  hiddenSensors?: string[]
}

export interface DashboardLayout {
  cards: CardLayout[]
}

/** Reads the saved dashboard layout, or null when none saved (the hub answers a never-arranged dashboard with 204). */
export async function getLayout(): Promise<DashboardLayout | null> {
  const layout = await request<DashboardLayout | undefined>('/api/dashboard/layout')
  return layout ?? null
}

export function saveLayout(layout: DashboardLayout): Promise<DashboardLayout> {
  return request<DashboardLayout>('/api/dashboard/layout', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(layout),
  })
}
