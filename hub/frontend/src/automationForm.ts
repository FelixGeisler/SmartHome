import type { Device } from './api/devices'
import type {
  ActionKind,
  Automation,
  AutomationInput,
  AutomationTrigger,
  Comparison,
  TriggerKind,
} from './api/automations'

/** The trigger being edited: a sensor threshold, or a schedule (time of day + days of week). */
export interface TriggerDraft {
  kind: TriggerKind
  deviceId: string
  sensorKey: string
  comparison: Comparison
  threshold: string
  atTime: string
  onDays: string[]
}

/** A device-state condition being edited: the device must be on or off. */
export interface ConditionDraft {
  /** A stable client-only id, so removing a row keeps React keyed by row, not by index. */
  key: string
  deviceId: string
  expected: 'true' | 'false'
}

/** An action being edited: a toggle, or a command setting any of power, brightness, or color temp. */
export interface ActionDraft {
  /** A stable client-only id, so removing a row keeps React keyed by row, not by index. */
  key: string
  deviceId: string
  kind: ActionKind
  power: '' | 'on' | 'off'
  brightness: string
  colorTemperatureK: string
}

let rowKeySeq = 0

/** A stable id for a new builder row, so React reconciles editable rows by identity, not position. */
function nextRowKey(): string {
  rowKeySeq += 1
  return `row-${rowKeySeq}`
}

/** The whole automation being edited, with form-friendly string fields. */
export interface AutomationDraft {
  id: number | null
  name: string
  enabled: boolean
  trigger: TriggerDraft
  conditions: ConditionDraft[]
  actions: ActionDraft[]
}

/** The comparison choices offered in the builder, with human-readable labels. */
export const COMPARISON_OPTIONS: ReadonlyArray<{ value: Comparison; label: string }> = [
  { value: 'GREATER_THAN', label: 'rises above (>)' },
  { value: 'GREATER_THAN_OR_EQUAL', label: 'reaches (≥)' },
  { value: 'LESS_THAN', label: 'drops below (<)' },
  { value: 'LESS_THAN_OR_EQUAL', label: 'falls to (≤)' },
]

const COMPARISON_SYMBOLS: Record<Comparison, string> = {
  GREATER_THAN: '>',
  GREATER_THAN_OR_EQUAL: '≥',
  LESS_THAN: '<',
  LESS_THAN_OR_EQUAL: '≤',
}

/** The math symbol for a comparison. */
export function comparisonSymbol(comparison: Comparison): string {
  return COMPARISON_SYMBOLS[comparison]
}

/** The days of the week offered as checkboxes in the schedule builder, Monday first. */
export const DAY_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'MONDAY', label: 'Mon' },
  { value: 'TUESDAY', label: 'Tue' },
  { value: 'WEDNESDAY', label: 'Wed' },
  { value: 'THURSDAY', label: 'Thu' },
  { value: 'FRIDAY', label: 'Fri' },
  { value: 'SATURDAY', label: 'Sat' },
  { value: 'SUNDAY', label: 'Sun' },
]

const DAY_LABELS: Record<string, string> = Object.fromEntries(
  DAY_OPTIONS.map((day) => [day.value, day.label]),
)

/** A blank automation to start the builder from. */
export function emptyDraft(): AutomationDraft {
  return {
    id: null,
    name: '',
    enabled: true,
    trigger: {
      kind: 'SENSOR_THRESHOLD',
      deviceId: '',
      sensorKey: '',
      comparison: 'GREATER_THAN',
      threshold: '',
      atTime: '',
      onDays: [],
    },
    conditions: [],
    actions: [],
  }
}

/** A blank condition row. */
export function emptyCondition(): ConditionDraft {
  return { key: nextRowKey(), deviceId: '', expected: 'true' }
}

/** A blank action row. */
export function emptyAction(): ActionDraft {
  return {
    key: nextRowKey(),
    deviceId: '',
    kind: 'DEVICE_TOGGLE',
    power: '',
    brightness: '',
    colorTemperatureK: '',
  }
}

/** Loads an existing automation into an editable draft. */
export function draftFromAutomation(automation: Automation): AutomationDraft {
  const trigger = automation.triggers[0]
  return {
    id: automation.id,
    name: automation.name,
    enabled: automation.enabled,
    trigger: {
      kind: trigger?.kind ?? 'SENSOR_THRESHOLD',
      deviceId: trigger?.deviceId != null ? String(trigger.deviceId) : '',
      sensorKey: trigger?.sensorKey ?? '',
      comparison: trigger?.comparison ?? 'GREATER_THAN',
      threshold: trigger?.threshold != null ? String(trigger.threshold) : '',
      atTime: trigger?.atTime != null ? trigger.atTime.slice(0, 5) : '',
      onDays: trigger?.onDays ?? [],
    },
    conditions: automation.conditions.map((condition) => ({
      key: nextRowKey(),
      deviceId: String(condition.deviceId),
      expected: condition.expected === 'false' ? 'false' : 'true',
    })),
    actions: automation.actions.map((action) => ({
      key: nextRowKey(),
      deviceId: String(action.deviceId),
      kind: action.kind,
      power: action.on === null ? '' : action.on ? 'on' : 'off',
      brightness: action.brightness != null ? String(action.brightness) : '',
      colorTemperatureK: action.colorTemperatureK != null ? String(action.colorTemperatureK) : '',
    })),
  }
}

