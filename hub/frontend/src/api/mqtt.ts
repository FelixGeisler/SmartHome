import { request } from './devices'

export interface ConnectionStatus {
  connected: boolean
  message: string
}

/** Connects the hub to an MQTT broker. The backend defaults the port to 1883 when omitted. */
export function connectMqtt(host: string, port?: number): Promise<ConnectionStatus> {
  const body = port === undefined ? { host } : { host, port }
  return request<ConnectionStatus>('/api/integrations/mqtt/connect', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function mqttStatus(): Promise<ConnectionStatus> {
  return request<ConnectionStatus>('/api/integrations/mqtt/status')
}

export function disconnectMqtt(): Promise<ConnectionStatus> {
  return request<ConnectionStatus>('/api/integrations/mqtt/disconnect', { method: 'POST' })
}
