/** A device as returned by the SmartHome REST API. */
export interface Device {
  id: number
  externalId: string
  name: string
  type: string
  adapterType: string
  on: boolean
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