function triggerToInput(trigger: TriggerDraft): AutomationTrigger {
  if (trigger.kind === 'SCHEDULE') {
    return {
      kind: 'SCHEDULE',
      deviceId: null,
      sensorKey: null,
      comparison: null,
      threshold: null,
      atTime: trigger.atTime,
      onDays: trigger.onDays,
    }
  }
  return {
    kind: 'SENSOR_THRESHOLD',
    deviceId: Number(trigger.deviceId),
    sensorKey: trigger.sensorKey,
    comparison: trigger.comparison,
    threshold: Number(trigger.threshold),
    atTime: null,
    onDays: [],
  }
}

/** Converts a draft into the request body the API expects. */
export function draftToInput(draft: AutomationDraft): AutomationInput {
  return {
    name: draft.name.trim(),
    enabled: draft.enabled,
    triggers: [triggerToInput(draft.trigger)],
    conditions: draft.conditions.map((condition) => ({
      kind: 'DEVICE_STATE',
      deviceId: Number(condition.deviceId),
      stateKey: 'on',
      expected: condition.expected,
    })),
    actions: draft.actions.map((action) =>
      action.kind === 'DEVICE_TOGGLE'
        ? {
            kind: 'DEVICE_TOGGLE',
            deviceId: Number(action.deviceId),
            on: null,
            brightness: null,
            colorTemperatureK: null,
          }
        : {
            kind: 'DEVICE_COMMAND',
            deviceId: Number(action.deviceId),
            on: action.power === '' ? null : action.power === 'on',
            brightness: action.brightness === '' ? null : Number(action.brightness),
            colorTemperatureK:
              action.colorTemperatureK === '' ? null : Number(action.colorTemperatureK),
          },
    ),
  }
}

/** Returns the first problem with a draft as a message, or null when it is ready to save. */
export function draftError(draft: AutomationDraft): string | null {
  if (draft.name.trim() === '') {
    return 'Give the automation a name.'
  }
  const trigger = draft.trigger
  if (trigger.kind === 'SCHEDULE') {
    if (trigger.atTime === '') {
      return 'Choose a time for the schedule.'
    }
  } else {
    if (trigger.deviceId === '' || trigger.sensorKey === '') {
      return 'Choose a sensor for the trigger.'
    }
    if (trigger.threshold === '' || Number.isNaN(Number(trigger.threshold))) {
      return 'Enter a numeric threshold.'
    }
  }
  for (const condition of draft.conditions) {
    if (condition.deviceId === '') {
      return 'Choose a device for every condition.'
    }
  }
  if (draft.actions.length === 0) {
    return 'Add at least one action.'
  }
  for (const action of draft.actions) {
    if (action.deviceId === '') {
      return 'Choose a device for every action.'
    }
    if (
      action.kind === 'DEVICE_COMMAND' &&
      action.power === '' &&
      action.brightness === '' &&
      action.colorTemperatureK === ''
    ) {
      return 'A set-state action needs a power, brightness, or color temperature.'
    }
  }
  return null
}

/** Devices that report sensor readings, for the trigger picker. */
export function sensingDevices(devices: Device[]): Device[] {
  return devices.filter((device) => device.sensors.length > 0)
}

/** Devices reachable by a command adapter, for the action picker. */
export function commandDevices(devices: Device[]): Device[] {
  return devices.filter((device) => device.adapterType !== null)
}

/** Devices that can be on or off, for the condition picker. */
export function switchableDevices(devices: Device[]): Device[] {
  return devices.filter((device) => device.capabilities.includes('SWITCHABLE'))
}

/** A short, readable one-line summary of an automation for the list. */
export function summarize(automation: Automation, devices: Device[]): string {
  const nameOf = (id: number | null): string =>
    id == null ? 'a device' : (devices.find((device) => device.id === id)?.name ?? `device ${id}`)
  const trigger = automation.triggers[0]
  let when: string
  if (!trigger) {
    when = 'When triggered'
  } else if (trigger.kind === 'SCHEDULE') {
    when = `At ${trigger.atTime?.slice(0, 5) ?? ''}${describeDays(trigger.onDays)}`
  } else {
    const symbol = trigger.comparison ? comparisonSymbol(trigger.comparison) : ''
    when = `When ${nameOf(trigger.deviceId)} ${trigger.sensorKey ?? ''} ${symbol} ${trigger.threshold ?? ''}`
  }
  const ifs = automation.conditions
    .map((condition) => `${nameOf(condition.deviceId)} is ${condition.expected === 'true' ? 'on' : 'off'}`)
    .join(' and ')
  const thens = automation.actions
    .map((action) =>
      action.kind === 'DEVICE_TOGGLE'
        ? `toggle ${nameOf(action.deviceId)}`
        : `set ${nameOf(action.deviceId)}`,
    )
    .join(', ')
  return `${when}${ifs ? ` and ${ifs}` : ''}, then ${thens || 'do nothing'}`
}

function describeDays(days: string[]): string {
  if (days.length === 0) {
    return ' every day'
  }
  return ` on ${days.map((day) => DAY_LABELS[day] ?? day).join(', ')}`
}
