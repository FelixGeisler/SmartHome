import { request } from './devices'

/** One historical sensor reading, as served by the telemetry history endpoint. */
export interface ReadingPoint {
  /** When the reading was taken, as an ISO-8601 instant. */
  timestamp: string
  /** The numeric reading value. */
  value: number
}

/**
 * Fetches a sensor's reading history from the streaming store (Elasticsearch, via the hub),
 * oldest first. The series is keyed by the device's external id, which is what the hub publishes
 * onto the telemetry topic.
 *
 * @param deviceExternalId the device's external id
 * @param sensorKey the sensor's key within its device
 * @param hours how many hours back to read
 * @returns the readings over that window
 */
export function fetchSensorHistory(
  deviceExternalId: string,
  sensorKey: string,
  hours = 24,
): Promise<ReadingPoint[]> {
  const query = new URLSearchParams({
    deviceId: deviceExternalId,
    sensorKey,
    hours: String(hours),
  })
  return request<ReadingPoint[]>(`/api/telemetry/history?${query.toString()}`)
}
