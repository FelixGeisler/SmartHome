import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Automation, AutomationInput } from './automations'
import {
  createAutomation,
  deleteAutomation,
  listAutomations,
  runAutomation,
  setAutomationEnabled,
  updateAutomation,
} from './automations'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const automation: Automation = {
  id: 1,
  name: 'Vent the office',
  enabled: true,
  triggers: [],
  conditions: [],
  actions: [],
}

const input: AutomationInput = {
  name: 'Vent the office',
  enabled: true,
  triggers: [
    {
      kind: 'SENSOR_THRESHOLD',
      deviceId: 10,
      sensorKey: 'temperature',
      comparison: 'GREATER_THAN',
      threshold: 25,
    },
  ],
  conditions: [],
  actions: [{ kind: 'DEVICE_TOGGLE', deviceId: 20, on: null, brightness: null, colorTemperatureK: null }],
}

describe('automations api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists automations from GET /api/automations', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([automation]))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listAutomations()).resolves.toEqual([automation])

    expect(fetchMock).toHaveBeenCalledWith('/api/automations', undefined)
  })

  it('creates an automation via POST /api/automations', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(automation, 201))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createAutomation(input)).resolves.toEqual(automation)

    expect(fetchMock).toHaveBeenCalledWith('/api/automations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })
  })

  it('replaces an automation via PUT /api/automations/{id}', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(automation))
    vi.stubGlobal('fetch', fetchMock)

    await updateAutomation(1, input)

    expect(fetchMock).toHaveBeenCalledWith('/api/automations/1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })
  })

  it('enables an automation via POST /api/automations/{id}/enable', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(automation))
    vi.stubGlobal('fetch', fetchMock)

    await setAutomationEnabled(1, true)

    expect(fetchMock).toHaveBeenCalledWith('/api/automations/1/enable', { method: 'POST' })
  })

  it('disables an automation via POST /api/automations/{id}/disable', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ...automation, enabled: false }))
    vi.stubGlobal('fetch', fetchMock)

    await setAutomationEnabled(1, false)

    expect(fetchMock).toHaveBeenCalledWith('/api/automations/1/disable', { method: 'POST' })
  })

  it('runs an automation via POST /api/automations/{id}/run', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(runAutomation(1)).resolves.toBeUndefined()

    expect(fetchMock).toHaveBeenCalledWith('/api/automations/1/run', { method: 'POST' })
  })

  it('deletes an automation via DELETE /api/automations/{id}', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await deleteAutomation(1)

    expect(fetchMock).toHaveBeenCalledWith('/api/automations/1', { method: 'DELETE' })
  })

  it('surfaces the detail of an error response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ detail: 'Automation not found: 9' }, 404)),
    )

    await expect(runAutomation(9)).rejects.toThrow('Automation not found: 9')
  })
})
