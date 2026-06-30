import { request } from './devices'

/** The assistant's answer to a chat message. */
export interface ChatReply {
  reply: string
}

/**
 * Sends a message to the in-app assistant, which may read telemetry and control devices to answer.
 *
 * @param message the user's message
 * @returns the assistant's reply
 */
export function sendChat(message: string): Promise<ChatReply> {
  return request<ChatReply>('/api/assistant/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message }),
  })
}

/** Whether the assistant has an API key configured. */
export interface AssistantStatus {
  configured: boolean
}

/** Reports whether the assistant has an API key set. */
export function assistantStatus(): Promise<AssistantStatus> {
  return request<AssistantStatus>('/api/assistant/status')
}

/**
 * Sets the assistant's Anthropic API key on the hub. The key is persisted in the hub's settings and
 * restored on the next restart.
 *
 * @param apiKey the API key to use
 * @returns the resulting configuration status
 */
export function setAssistantKey(apiKey: string): Promise<AssistantStatus> {
  return request<AssistantStatus>('/api/assistant/key', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ apiKey }),
  })
}
