import { request } from './devices'

/** Result of a Solakon inverter connection attempt or a status query. */
export interface ConnectionStatus {
  connected: boolean
  message: string
}

/**
 * Connects the hub to a Solakon inverter over Modbus TCP. The backend defaults the port to 502 and
 * the unit id to 1 when they are omitted.
 */
export function connectSolakon(
  host: string,
  port?: number,
  unitId?: number,
): Promise<ConnectionStatus> {
  const body: { host: string; port?: number; unitId?: number } = { host }
  if (port !== undefined) {
    body.port = port
  }
  if (unitId !== undefined) {
    body.unitId = unitId
  }
  return request<ConnectionStatus>('/api/integrations/solakon/connect', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

/** Reports whether the hub is currently connected to a Solakon inverter. */
export function solakonStatus(): Promise<ConnectionStatus> {
  return request<ConnectionStatus>('/api/integrations/solakon/status')
}

/** Disconnects the hub from its Solakon inverter. */
export function disconnectSolakon(): Promise<ConnectionStatus> {
  return request<ConnectionStatus>('/api/integrations/solakon/disconnect', { method: 'POST' })
}
