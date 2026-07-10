import type { SensorSpec } from './devices'
import { request } from './devices'

/** A controllable or sensing channel discovered on the Homematic CCU. */
export interface HomematicDevice {
  /** The channel address as "<interface>/<channelAddress>", used as the device's external id. */
  externalId: string
  name: string
  /** What the channel can do, detected from its datapoints. */
  capabilities: string[]
  /** The sensors a sensing channel reports; empty for a command channel. */
  sensors: SensorSpec[]
}

/** Result of a CCU connection attempt. */
export interface ConnectResult {
  connected: boolean
  message: string
}

/** Whether a Homematic CCU is currently connected. */
export interface HomematicStatus {
  connected: boolean
}

/** Reports whether a Homematic CCU is currently connected. */
export function homematicStatus(): Promise<HomematicStatus> {
  return request<HomematicStatus>('/api/integrations/homematic/status')
}

/** Connects to a Homematic CCU with its WebUI credentials. */
export function connectCcu(host: string, username: string, password: string): Promise<ConnectResult> {
  return request<ConnectResult>('/api/integrations/homematic/connect', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ host, username, password }),
  })
}

/** Lists the controllable and sensing channels on the connected CCU. */
export function discoverDevices(): Promise<HomematicDevice[]> {
  return request<HomematicDevice[]>('/api/integrations/homematic/devices')
}
