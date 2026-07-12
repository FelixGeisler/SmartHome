import { request } from './devices'

export interface ConnectionStatus {
  connected: boolean
  message: string
}

/** Connects the hub to a Solakon IR meter head over HTTP, polling its grid metering. */
export function connectSolakonIr(host: string): Promise<ConnectionStatus> {
  return request<ConnectionStatus>('/api/integrations/solakon-ir/connect', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ host }),
  })
}

export function solakonIrStatus(): Promise<ConnectionStatus> {
  return request<ConnectionStatus>('/api/integrations/solakon-ir/status')
}

export function disconnectSolakonIr(): Promise<ConnectionStatus> {
  return request<ConnectionStatus>('/api/integrations/solakon-ir/disconnect', { method: 'POST' })
}
