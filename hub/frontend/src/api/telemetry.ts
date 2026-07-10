import { request } from './devices'

export interface ReadingPoint {
  /** When the reading was taken, as an ISO-8601 instant. */
  timestamp: string
  value: number
}

/** Fetches a sensor's reading history (oldest first), keyed by the device's external id. */
export function fetchSensorHistory(
  deviceExternalId: string,
  sensorKey: string,
  hours: number,
): Promise<ReadingPoint[]> {
  const query = new URLSearchParams({
    deviceId: deviceExternalId,
    sensorKey,
    hours: String(hours),
  })
  return request<ReadingPoint[]>(`/api/telemetry/history?${query.toString()}`)
}
