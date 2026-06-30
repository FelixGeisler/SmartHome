import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SensorHistoryChart } from './SensorHistoryChart'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('SensorHistoryChart', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('loads and charts the sensor history on first open', async () => {
    const points = [
      { timestamp: '2026-06-30T08:00:00Z', value: 21.5 },
      { timestamp: '2026-06-30T08:05:00Z', value: 22.5 },
    ]
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(points))
    vi.stubGlobal('fetch', fetchMock)

    render(<SensorHistoryChart deviceExternalId="dev-1" sensorKey="temp" unit="°C" />)
    await userEvent.click(screen.getByRole('button', { name: /history/i }))

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/telemetry/history?deviceId=dev-1&sensorKey=temp&hours=24',
      undefined,
    )
    expect(
      await screen.findByRole('img', { name: 'History of the last 2 readings' }),
    ).toBeInTheDocument()
  })

  it('shows an error note when the history cannot be loaded', async () => {
    const problem = { detail: 'Sensor history is currently unavailable.' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(problem, 502)))

    render(<SensorHistoryChart deviceExternalId="dev-1" sensorKey="temp" unit="°C" />)
    await userEvent.click(screen.getByRole('button', { name: /history/i }))

    expect(
      await screen.findByText('Sensor history is currently unavailable.'),
    ).toBeInTheDocument()
  })
})
