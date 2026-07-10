import { request } from './devices'

export interface ConnectionStatus {
  connected: boolean
  message: string
}

/** Connects the hub to a Solakon inverter over Modbus TCP; the backend defaults port to 502 and unit id to 1 when omitted. */
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

export function solakonStatus(): Promise<ConnectionStatus> {
  return request<ConnectionStatus>('/api/integrations/solakon/status')
}

export function disconnectSolakon(): Promise<ConnectionStatus> {
  return request<ConnectionStatus>('/api/integrations/solakon/disconnect', { method: 'POST' })
}
