import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  assignRoomToFloor,
  clearRoomFloor,
  createFloor,
  deleteFloor,
  listFloors,
  renameFloor,
} from './floors'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('floors api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists floors from GET /api/floors', async () => {
    const floors = [{ id: 1, name: 'Ground', level: 0 }]
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(floors))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listFloors()).resolves.toEqual(floors)
    expect(fetchMock).toHaveBeenCalledWith('/api/floors', undefined)
  })

  it('creates a floor via POST /api/floors', async () => {
    const floor = { id: 1, name: 'Ground', level: 0 }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(floor, 201))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createFloor('Ground')).resolves.toEqual(floor)
    expect(fetchMock).toHaveBeenCalledWith('/api/floors', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Ground' }),
    })
  })

  it('renames a floor via PUT /api/floors/{id}', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1, name: 'Basement', level: 0 }))
    vi.stubGlobal('fetch', fetchMock)

    await renameFloor(1, 'Basement')

    expect(fetchMock).toHaveBeenCalledWith('/api/floors/1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Basement' }),
    })
  })

  it('deletes a floor via DELETE /api/floors/{id}', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(deleteFloor(1)).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenCalledWith('/api/floors/1', { method: 'DELETE' })
  })

  it('assigns a room to a floor via PUT /api/rooms/{id}/floor', async () => {
    const room = { id: 5, name: 'Kitchen', floorId: 1, floorName: 'Ground' }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(room))
    vi.stubGlobal('fetch', fetchMock)

    await expect(assignRoomToFloor(5, 1)).resolves.toEqual(room)
    expect(fetchMock).toHaveBeenCalledWith('/api/rooms/5/floor', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ floorId: 1 }),
    })
  })

  it('clears a room floor via DELETE /api/rooms/{id}/floor', async () => {
    const room = { id: 5, name: 'Kitchen', floorId: null, floorName: null }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(room))
    vi.stubGlobal('fetch', fetchMock)

    await clearRoomFloor(5)
    expect(fetchMock).toHaveBeenCalledWith('/api/rooms/5/floor', { method: 'DELETE' })
  })

  it('reports the detail of an RFC 9457 problem response', async () => {
    const problem = { title: 'Conflict', detail: 'Floor already exists: Ground' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(problem, 409)))

    await expect(createFloor('Ground')).rejects.toThrow('Floor already exists: Ground')
  })
})
