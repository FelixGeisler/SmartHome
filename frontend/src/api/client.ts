// ── Types ─────────────────────────────────────────────────────────────────────

export interface Floor {
  id: number
  name: string
  sortOrder: number
}

export interface Room {
  id: number
  name: string
  icon: string
  sortOrder: number
  floorId: number | null
  /** Primary rectangle position/size on the floor-plan canvas, as 0–100 percentages. Null = not placed. */
  planX: number | null
  planY: number | null
  planW: number | null
  planH: number | null
  /** Optional second segment for L-shaped rooms. Null = plain rectangle. */
  planX2: number | null
  planY2: number | null
  planW2: number | null
  planH2: number | null
}

export interface Scene {
  id: number
  name: string
  icon: string
  actions: SceneAction[]
}

export interface SceneAction {
  deviceId: number
  payloadJson: string
}

export interface Device {
  id: number
  externalId: string
  name: string
  type: 'HUE_LIGHT' | 'HOMEMATIC_RADIATOR' | 'MQTT_SENSOR' | 'SHELLY_PLUG' | 'SOLAKON_METER' | 'SOLAKON_INVERTER' | 'SOLAKON_ONE'
  room: string | null
  online: boolean
  lastStateJson: string | null
  lastSeen: string | null
  integrationInstanceId: number | null
  /** Floor-plan position within the room, as 0–100 percentages. Null = not placed. */
  roomX: number | null
  roomY: number | null
}

export interface SensorReading {
  id: number
  topic: string
  room: string
  metric: string
  value: number
  recordedAt: string
}

export interface AutomationEvent {
  id: number
  ruleName: string
  deviceId: number | null
  deviceName: string | null
  payloadJson: string
  firedAt: string
}

export interface Rule {
  id: number
  name: string
  enabled: boolean
  /** "HH:mm" e.g. "07:00" */
  triggerTime: string
  /** Comma-separated ISO weekday numbers (1=Mon…7=Sun), or null = every day */
  triggerDays: string | null
  targetDeviceId: number
  actionPayloadJson: string
  cooldownMs: number
  lastTriggered: string | null
}

export interface ConfigField {
  key: string
  label: string
  type: 'text' | 'password' | 'number'
  required: boolean
  placeholder: string
  description: string
}

export interface IntegrationTypeInfo {
  type: string
  displayName: string
  schema: ConfigField[]
}

export interface IntegrationInstance {
  id: number
  adapterType: string
  adapterDisplayName: string
  name: string
  config: Record<string, string>
  enabled: boolean
  running: boolean
}

export interface DashboardConfig {
  id: number
  name: string
  sortOrder: number
  layoutJson: string
  widgetsJson: string
  updatedAt: string
}

/** Returned by any adapter's scan-network endpoint. */
export interface FoundNetworkDevice {
  ip: string
  type: string
  name: string
  alreadyConfigured: boolean
}

export interface WidgetDef {
  id: string
  type: 'LightCard' | 'ThermostatCard' | 'ShellyPlugCard' | 'SolakonMeterCard' | 'SolakonGatewayCard' | 'RoomOverview' | 'SensorChart' | 'SceneCard'
  config: Record<string, unknown>
}

// ── Fetch helper ──────────────────────────────────────────────────────────────

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  })
  if (!res.ok) throw new Error(`${options?.method ?? 'GET'} ${path} → ${res.status}`)
  if (res.status === 204) return undefined as T
  return res.json()
}

// ── API ───────────────────────────────────────────────────────────────────────

