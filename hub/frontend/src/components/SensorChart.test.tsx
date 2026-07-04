import { act, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Sensor } from '../api/devices'
import { SensorChart } from './SensorChart'

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function sensor(overrides: Partial<Sensor> = {}): Sensor {
  return { key: 'temp', type: 'TEMPERATURE', unit: '°C', value: null, updatedAt: null, ...overrides }
}

const storedPoints = [
  { timestamp: '2026-06-30T08:00:00Z', value: 21.5 },
  { timestamp: '2026-06-30T08:05:00Z', value: 22.5 },
]

describe('SensorChart', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('reads the sensor history from the hub and draws a line', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(storedPoints))
    vi.stubGlobal('fetch', fetchMock)

    render(<SensorChart deviceExternalId="dev-1" sensor={sensor()} />)

    expect(
      await screen.findByRole('img', { name: 'temp history, 2 readings' }),
    ).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/telemetry/history?deviceId=dev-1&sensorKey=temp&hours=24',
      undefined,
    )
  })

  it('shows a baseline placeholder when the sensor has no stored history yet', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([])))

    const { container } = render(<SensorChart deviceExternalId="dev-1" sensor={sensor()} />)

    await waitFor(() => {
      expect(container.querySelector('.sensor-chart--empty')).toBeInTheDocument()
    })
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('appends a newer reading pushed over the stream to the drawn series', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(storedPoints)))

    const { rerender } = render(<SensorChart deviceExternalId="dev-1" sensor={sensor()} />)
    await screen.findByRole('img', { name: 'temp history, 2 readings' })

    rerender(
      <SensorChart
        deviceExternalId="dev-1"
        sensor={sensor({ value: '23.5', updatedAt: '2026-06-30T08:10:00Z' })}
      />,
    )

    expect(
      await screen.findByRole('img', { name: 'temp history, 3 readings' }),
    ).toBeInTheDocument()
  })

  it('ignores a pushed reading no newer than the last point', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(storedPoints)))

    const { rerender } = render(<SensorChart deviceExternalId="dev-1" sensor={sensor()} />)
    await screen.findByRole('img', { name: 'temp history, 2 readings' })

    rerender(
      <SensorChart
        deviceExternalId="dev-1"
        sensor={sensor({ value: '22.5', updatedAt: '2026-06-30T08:05:00Z' })}
      />,
    )

    // Same timestamp as the last stored point, so the series stays at two readings.
    expect(screen.getByRole('img', { name: 'temp history, 2 readings' })).toBeInTheDocument()
  })

  it('ignores a blank pushed value instead of plotting it as zero', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(storedPoints)))

    const { rerender } = render(<SensorChart deviceExternalId="dev-1" sensor={sensor()} />)
    await screen.findByRole('img', { name: 'temp history, 2 readings' })

    rerender(
      <SensorChart
        deviceExternalId="dev-1"
        sensor={sensor({ value: '  ', updatedAt: '2026-06-30T08:10:00Z' })}
      />,
    )

    expect(screen.getByRole('img', { name: 'temp history, 2 readings' })).toBeInTheDocument()
  })

  it('ignores a pushed reading whose timestamp does not parse', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(storedPoints)))

    const { rerender } = render(<SensorChart deviceExternalId="dev-1" sensor={sensor()} />)
    await screen.findByRole('img', { name: 'temp history, 2 readings' })

    rerender(
      <SensorChart
        deviceExternalId="dev-1"
        sensor={sensor({ value: '23.5', updatedAt: 'not-a-timestamp' })}
      />,
    )

    expect(screen.getByRole('img', { name: 'temp history, 2 readings' })).toBeInTheDocument()
  })

  it('keeps live-appended points when a reconnect refetches an older history window', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(storedPoints))
    vi.stubGlobal('fetch', fetchMock)

    const { rerender } = render(
      <SensorChart deviceExternalId="dev-1" sensor={sensor()} syncToken={0} />,
    )
    await screen.findByRole('img', { name: 'temp history, 2 readings' })
    rerender(
      <SensorChart
        deviceExternalId="dev-1"
        sensor={sensor({ value: '23.5', updatedAt: '2026-06-30T08:10:00Z' })}
        syncToken={0}
      />,
    )
    await screen.findByRole('img', { name: 'temp history, 3 readings' })

    // A reconnect bumps the token; the search index still lags, so the refetch returns the same
    // two stored points. The appended live reading must survive the merge.
    rerender(
      <SensorChart
        deviceExternalId="dev-1"
        sensor={sensor({ value: '23.5', updatedAt: '2026-06-30T08:10:00Z' })}
        syncToken={1}
      />,
    )

    expect(
      await screen.findByRole('img', { name: 'temp history, 3 readings' }),
    ).toBeInTheDocument()
    // Pins the refetch itself: the token bump must reload the window, not just keep state.
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
  })

  it('prunes points that have left the lookback window when appending', async () => {
    const aged = [
      { timestamp: '2026-06-29T07:00:00Z', value: 20.0 }, // > 24h before the appended reading
      { timestamp: '2026-06-30T08:05:00Z', value: 22.5 },
    ]
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(aged)))

    const { rerender } = render(<SensorChart deviceExternalId="dev-1" sensor={sensor()} />)
    await screen.findByRole('img', { name: 'temp history, 2 readings' })

    rerender(
      <SensorChart
        deviceExternalId="dev-1"
        sensor={sensor({ value: '23.5', updatedAt: '2026-06-30T08:10:00Z' })}
      />,
    )

    // The aged point drops out as the new one comes in: still two readings, not three.
    expect(
      await screen.findByRole('img', { name: 'temp history, 2 readings' }),
    ).toBeInTheDocument()
  })

  it('retries a failed history load on its own', async () => {
    vi.useFakeTimers()
    try {
      const fetchMock = vi
        .fn()
        .mockRejectedValueOnce(new Error('search index unreachable'))
        .mockResolvedValueOnce(jsonResponse(storedPoints))
      vi.stubGlobal('fetch', fetchMock)

      render(<SensorChart deviceExternalId="dev-1" sensor={sensor()} />)
      await act(async () => {
        await vi.advanceTimersByTimeAsync(15_000)
      })

      expect(screen.getByRole('img', { name: 'temp history, 2 readings' })).toBeInTheDocument()
      expect(fetchMock).toHaveBeenCalledTimes(2)
    } finally {
      vi.useRealTimers()
    }
  })
})
