import type { SensorSpec } from './devices'
import { request } from './devices'

export interface HomematicDevice {
  /** The channel address as "<interface>/<channelAddress>", used as the device's external id. */
  externalId: string
  name: string
  /** What the channel can do, detected from its datapoints. */
  capabilities: string[]
  /** The sensors a sensing channel reports; empty for a command channel. */
  sensors: SensorSpec[]
}

export interface ConnectResult {
  connected: boolean
  message: string
}

export interface HomematicStatus {
  connected: boolean
}

export function homematicStatus(): Promise<HomematicStatus> {
  return request<HomematicStatus>('/api/integrations/homematic/status')
}

export function connectCcu(host: string, username: string, password: string): Promise<ConnectResult> {
  return request<ConnectResult>('/api/integrations/homematic/connect', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ host, username, password }),
  })
}

export function discoverDevices(): Promise<HomematicDevice[]> {
  return request<HomematicDevice[]>('/api/integrations/homematic/devices')
}
