/** A device as returned by the SmartHome REST API. */
export interface Device {
  id: number
  externalId: string
  name: string
  type: string
  adapterType: string
  on: boolean
}

/** Request body for registering a device. */
export interface DeviceRegistration {
  externalId: string
  name: string
  type: string
  adapterType: string
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
