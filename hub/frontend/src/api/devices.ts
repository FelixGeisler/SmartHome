export interface Device {
  id: number
  externalId: string
  name: string
  type: string
  capabilities: string[]
  /** Command adapter id, or null for a sensing device. */
  adapterType: string | null
  /** The last known runtime state as key/value entries, interpreted per capability. */
  state: Record<string, string>
  /** Declared sensors and their latest readings; empty for non-sensing devices. */
  sensors: Sensor[]
  roomId?: number | null
  roomName?: string | null
  /** Whether the hub currently finds the device reachable; absent is treated as reachable. */
  reachable?: boolean
  /** When the hub last heard from the device (ISO-8601), or null before it ever has. */
  lastSeenAt?: string | null
}

export interface Sensor {
  key: string
  type: string
  unit: string
  /** Latest reading, or null before the first arrives. */
  value: string | null
  /** When the latest reading arrived (ISO-8601), or null before the first. */
  updatedAt: string | null
}

export function isSwitchable(device: Device): boolean {
  return device.capabilities.includes('SWITCHABLE')
}

export function isDimmable(device: Device): boolean {
  return device.capabilities.includes('DIMMABLE')
}

export function hasColor(device: Device): boolean {
  return device.capabilities.includes('COLOR')
}

export function hasColorTemperature(device: Device): boolean {
  return device.capabilities.includes('COLOR_TEMPERATURE')
}

export function isSensing(device: Device): boolean {
  return device.capabilities.includes('SENSING')
}

export function isOn(device: Device): boolean {
  return device.state.on === 'true'
}

export function brightnessOf(device: Device): number | null {
  const raw = device.state.brightness
  return raw === undefined ? null : Number(raw)
}

export function colorXyOf(device: Device): { x: number; y: number } | null {
  const raw = device.state.colorXy
  if (raw === undefined) {
    return null
  }
  const [x, y] = raw.split(',').map(Number)
  return { x, y }
}

export function colorTemperatureKOf(device: Device): number | null {
  const raw = device.state.colorTemperatureK
  return raw === undefined ? null : Number(raw)
}

/** Renders a reading as value plus unit, or "n/a" before the first reading arrives. */
export function formatReading(sensor: Sensor): string {
  return sensor.value === null ? 'n/a' : `${sensor.value} ${sensor.unit}`
}

export interface SensorSpec {
  key: string
  type: string
  unit: string
}

export interface DeviceRegistration {
  externalId: string
  name: string
  type: string
  /** Command adapter id; set for command devices, omitted for sensing devices. */
  adapterType?: string
  /** What the device can do, as detected at discovery; omitted to use the type's defaults. */
  capabilities?: string[]
  /** Declared sensors; set for sensing devices. */
  sensors?: SensorSpec[]
}

/** A neutral device command (ADR 3); set only the attributes that should change. */
export interface DeviceCommand {
  on?: boolean
  brightness?: number
  colorXy?: { x: number; y: number }
  colorTemperatureK?: number
}

/** The fields we read from an RFC 9457 problem response. */
interface ProblemDetail {
  detail?: string
}

export function listDevices(): Promise<Device[]> {
  return request<Device[]>('/api/devices')
}

export function registerDevice(registration: DeviceRegistration): Promise<Device> {
  return request<Device>('/api/devices', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(registration),
  })
}

export function toggleDevice(id: number): Promise<Device> {
  return request<Device>(`/api/devices/${id}/toggle`, { method: 'POST' })
}

export function sendCommand(id: number, command: DeviceCommand): Promise<Device> {
  return request<Device>(`/api/devices/${id}/command`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(command),
  })
}

export function renameDevice(id: number, name: string): Promise<Device> {
  return request<Device>(`/api/devices/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
}

/** Deletes a device; a still-publishing sensor node reappears on its next reading. */
export function deleteDevice(id: number): Promise<void> {
  return request<void>(`/api/devices/${id}`, { method: 'DELETE' })
}

/** Notified when a request is refused for want of a session, so the app can show the login gate. */
let unauthorizedHandler: (() => void) | null = null

/** Registers a callback invoked when an API request returns 401 (no session, or it expired). */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

// Spring issues a CSRF token in the XSRF-TOKEN cookie; echo it in a header on state-changing requests.
function csrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match !== null ? decodeURIComponent(match[1]) : null
}

export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const method = (init?.method ?? 'GET').toUpperCase()
  let finalInit = init
  if (MUTATING_METHODS.has(method)) {
    const token = csrfToken()
    if (token !== null) {
      finalInit = {
        ...init,
        headers: {
          ...(init?.headers as Record<string, string> | undefined),
          'X-XSRF-TOKEN': token,
        },
      }
    }
  }
  const response = await fetch(url, finalInit)
  if (response.status === 401 && !url.startsWith('/api/auth/')) {
    unauthorizedHandler?.()
  }
  if (!response.ok) {
    throw new Error(await errorMessage(response))
  }
  if (response.status === 204) {
    return undefined as T
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
