import { describe, expect, it } from 'vitest'
import type { Device } from './api/devices'
import type { Automation } from './api/automations'
import {
  commandDevices,
  comparisonSymbol,
  draftError,
  draftFromAutomation,
  draftToInput,
  emptyAction,
  emptyDraft,
  sensingDevices,
  summarize,
  switchableDevices,
} from './automationForm'

function device(overrides: Partial<Device>): Device {
  return {
    id: 1,
    externalId: 'ext-1',
    name: 'Device',
    type: 'SHELLY_PLUG',
    capabilities: [],
    adapterType: null,
    state: {},
    sensors: [],
    ...overrides,
  }
}

const sensorNode = device({
  id: 10,
  name: 'Office Sensor',
  capabilities: ['SENSING'],
  sensors: [{ key: 'temperature', type: 'TEMPERATURE', unit: '°C', value: '21', updatedAt: null }],
})

const fan = device({
  id: 20,
  name: 'Fan',
  capabilities: ['SWITCHABLE'],
  adapterType: 'shelly',
})

const automation: Automation = {
  id: 1,
  name: 'Vent the office',
  enabled: true,
  triggers: [
    {
      kind: 'SENSOR_THRESHOLD',
      deviceId: 10,
      sensorKey: 'temperature',
      comparison: 'GREATER_THAN',
      threshold: 25,
      atTime: null,
      onDays: [],
    },
  ],
  conditions: [{ kind: 'DEVICE_STATE', deviceId: 20, stateKey: 'on', expected: 'false' }],
  actions: [{ kind: 'DEVICE_TOGGLE', deviceId: 20, on: null, brightness: null, colorTemperatureK: null }],
}

describe('draftToInput', () => {
  it('maps a threshold trigger, condition, and toggle action into the request body', () => {
    const draft = draftFromAutomation(automation)

    expect(draftToInput(draft)).toEqual({
      name: 'Vent the office',
      enabled: true,
      triggers: [
        {
          kind: 'SENSOR_THRESHOLD',
          deviceId: 10,
          sensorKey: 'temperature',
          comparison: 'GREATER_THAN',
          threshold: 25,
          atTime: null,
          onDays: [],
        },
      ],
      conditions: [{ kind: 'DEVICE_STATE', deviceId: 20, stateKey: 'on', expected: 'false' }],
      actions: [
        { kind: 'DEVICE_TOGGLE', deviceId: 20, on: null, brightness: null, colorTemperatureK: null },
      ],
    })
  })

  it('maps a command action, sending only the attributes that are set', () => {
    const draft = emptyDraft()
    draft.name = 'Warm up'
    draft.trigger = {
      kind: 'SENSOR_THRESHOLD',
      deviceId: '10',
      sensorKey: 'temperature',
      comparison: 'LESS_THAN',
      threshold: '18',
      atTime: '',
      onDays: [],
    }
    draft.actions = [
      { key: 'a1', deviceId: '20', kind: 'DEVICE_COMMAND', power: 'on', brightness: '60', colorTemperatureK: '' },
    ]

    const input = draftToInput(draft)

    expect(input.actions[0]).toEqual({
      kind: 'DEVICE_COMMAND',
      deviceId: 20,
      on: true,
      brightness: 60,
      colorTemperatureK: null,
    })
  })

  it('maps a schedule trigger with its time and days', () => {
    const draft = emptyDraft()
    draft.name = 'Morning'
    draft.trigger = {
      kind: 'SCHEDULE',
      deviceId: '',
      sensorKey: '',
      comparison: 'GREATER_THAN',
      threshold: '',
      atTime: '07:30',
      onDays: ['MONDAY', 'FRIDAY'],
    }
    draft.actions = [
      { key: 'a1', deviceId: '20', kind: 'DEVICE_TOGGLE', power: '', brightness: '', colorTemperatureK: '' },
    ]

    expect(draftToInput(draft).triggers[0]).toEqual({
      kind: 'SCHEDULE',
      deviceId: null,
      sensorKey: null,
      comparison: null,
      threshold: null,
      atTime: '07:30',
      onDays: ['MONDAY', 'FRIDAY'],
    })
  })
})

describe('draftError', () => {
  function validDraft() {
    const draft = emptyDraft()
    draft.name = 'Vent'
    draft.trigger = {
      kind: 'SENSOR_THRESHOLD',
      deviceId: '10',
      sensorKey: 'temperature',
      comparison: 'GREATER_THAN',
      threshold: '25',
      atTime: '',
      onDays: [],
    }
    draft.actions = [emptyAction()]
    draft.actions[0].deviceId = '20'
    return draft
  }

  it('accepts a complete draft', () => {
    expect(draftError(validDraft())).toBeNull()
  })

  it('requires a name', () => {
    const draft = validDraft()
    draft.name = '  '
    expect(draftError(draft)).toMatch(/name/i)
  })

  it('requires a numeric threshold', () => {
    const draft = validDraft()
    draft.trigger.threshold = 'hot'
    expect(draftError(draft)).toMatch(/threshold/i)
  })

  it('requires at least one action', () => {
    const draft = validDraft()
    draft.actions = []
    expect(draftError(draft)).toMatch(/action/i)
  })

  it('requires a command action to set at least one attribute', () => {
    const draft = validDraft()
    draft.actions = [
      { key: 'a1', deviceId: '20', kind: 'DEVICE_COMMAND', power: '', brightness: '', colorTemperatureK: '' },
    ]
    expect(draftError(draft)).toMatch(/power, brightness, or color temperature/i)
  })

  it('requires a time for a schedule', () => {
    const draft = validDraft()
    draft.trigger = { ...draft.trigger, kind: 'SCHEDULE', atTime: '' }
    expect(draftError(draft)).toMatch(/time/i)
  })
})

describe('summarize', () => {
  it('renders a readable one-line summary', () => {
    expect(summarize(automation, [sensorNode, fan])).toBe(
      'When Office Sensor temperature > 25 and Fan is off, then toggle Fan',
    )
  })

  it('renders a schedule summary with its time and days', () => {
    const scheduled: Automation = {
      id: 2,
      name: 'Morning',
      enabled: true,
      triggers: [
        {
          kind: 'SCHEDULE',
          deviceId: null,
          sensorKey: null,
          comparison: null,
          threshold: null,
          atTime: '07:30:00',
          onDays: ['MONDAY', 'FRIDAY'],
        },
      ],
      conditions: [],
      actions: [
        { kind: 'DEVICE_TOGGLE', deviceId: 20, on: null, brightness: null, colorTemperatureK: null },
      ],
    }

    expect(summarize(scheduled, [fan])).toBe('At 07:30 on Mon, Fri, then toggle Fan')
  })
})

describe('comparisonSymbol', () => {
  it('maps each comparison to its math symbol', () => {
    expect(comparisonSymbol('GREATER_THAN')).toBe('>')
    expect(comparisonSymbol('LESS_THAN_OR_EQUAL')).toBe('≤')
  })
})

describe('device filters', () => {
  it('offers only sensing devices for triggers', () => {
    expect(sensingDevices([sensorNode, fan])).toEqual([sensorNode])
  })

  it('offers only command devices for actions', () => {
    expect(commandDevices([sensorNode, fan])).toEqual([fan])
  })

  it('offers only switchable devices for conditions', () => {
    expect(switchableDevices([sensorNode, fan])).toEqual([fan])
  })
})
