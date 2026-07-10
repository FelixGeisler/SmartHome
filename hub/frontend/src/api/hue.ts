import { request } from './devices'

export interface HueLight {
  id: string
  name: string
  on: boolean
  /** What the light can do, detected from the state the bridge reports. */
  capabilities: string[]
}

export interface PairResult {
  paired: boolean
  message: string
}

export interface HueStatus {
  paired: boolean
}

export function hueStatus(): Promise<HueStatus> {
  return request<HueStatus>('/api/integrations/hue/status')
}

/** Pairs with a Hue bridge. The bridge link button must be pressed first. */
export function pairBridge(host: string): Promise<PairResult> {
  return request<PairResult>('/api/integrations/hue/pair', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ host }),
  })
}

export function discoverLights(): Promise<HueLight[]> {
  return request<HueLight[]>('/api/integrations/hue/lights')
}
