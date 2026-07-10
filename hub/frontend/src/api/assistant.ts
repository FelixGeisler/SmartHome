import { request } from './devices'

export interface ChatReply {
  reply: string
}

/** Sends a message to the in-app assistant, which may read telemetry and control devices to answer. */
export function sendChat(message: string): Promise<ChatReply> {
  return request<ChatReply>('/api/assistant/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message }),
  })
}

export interface AssistantStatus {
  configured: boolean
}

export function assistantStatus(): Promise<AssistantStatus> {
  return request<AssistantStatus>('/api/assistant/status')
}

/** Sets the assistant's Anthropic API key; persisted in the hub's settings and restored on restart. */
export function setAssistantKey(apiKey: string): Promise<AssistantStatus> {
  return request<AssistantStatus>('/api/assistant/key', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ apiKey }),
  })
}
