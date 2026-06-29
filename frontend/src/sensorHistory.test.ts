import { describe, expect, it } from 'vitest'
import type { Device } from './api/devices'
import { accumulateHistory, forgetDevice, seriesKey } from './sensorHistory'

function reading(updatedAt: string, value: string): Device {
  return {
    id: 1,
    externalId: 'living-room',
    name: 'living-room',
    type: 'SENSOR_NODE',
    capabilities: ['SENSING'],
    adapterType: null,
    state: {},
    sensors: [{ key: 'temperature', type: 'TEMPERATURE', unit: '°C', value, updatedAt }],
  }
}

describe('accumulateHistory', () => {
  it('appends a point per new reading timestamp', () => {
    let history = accumulateHistory({}, [reading('2026-06-30T00:00:00Z', '21.0')])
    history = accumulateHistory(history, [reading('2026-06-30T00:00:05Z', '21.5')])
    history = accumulateHistory(history, [reading('2026-06-30T00:00:10Z', '22.0')])

    const series = history[seriesKey(1, 'temperature')]
    expect(series).toHaveLength(3)
    expect(series.map((point) => point.v)).toEqual([21, 21.5, 22])
  })

  it('ignores a repeated reading timestamp and returns the same reference', () => {
    const history = accumulateHistory({}, [reading('2026-06-30T00:00:00Z', '21.0')])

    const again = accumulateHistory(history, [reading('2026-06-30T00:00:00Z', '21.0')])

    expect(again).toBe(history)
    expect(again[seriesKey(1, 'temperature')]).toHaveLength(1)
  })

  it('skips sensors without a numeric reading', () => {
    const node = reading('2026-06-30T00:00:00Z', '21.0')
    node.sensors[0].value = null

    expect(accumulateHistory({}, [node])).toEqual({})
  })

  it('skips a reading with an unparseable timestamp', () => {
    expect(accumulateHistory({}, [reading('not-a-date', '21.0')])).toEqual({})
  })

  it('forgets the series of a deleted device', () => {
    const node = reading('2026-06-30T00:00:00Z', '21.0')
    const history = accumulateHistory({}, [node])

    const pruned = forgetDevice(history, node)

    expect(seriesKey(1, 'temperature') in pruned).toBe(false)
  })
})
