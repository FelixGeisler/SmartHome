import { describe, expect, it } from 'vitest'
import type { Device, Sensor } from './api/devices'
import {
  deviceIconKind,
  isTemperatureReading,
  primarySensor,
  sensorLabel,
  temperatureColor,
} from './deviceIcon'

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

function sensor(type: string): Sensor {
  return { key: type.toLowerCase(), type, unit: '', value: '1', updatedAt: null }
}

describe('deviceIconKind', () => {
  it('symbolizes a color or dimmable device as a bulb', () => {
    expect(deviceIconKind(device({ capabilities: ['SWITCHABLE', 'DIMMABLE'] }))).toBe('bulb')
  })

  it('symbolizes a plain Hue light as a bulb', () => {
    expect(deviceIconKind(device({ type: 'HUE_LIGHT', capabilities: ['SWITCHABLE'] }))).toBe('bulb')
  })

  it('symbolizes a plain switch as a plug', () => {
    expect(deviceIconKind(device({ type: 'SHELLY_PLUG', capabilities: ['SWITCHABLE'] }))).toBe(
      'plug',
    )
  })

  it('symbolizes the solar inverter as a sun even though it reports temperatures', () => {
    const inverter = device({
      type: 'SOLAR_INVERTER',
      capabilities: ['SENSING'],
      sensors: [sensor('PV_POWER'), sensor('INVERTER_TEMPERATURE')],
    })

    expect(deviceIconKind(inverter)).toBe('solar')
  })

  it('symbolizes a temperature sensor as a thermometer', () => {
    const node = device({ type: 'SENSOR_NODE', capabilities: ['SENSING'], sensors: [sensor('TEMPERATURE')] })

    expect(deviceIconKind(node)).toBe('thermometer')
  })

  it('symbolizes a humidity-only sensor as a droplet', () => {
    const node = device({ type: 'SENSOR_NODE', capabilities: ['SENSING'], sensors: [sensor('HUMIDITY')] })

    expect(deviceIconKind(node)).toBe('humidity')
  })

  it('falls back to a plug for a device with no capabilities', () => {
    expect(deviceIconKind(device({ type: 'SHELLY_PLUG', capabilities: [] }))).toBe('plug')
  })
})

describe('primarySensor', () => {
  it('prefers temperature over a later channel', () => {
    const node = device({ sensors: [sensor('HUMIDITY'), sensor('TEMPERATURE')] })

    expect(primarySensor(node)?.type).toBe('TEMPERATURE')
  })

  it('prefers produced power for a solar inverter', () => {
    const inverter = device({ sensors: [sensor('INVERTER_TEMPERATURE'), sensor('PV_POWER')] })

    expect(primarySensor(inverter)?.type).toBe('PV_POWER')
  })

  it('uses the first declared sensor when no preferred type is present', () => {
    const node = device({ sensors: [sensor('PRESSURE'), sensor('CO2')] })

    expect(primarySensor(node)?.type).toBe('PRESSURE')
  })

  it('returns null for a device with no sensors', () => {
    expect(primarySensor(device({ sensors: [] }))).toBeNull()
  })
})

describe('isTemperatureReading', () => {
  it('is true for any temperature channel', () => {
    expect(isTemperatureReading(sensor('TEMPERATURE'))).toBe(true)
    expect(isTemperatureReading(sensor('INVERTER_TEMPERATURE'))).toBe(true)
  })

  it('is false for a non-temperature channel', () => {
    expect(isTemperatureReading(sensor('HUMIDITY'))).toBe(false)
  })
})

describe('temperatureColor', () => {
  it('clamps to the coldest stop when very cold and the hottest when very hot', () => {
    expect(temperatureColor(2)).toBe('rgb(74, 163, 255)')
    expect(temperatureColor(35)).toBe('rgb(255, 59, 48)')
  })

  it('interpolates to a color between the surrounding stops', () => {
    const mid = temperatureColor(21)

    expect(mid).not.toBe(temperatureColor(18))
    expect(mid).not.toBe(temperatureColor(24))
  })
})

describe('sensorLabel', () => {
  function reading(key: string): Sensor {
    return { key, type: 'X', unit: '', value: '1', updatedAt: null }
  }

  it('title-cases a camelCase key', () => {
    expect(sensorLabel(reading('batteryTemp'))).toBe('Battery Temp')
    expect(sensorLabel(reading('inverterTemp'))).toBe('Inverter Temp')
    expect(sensorLabel(reading('outputPower'))).toBe('Output Power')
  })

  it('keeps known acronyms uppercase', () => {
    expect(sensorLabel(reading('pvPower'))).toBe('PV Power')
    expect(sensorLabel(reading('co2'))).toBe('CO2')
    expect(sensorLabel(reading('batterySoc'))).toBe('Battery SoC')
  })

  it('title-cases a plain key', () => {
    expect(sensorLabel(reading('humidity'))).toBe('Humidity')
  })
})
