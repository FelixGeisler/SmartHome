import { request } from './devices'

export type TriggerKind = 'SENSOR_THRESHOLD' | 'SCHEDULE'

export type ConditionKind = 'DEVICE_STATE'

export type ActionKind = 'DEVICE_COMMAND' | 'DEVICE_TOGGLE'

export type Comparison =
  | 'GREATER_THAN'
  | 'GREATER_THAN_OR_EQUAL'
  | 'LESS_THAN'
  | 'LESS_THAN_OR_EQUAL'

export interface AutomationTrigger {
  kind: TriggerKind
  /** The watched device (sensor threshold only); null for a schedule. */
  deviceId: number | null
  sensorKey: string | null
  comparison: Comparison | null
  threshold: number | null
  /** The time of day a schedule fires (HH:MM:SS); null for a sensor threshold. */
  atTime: string | null
  /** The days a schedule fires on (day names); empty means every day. */
  onDays: string[]
}

export interface AutomationCondition {
  kind: ConditionKind
  deviceId: number
  stateKey: string | null
  expected: string | null
}

export interface AutomationAction {
  kind: ActionKind
  deviceId: number
  on: boolean | null
  brightness: number | null
  colorTemperatureK: number | null
}

export interface Automation {
  id: number
  name: string
  enabled: boolean
  triggers: AutomationTrigger[]
  conditions: AutomationCondition[]
  actions: AutomationAction[]
}

export interface AutomationInput {
  name: string
  enabled: boolean
  triggers: AutomationTrigger[]
  conditions: AutomationCondition[]
  actions: AutomationAction[]
}

const JSON_HEADERS = { 'Content-Type': 'application/json' }

export function listAutomations(): Promise<Automation[]> {
  return request<Automation[]>('/api/automations')
}

export function createAutomation(input: AutomationInput): Promise<Automation> {
  return request<Automation>('/api/automations', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify(input),
  })
}

export function updateAutomation(id: number, input: AutomationInput): Promise<Automation> {
  return request<Automation>(`/api/automations/${id}`, {
    method: 'PUT',
    headers: JSON_HEADERS,
    body: JSON.stringify(input),
  })
}

export function setAutomationEnabled(id: number, enabled: boolean): Promise<Automation> {
  return request<Automation>(`/api/automations/${id}/${enabled ? 'enable' : 'disable'}`, {
    method: 'POST',
  })
}

export function runAutomation(id: number): Promise<void> {
  return request<void>(`/api/automations/${id}/run`, { method: 'POST' })
}

export function deleteAutomation(id: number): Promise<void> {
  return request<void>(`/api/automations/${id}`, { method: 'DELETE' })
}
