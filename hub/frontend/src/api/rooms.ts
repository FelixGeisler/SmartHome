import { request, type Device } from './devices'

export interface Room {
  id: number
  name: string
  floorId?: number | null
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

export interface RoomsLayout {
  rooms: RoomBox[]
  /** Placed device positions; absent means none yet (every icon falls back to a default slot). */
  devices?: DevicePlacement[]
}

export function listRooms(): Promise<Room[]> {
  return request<Room[]>('/api/rooms')
}

export function createRoom(name: string): Promise<Room> {
  return request<Room>('/api/rooms', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
}

export function renameRoom(id: number, name: string): Promise<Room> {
  return request<Room>(`/api/rooms/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
}

/** Deletes a room; its devices fall back to unassigned. */
export function deleteRoom(id: number): Promise<void> {
  return request<void>(`/api/rooms/${id}`, { method: 'DELETE' })
}

export function assignDeviceToRoom(deviceId: number, roomId: number): Promise<Device> {
  return request<Device>(`/api/devices/${deviceId}/room`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ roomId }),
  })
}

export function clearDeviceRoom(deviceId: number): Promise<Device> {
  return request<Device>(`/api/devices/${deviceId}/room`, { method: 'DELETE' })
}

/** Reads the saved floor-plan layout, or null when none saved (the hub answers a never-arranged plan with 204). */
export async function getRoomsLayout(): Promise<RoomsLayout | null> {
  const layout = await request<RoomsLayout | undefined>('/api/rooms/layout')
  return layout ?? null
}

export function saveRoomsLayout(layout: RoomsLayout): Promise<RoomsLayout> {
  return request<RoomsLayout>('/api/rooms/layout', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(layout),
  })
}
