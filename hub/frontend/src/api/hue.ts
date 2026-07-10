import { request } from './devices'

/** A light discovered on the Hue bridge. */
export interface HueLight {
  id: string
  name: string
  on: boolean
  /** What the light can do, detected from the state the bridge reports. */
  capabilities: string[]
}

/** Result of a bridge pairing attempt. */
export interface PairResult {
  paired: boolean
  message: string
}

/** Whether a Hue bridge is currently paired. */
export interface HueStatus {
  paired: boolean
}

/** Reports whether a Hue bridge is currently paired. */
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

/** Lists the lights on the paired bridge. */
export function discoverLights(): Promise<HueLight[]> {
  return request<HueLight[]>('/api/integrations/hue/lights')
}
