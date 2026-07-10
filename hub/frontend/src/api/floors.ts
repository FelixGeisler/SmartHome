import { request } from './devices'
import type { Room } from './rooms'

export interface Floor {
  id: number
  name: string
  /** Sort order among floors, from the lowest storey to the highest. */
  level: number
}

/** Lists all floors, ordered from the lowest storey to the highest. */
export function listFloors(): Promise<Floor[]> {
  return request<Floor[]>('/api/floors')
}

export function createFloor(name: string): Promise<Floor> {
  return request<Floor>('/api/floors', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
}

export function renameFloor(id: number, name: string): Promise<Floor> {
  return request<Floor>(`/api/floors/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
}

/** Deletes a floor; its rooms fall back to unassigned. */
export function deleteFloor(id: number): Promise<void> {
  return request<void>(`/api/floors/${id}`, { method: 'DELETE' })
}

export function assignRoomToFloor(roomId: number, floorId: number): Promise<Room> {
  return request<Room>(`/api/rooms/${roomId}/floor`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ floorId }),
  })
}

export function clearRoomFloor(roomId: number): Promise<Room> {
  return request<Room>(`/api/rooms/${roomId}/floor`, { method: 'DELETE' })
}
