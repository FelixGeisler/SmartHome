import { request, type Device } from './devices'

/** A room as returned by the SmartHome REST API. */
export interface Room {
  id: number
  name: string
  /** The id of the floor the room is on, or null when unassigned. */
  floorId?: number | null
  /** The name of the floor the room is on, or null when unassigned. */
  floorName?: string | null
}

/** One room's box on the floor plan: which room it draws and its position and size in grid units. */
export interface RoomBox {
  roomId: number
  x: number
  y: number
  w: number
  h: number
}

/** One device's placed position within its room, as a fraction (0..1) of the room content rect. */
export interface DevicePlacement {
  deviceId: number
  /** The icon center's horizontal position, 0..1 of the room content rect width. */
  fx: number
  /** The icon center's vertical position, 0..1 of the room content rect height. */
  fy: number
}

/** A saved floor-plan arrangement: each room's box placement and each placed device's spot. */
export interface RoomsLayout {
  rooms: RoomBox[]
  /** Placed device positions; absent means none yet (every icon falls back to a default slot). */
  devices?: DevicePlacement[]
}

/** Lists all rooms. */
export function listRooms(): Promise<Room[]> {
  return request<Room[]>('/api/rooms')
}

/**
 * Creates a room.
 *
 * @param name the room name
 * @returns the created room
 */
export function createRoom(name: string): Promise<Room> {
  return request<Room>('/api/rooms', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
}

/**
 * Renames a room.
 *
 * @param id the room id
 * @param name the new name
 * @returns the updated room
 */
export function renameRoom(id: number, name: string): Promise<Room> {
  return request<Room>(`/api/rooms/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
}

/**
 * Deletes a room; its devices fall back to unassigned.
 *
 * @param id the room id
 */
export function deleteRoom(id: number): Promise<void> {
  return request<void>(`/api/rooms/${id}`, { method: 'DELETE' })
}

/**
 * Assigns a device to a room.
 *
 * @param deviceId the device id
 * @param roomId the room id
 * @returns the updated device
 */
export function assignDeviceToRoom(deviceId: number, roomId: number): Promise<Device> {
  return request<Device>(`/api/devices/${deviceId}/room`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ roomId }),
  })
}

/**
 * Removes a device from its room, leaving it unassigned.
 *
 * @param deviceId the device id
 * @returns the updated device
 */
export function clearDeviceRoom(deviceId: number): Promise<Device> {
  return request<Device>(`/api/devices/${deviceId}/room`, { method: 'DELETE' })
}

/**
 * Reads the saved floor-plan layout, or null when none has been saved yet. The hub answers a
 * never-arranged floor plan with 204 (an empty body); surfacing that as null lets the caller tell a
 * first-run floor plan apart from one a user intentionally emptied.
 */
export async function getRoomsLayout(): Promise<RoomsLayout | null> {
  const layout = await request<RoomsLayout | undefined>('/api/rooms/layout')
  return layout ?? null
}

/**
 * Replaces the saved floor-plan layout.
 *
 * @param layout the layout to persist
 * @returns the saved layout
 */
export function saveRoomsLayout(layout: RoomsLayout): Promise<RoomsLayout> {
  return request<RoomsLayout>('/api/rooms/layout', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(layout),
  })
}