export const api = {
  devices: {
    list: () => request<Device[]>('/api/devices'),
    getState: (id: number) => request<Record<string, unknown>>(`/api/devices/${id}/state`),
    command: (id: number, payload: Record<string, unknown>) =>
      request<void>(`/api/devices/${id}/command`, { method: 'POST', body: JSON.stringify(payload) }),
    discover: () => request<Record<string, number>>('/api/devices/discover', { method: 'POST' }),
    update: (id: number, data: Partial<Device> & Record<string, unknown>) =>
      request<Device>(`/api/devices/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id: number) =>
      request<void>(`/api/devices/${id}`, { method: 'DELETE' }),
    deleteByType: (type: string) =>
      request<void>(`/api/devices/by-type/${type}`, { method: 'DELETE' }),
    updatePosition: (id: number, x: number | null, y: number | null) =>
      request<Device>(`/api/devices/${id}/position`, { method: 'PUT', body: JSON.stringify({ x, y }) }),
  },

  sensors: {
    latest: () => request<SensorReading[]>('/api/sensors'),
    byRoomMetric: (room: string, metric: string, limit = 20) =>
      request<SensorReading[]>(`/api/sensors/${room}/${metric}?limit=${limit}`),
    history: (room: string, metric: string, since: string) =>
      request<SensorReading[]>(`/api/sensors/${room}/${metric}/history?since=${since}`),
  },

  rules: {
    list: () => request<Rule[]>('/api/automation/rules'),
    create: (rule: Omit<Rule, 'id' | 'lastTriggered'>) =>
      request<Rule>('/api/automation/rules', { method: 'POST', body: JSON.stringify(rule) }),
    update: (id: number, rule: Partial<Rule>) =>
      request<Rule>(`/api/automation/rules/${id}`, { method: 'PUT', body: JSON.stringify(rule) }),
    delete: (id: number) =>
      request<void>(`/api/automation/rules/${id}`, { method: 'DELETE' }),
    toggle: (id: number) =>
      request<Rule>(`/api/automation/rules/${id}/toggle`, { method: 'POST' }),
  },

  automationHistory: {
    list: (limit = 100) => request<AutomationEvent[]>(`/api/automation/history?limit=${limit}`),
    clear: () => request<void>('/api/automation/history', { method: 'DELETE' }),
  },

  dashboards: {
    list: () => request<DashboardConfig[]>('/api/dashboards'),
    get: (id: number) => request<DashboardConfig>(`/api/dashboards/${id}`),
    create: (data: { name: string }) =>
      request<DashboardConfig>('/api/dashboards', { method: 'POST', body: JSON.stringify(data) }),
    update: (id: number, data: Partial<DashboardConfig>) =>
      request<DashboardConfig>(`/api/dashboards/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id: number) =>
      request<void>(`/api/dashboards/${id}`, { method: 'DELETE' }),
  },

  floors: {
    list: () => request<Floor[]>('/api/floors'),
    create: (data: { name: string; sortOrder?: number }) =>
      request<Floor>('/api/floors', { method: 'POST', body: JSON.stringify(data) }),
    update: (id: number, data: { name?: string; sortOrder?: number }) =>
      request<Floor>(`/api/floors/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id: number) =>
      request<void>(`/api/floors/${id}`, { method: 'DELETE' }),
  },

  rooms: {
    list: () => request<Room[]>('/api/rooms'),
    create: (data: Partial<Room> & { name: string; icon: string }) =>
      request<Room>('/api/rooms', { method: 'POST', body: JSON.stringify(data) }),
    update: (id: number, data: Partial<Room>) =>
      request<Room>(`/api/rooms/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id: number) =>
      request<void>(`/api/rooms/${id}`, { method: 'DELETE' }),
  },

  scenes: {
    list: () => request<Scene[]>('/api/scenes'),
    create: (data: Omit<Scene, 'id'>) =>
      request<Scene>('/api/scenes', { method: 'POST', body: JSON.stringify(data) }),
    update: (id: number, data: Partial<Omit<Scene, 'id'>>) =>
      request<Scene>(`/api/scenes/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id: number) =>
      request<void>(`/api/scenes/${id}`, { method: 'DELETE' }),
    activate: (id: number) =>
      request<void>(`/api/scenes/${id}/activate`, { method: 'POST' }),
  },

  chat: {
    status: () => request<{ enabled: boolean; message: string }>('/api/chat/status'),
    /**
     * POST to /api/chat/stream and read the SSE response token-by-token.
     * Calls {@code onToken} for each chunk, {@code onError} on stream/server errors,
     * resolves when the stream completes.
     */
    stream: async (
      message: string,
      history: { role: 'user' | 'assistant'; content: string }[],
      onToken: (token: string) => void,
      onError: (err: string) => void,
      signal?: AbortSignal,
    ): Promise<void> => {
      const res = await fetch('/api/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message, history }),
        signal,
      })
      if (!res.ok || !res.body) {
        onError(`HTTP ${res.status}: ${await res.text().catch(() => '')}`)
        return
      }
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      // eslint-disable-next-line no-constant-condition
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const frames = buffer.split('\n\n')
        buffer = frames.pop() ?? ''
        for (const frame of frames) {
          if (!frame.trim()) continue
          let event = 'message'
          const dataLines: string[] = []
          for (const line of frame.split('\n')) {
            if (line.startsWith('event:')) event = line.slice(6).trim()
            else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
          }
          const data = dataLines.join('\n')
          if (event === 'error') onError(data)
          else onToken(data)
        }
      }
    },
  },

  integrations: {
    types: () => request<IntegrationTypeInfo[]>('/api/integrations/types'),
    list: () => request<IntegrationInstance[]>('/api/integrations/instances'),
    get: (id: number) => request<IntegrationInstance>(`/api/integrations/instances/${id}`),
    create: (data: { adapterType: string; name: string; config: Record<string, string> }) =>
      request<IntegrationInstance>('/api/integrations/instances', { method: 'POST', body: JSON.stringify(data) }),
    update: (id: number, data: { name?: string; enabled?: boolean; config?: Record<string, string> }) =>
      request<IntegrationInstance>(`/api/integrations/instances/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id: number) =>
      request<void>(`/api/integrations/instances/${id}`, { method: 'DELETE' }),
    test: (id: number) =>
      request<{ success: boolean; message: string }>(`/api/integrations/instances/${id}/test`, { method: 'POST' }),
    discover: (id: number) =>
      request<{ discovered: number; devices: { id: number; name: string; externalId: string }[] }>(
        `/api/integrations/instances/${id}/discover`, { method: 'POST' }),
    /** Scan the local network for devices of the given adapter type. */
    scan: (adapterType: string) =>
      request<{ found: FoundNetworkDevice[]; error?: string }>(
        `/api/integrations/scan/${adapterType}`, { method: 'POST' }),
    /** Run the automated setup flow (e.g. Hue bridge button pairing). */
    autoSetup: (adapterType: string, knownIp?: string) =>
      request<{ success: boolean; message: string; linkButtonRequired?: boolean; ip?: string; settingsFound?: Record<string, string> }>(
        `/api/integrations/auto-setup/${adapterType}`, { method: 'POST', body: JSON.stringify(knownIp ? { ip: knownIp } : {}) }),
    /** Discover the CCU/gateway for the given adapter type on the local network. */
    discoverCcu: (adapterType: string) =>
      request<{ found: boolean; ip?: string; message?: string }>(
        `/api/integrations/discover-ccu/${adapterType}`, { method: 'POST' }),
  },
}
