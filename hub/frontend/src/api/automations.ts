import { request } from './devices'

/** What starts an automation. */
export type TriggerKind = 'SENSOR_THRESHOLD'

/** An extra check that must hold for a triggered automation to run. */
export type ConditionKind = 'DEVICE_STATE'

/** What an automation does when it runs. */
export type ActionKind = 'DEVICE_COMMAND' | 'DEVICE_TOGGLE'

/** How a reading is weighed against a threshold. */
export type Comparison =
  | 'GREATER_THAN'
  | 'GREATER_THAN_OR_EQUAL'
  | 'LESS_THAN'
  | 'LESS_THAN_OR_EQUAL'

/** A check on telemetry that starts an automation. */
export interface AutomationTrigger {
  kind: TriggerKind
  deviceId: number
  sensorKey: string | null
  comparison: Comparison | null
  threshold: number | null
}

/** A device-state check that must hold for the automation to run. */
export interface AutomationCondition {
  kind: ConditionKind
  deviceId: number
  stateKey: string | null
  expected: string | null
}

/** Something the automation does to a device when it runs. */
export interface AutomationAction {
  kind: ActionKind
  deviceId: number
  on: boolean | null
  brightness: number | null
  colorTemperatureK: number | null
}

/** An automation as returned by the SmartHome REST API. */
export interface Automation {
  id: number
  name: string
  enabled: boolean
  triggers: AutomationTrigger[]
  conditions: AutomationCondition[]
  actions: AutomationAction[]
}

/** Request body for creating or replacing an automation. */
export interface AutomationInput {
  name: string
  enabled: boolean
  triggers: AutomationTrigger[]
  conditions: AutomationCondition[]
  actions: AutomationAction[]
}

const JSON_HEADERS = { 'Content-Type': 'application/json' }

/** Lists all automations. */
export function listAutomations(): Promise<Automation[]> {
  return request<Automation[]>('/api/automations')
}

/**
 * Creates an automation.
 *
 * @param input the automation to create
 * @returns the persisted automation
 */
export function createAutomation(input: AutomationInput): Promise<Automation> {
  return request<Automation>('/api/automations', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify(input),
  })
}

/**
 * Replaces an automation.
 *
 * @param id the automation id
 * @param input the new definition
 * @returns the persisted automation
 */
export function updateAutomation(id: number, input: AutomationInput): Promise<Automation> {
  return request<Automation>(`/api/automations/${id}`, {
    method: 'PUT',
    headers: JSON_HEADERS,
    body: JSON.stringify(input),
  })
}

/**
 * Enables or disables an automation.
 *
 * @param id the automation id
 * @param enabled whether the automation should react to triggers
 * @returns the persisted automation
 */
export function setAutomationEnabled(id: number, enabled: boolean): Promise<Automation> {
  return request<Automation>(`/api/automations/${id}/${enabled ? 'enable' : 'disable'}`, {
    method: 'POST',
  })
}

/**
 * Runs an automation's actions now, for a manual test.
 *
 * @param id the automation id
 */
export function runAutomation(id: number): Promise<void> {
  return request<void>(`/api/automations/${id}/run`, { method: 'POST' })
}

/**
 * Deletes an automation.
 *
 * @param id the automation id
 */
export function deleteAutomation(id: number): Promise<void> {
  return request<void>(`/api/automations/${id}`, { method: 'DELETE' })
}
