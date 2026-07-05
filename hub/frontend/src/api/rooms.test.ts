import { afterEach, describe, expect, it, vi } from 'vitest'
import type { RoomsLayout } from './rooms'
import {
  assignDeviceToRoom,
  clearDeviceRoom,
  createRoom,
  deleteRoom,
  getRoomsLayout,
  listRooms,
  renameRoom,
  saveRoomsLayout,
} from './rooms'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('rooms api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists rooms from GET /api/rooms', async () => {
    const rooms = [{ id: 1, name: 'Kitchen' }]
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(rooms))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listRooms()).resolves.toEqual(rooms)
    expect(fetchMock).toHaveBeenCalledWith('/api/rooms', undefined)
  })

  it('creates a room via POST /api/rooms', async () => {
    const room = { id: 1, name: 'Kitchen' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(room, 201))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createRoom('Kitchen')).resolves.toEqual(room)
    expect(fetchMock).toHaveBeenCalledWith('/api/rooms', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Kitchen' }),
    })
  })

  it('renames a room via PUT /api/rooms/{id}', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1, name: 'Cookhouse' }))
    vi.stubGlobal('fetch', fetchMock)

    await renameRoom(1, 'Cookhouse')

    expect(fetchMock).toHaveBeenCalledWith('/api/rooms/1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Cookhouse' }),
    })
  })

  it('deletes a room via DELETE /api/rooms/{id}', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(deleteRoom(1)).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenCalledWith('/api/rooms/1', { method: 'DELETE' })
  })

  it('assigns a device to a room via PUT /api/devices/{id}/room', async () => {
    const device = { id: 5, name: 'Plug', roomId: 1, roomName: 'Kitchen' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(device))
    vi.stubGlobal('fetch', fetchMock)

    await expect(assignDeviceToRoom(5, 1)).resolves.toEqual(device)
    expect(fetchMock).toHaveBeenCalledWith('/api/devices/5/room', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ roomId: 1 }),
    })
  })

  it('clears a device room via DELETE /api/devices/{id}/room', async () => {
    const device = { id: 5, name: 'Plug', roomId: null, roomName: null }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(device))
    vi.stubGlobal('fetch', fetchMock)

    await clearDeviceRoom(5)
    expect(fetchMock).toHaveBeenCalledWith('/api/devices/5/room', { method: 'DELETE' })
  })

  it('reads the floor-plan layout from GET /api/rooms/layout', async () => {
    const layout = { rooms: [{ roomId: 1, x: 0, y: 0, w: 6, h: 8 }] }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(layout))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getRoomsLayout()).resolves.toEqual(layout)
    expect(fetchMock).toHaveBeenCalledWith('/api/rooms/layout', undefined)
  })

  it('returns null when no floor-plan layout has been saved (204)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))

    await expect(getRoomsLayout()).resolves.toBeNull()
  })

  it('saves the floor-plan layout via PUT /api/rooms/layout', async () => {
    const layout: RoomsLayout = { rooms: [{ roomId: 2, x: 1, y: 2, w: 6, h: 8 }] }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(layout))
    vi.stubGlobal('fetch', fetchMock)

    await expect(saveRoomsLayout(layout)).resolves.toEqual(layout)
    expect(fetchMock).toHaveBeenCalledWith('/api/rooms/layout', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(layout),
    })
  })

  it('reports the detail of an RFC 9457 problem response', async () => {
    const problem = { title: 'Conflict', detail: 'Room already exists: Kitchen' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(problem, 409)))

    await expect(createRoom('Kitchen')).rejects.toThrow('Room already exists: Kitchen')
  })
})
