import { request } from './devices'
import type { Room } from './rooms'

/** A floor (storey) as returned by the SmartHome REST API. */
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

/**
 * Creates a floor.
 *
 * @param name the floor name
 * @returns the created floor
 */
export function createFloor(name: string): Promise<Floor> {
  return request<Floor>('/api/floors', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
}

/**
 * Renames a floor.
 *
 * @param id the floor id
 * @param name the new name
 * @returns the updated floor
 */
export function renameFloor(id: number, name: string): Promise<Floor> {
  return request<Floor>(`/api/floors/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
}

/**
 * Deletes a floor; its rooms fall back to unassigned.
 *
 * @param id the floor id
 */
export function deleteFloor(id: number): Promise<void> {
  return request<void>(`/api/floors/${id}`, { method: 'DELETE' })
}

/**
 * Assigns a room to a floor.
 *
 * @param roomId the room id
 * @param floorId the floor id
 * @returns the updated room
 */
export function assignRoomToFloor(roomId: number, floorId: number): Promise<Room> {
  return request<Room>(`/api/rooms/${roomId}/floor`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ floorId }),
  })
}

/**
 * Removes a room from its floor, leaving it unassigned.
 *
 * @param roomId the room id
 * @returns the updated room
 */
export function clearRoomFloor(roomId: number): Promise<Room> {
  return request<Room>(`/api/rooms/${roomId}/floor`, { method: 'DELETE' })
}
