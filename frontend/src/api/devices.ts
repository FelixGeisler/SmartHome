/** A device as returned by the SmartHome REST API. */
export interface Device {
  id: number
  externalId: string
  name: string
  type: string
  capabilities: string[]
  /** Command adapter id, or null for a sensing device. */
  adapterType: string | null
  /** Last known runtime state as key/value entries, interpreted per capability. */
  state: Record<string, string>
  /** Declared sensors and their latest readings; empty for non-sensing devices. */
  sensors: Sensor[]
}

/** One measurement channel on a device. */
export interface Sensor {
  key: string
  type: string
  unit: string
  /** Latest reading, or null before the first arrives. */
  value: string | null
  /** When the latest reading arrived (ISO-8601), or null before the first. */
  updatedAt: string | null
}

/** True when the device can be switched on and off. */
export function isSwitchable(device: Device): boolean {
  return device.capabilities.includes('SWITCHABLE')
}

/** True when the device reports sensor readings. */
export function isSensing(device: Device): boolean {
  return device.capabilities.includes('SENSING')
}

/** True when a switchable device reports itself switched on. */
export function isOn(device: Device): boolean {
  return device.state.on === 'true'
}

/** A sensor a device declares at registration. */
export interface SensorSpec {
  key: string
  type: string
  unit: string
}

/** Request body for registering a device. */
export interface DeviceRegistration {
  externalId: string
  name: string
  type: string
  /** Command adapter id; set for command devices, omitted for sensing devices. */
  adapterType?: string
  /** Declared sensors; set for sensing devices. */
  sensors?: SensorSpec[]
}

/** The fields we read from an RFC 9457 problem response. */
interface ProblemDetail {
  detail?: string
}

/** Lists all registered devices. */
export function listDevices(): Promise<Device[]> {
  return request<Device[]>('/api/devices')
}

/**
 * Registers a new device.
 *
 * @param registration the device to register
 * @returns the persisted device
 */
export function registerDevice(registration: DeviceRegistration): Promise<Device> {
  return request<Device>('/api/devices', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(registration),
  })
}

/**
 * Toggles a device on or off.
 *
 * @param id the device id
 * @returns the device's updated state
 */
export function toggleDevice(id: number): Promise<Device> {
  return request<Device>(`/api/devices/${id}/toggle`, { method: 'POST' })
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  if (!response.ok) {
    throw new Error(await errorMessage(response))
  }
  return (await response.json()) as T
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const problem = (await response.json()) as ProblemDetail
    if (problem.detail) {
      return problem.detail
    }
  } catch {
    // Body wasn't a problem document; fall through to the generic message.
  }
  return `Request failed with status ${response.status}`
}
