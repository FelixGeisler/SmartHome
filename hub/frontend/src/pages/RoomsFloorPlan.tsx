import { useCallback, useEffect, useRef, useState } from 'react'
import ReactGridLayout from 'react-grid-layout'
import type { Layout } from 'react-grid-layout'
import type { Device, DeviceCommand } from '../api/devices'
import type { Floor } from '../api/floors'
import {
  assignRoomToFloor,
  clearRoomFloor,
  createFloor,
  deleteFloor,
  listFloors,
  renameFloor,
} from '../api/floors'
import type { DevicePlacement, Room, RoomBox as RoomBoxLayout } from '../api/rooms'
import {
  assignDeviceToRoom,
  clearDeviceRoom,
  createRoom,
  deleteRoom,
  getRoomsLayout,
  listRooms,
  renameRoom,
  saveRoomsLayout,
} from '../api/rooms'
import { EditToolbar } from '../components/EditToolbar'
import { FloorRail } from '../components/FloorRail'
import { DEVICE_DND_MIME, RoomBox } from '../components/RoomBox'
import {
  addRoomBox,
  displayBoxes,
  fromGridLayout,
  GRID_COLS,
  placementsToSave,
  reconcilePlacements,
  removeRoomBox,
  toGridLayout,
} from '../roomsLayout'
import type { LoadState } from './DashboardPage'

interface RoomsFloorPlanProps {
  devices: Device[]
  busyIds: ReadonlySet<number>
  onToggle: (device: Device) => void
  onCommand: (device: Device, command: DeviceCommand) => void
  onDeviceUpdated: (device: Device) => void
}

const GRID_CONFIG = {
  cols: GRID_COLS,
  rowHeight: 24,
  margin: [16, 16] as [number, number],
  containerPadding: [0, 0] as [number, number],
}
// Width used before the container is measured (and in test environments with no layout).
const FALLBACK_WIDTH = 1200
// A mousedown on any of these must not start a box drag; grabbing the box body moves it instead.
// `.room-icon` is here so dragging a device icon repositions the icon, never the room box.
const DRAG_CANCEL = 'button, input, select, a, .room-icon'

function isUnassigned(room: Room): boolean {
  return room.floorId === null || room.floorId === undefined
}

/** The Rooms view: a spatial floor plan of room boxes on a grid, grouped by building floor. */
export function RoomsFloorPlan({
  devices,
  busyIds,
  onToggle,
  onCommand,
  onDeviceUpdated,
}: RoomsFloorPlanProps) {
  const [rooms, setRooms] = useState<Room[]>([])
  const [floors, setFloors] = useState<Floor[]>([])
  const [activeFloorId, setActiveFloorId] = useState<number | null>(null)
  const [savedLayout, setSavedLayout] = useState<RoomBoxLayout[] | null>(null)
  const [savedPlacements, setSavedPlacements] = useState<DevicePlacement[]>([])
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState<RoomBoxLayout[]>([])
  const [draftPlacements, setDraftPlacements] = useState<DevicePlacement[]>([])
  const [saving, setSaving] = useState(false)

  const [gridWidth, setGridWidth] = useState(0)
  const observerRef = useRef<ResizeObserver | null>(null)
  const measureRef = useCallback((element: HTMLDivElement | null) => {
    observerRef.current?.disconnect()
    if (element === null || typeof ResizeObserver === 'undefined') {
      return
    }
    const measure = () => setGridWidth(element.clientWidth || FALLBACK_WIDTH)
    measure()
    observerRef.current = new ResizeObserver(measure)
    observerRef.current.observe(element)
  }, [])

  const loadRooms = useCallback(() => {
    listRooms()
      .then((loaded) => {
        setRooms(loaded)
        setError(null)
        setLoadState('ready')
      })
      .catch((cause: unknown) => {
        setError(messageOf(cause))
        setLoadState('error')
      })
  }, [])

  const loadFloors = useCallback(() => {
    listFloors()
      .then((loaded) => {
        setFloors(loaded)
        // Default to the lowest floor so the plan opens on a real storey, not the unassigned bucket.
        setActiveFloorId((current) => (current === null && loaded.length > 0 ? loaded[0].id : current))
      })
      .catch(() => {
        // Floors are advisory; if they fail to load, every room shows ungrouped.
      })
  }, [])

  useEffect(() => {
    loadRooms()
    loadFloors()
  }, [loadRooms, loadFloors])

  // The layout is advisory: a missing or unreadable one just leaves the tidy default.
  useEffect(() => {
    getRoomsLayout()
      .then((saved) => {
        setSavedLayout(saved ? saved.rooms : null)
        setSavedPlacements(saved?.devices ?? [])
      })
      .catch(() => {
        // The hub is briefly unreachable; leave the layout unset (rooms flow into a tidy default).
      })
  }, [])

  const hasFloors = floors.length > 0
  const unassigned = devices.filter(
    (device) => device.roomId === null || device.roomId === undefined,
  )
  const hasUnassignedRooms = rooms.some(isUnassigned)
  // Corrected so the view is never stranded on an empty, dot-less bucket (last unassigned room
  // placed, or the active floor deleted): fall back to a floor.
  const fallbackFloorId = hasFloors ? floors[0].id : null
  const selectionValid =
    (activeFloorId === null && hasUnassignedRooms) || floors.some((f) => f.id === activeFloorId)
  const effectiveFloorId = selectionValid ? activeFloorId : fallbackFloorId
  const activeFloor = floors.find((floor) => floor.id === effectiveFloorId) ?? null
  const visibleRooms = hasFloors
    ? rooms.filter((room) => (room.floorId ?? null) === effectiveFloorId)
    : rooms
  const visibleById = new Map(visibleRooms.map((room) => [room.id, room]))
  const placements = editing ? draftPlacements : savedPlacements
  // In edit mode the draft holds boxes only for floors touched this session; fall back to the
  // saved layout so switching floors never blanks a floor.
  const boxes = editing
    ? displayBoxes(visibleRooms, mergeBoxes(draft, savedLayout ?? []))
    : displayBoxes(visibleRooms, savedLayout)
  const gridLayout = toGridLayout(boxes)

  function devicesIn(roomId: number): Device[] {
    return devices.filter((device) => device.roomId === roomId)
  }

  function retry() {
    setLoadState('loading')
    setError(null)
    loadRooms()
    loadFloors()
  }

  function enterEdit() {
    setDraft(displayBoxes(visibleRooms, savedLayout))
    setDraftPlacements(savedPlacements)
    setEditing(true)
  }

  function cancelEdit() {
    setEditing(false)
  }

  async function commitEdit() {
    setSaving(true)
    setError(null)
    // Persist an explicit position per device, so removing one later never shifts another's icon.
    const devicePlacements = rooms.flatMap((room) =>
      placementsToSave(devicesIn(room.id), draftPlacements),
    )
    // Merge the draft (this floor's boxes) over the saved boxes so other floors' positions survive,
    // dropping any box whose room no longer exists.
    const liveRoomIds = new Set(rooms.map((room) => room.id))
    const boxById = new Map<number, RoomBoxLayout>()
    for (const box of savedLayout ?? []) {
      if (liveRoomIds.has(box.roomId)) {
        boxById.set(box.roomId, box)
      }
    }
    for (const box of draft) {
      if (liveRoomIds.has(box.roomId)) {
        boxById.set(box.roomId, box)
      }
    }
    const roomBoxes = [...boxById.values()]
    try {
      await saveRoomsLayout({ rooms: roomBoxes, devices: devicePlacements })
      setSavedLayout(roomBoxes)
      setSavedPlacements(devicePlacements)
      setEditing(false)
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setSaving(false)
    }
  }

  // Merge into the draft so boxes for other floors touched earlier this session are kept.
  function handleLayoutChange(next: Layout) {
    const nextBoxes = fromGridLayout(next)
    const nextIds = new Set(nextBoxes.map((box) => box.roomId))
    setDraft((current) => [...current.filter((box) => !nextIds.has(box.roomId)), ...nextBoxes])
  }

  async function handleAddRoom() {
    const name = window.prompt('Name the new room')?.trim()
    if (name === undefined || name === '') {
      return
    }
    setError(null)
    try {
      let room = await createRoom(name)
      // A room created while a floor is shown joins that floor.
      if (effectiveFloorId !== null) {
        room = await assignRoomToFloor(room.id, effectiveFloorId)
      }
      setRooms((current) => [...current, room])
      setDraft((current) => addRoomBox(current, room))
    } catch (cause) {
      setError(messageOf(cause))
    }
  }

  async function handleAddFloor() {
    const name = window.prompt('Name the new floor')?.trim()
    if (name === undefined || name === '') {
      return
    }
    setError(null)
    try {
      const floor = await createFloor(name)
      setFloors((current) => [...current, floor].sort((a, b) => a.level - b.level))
      setActiveFloorId(floor.id)
    } catch (cause) {
      setError(messageOf(cause))
    }
  }

  function handleRenameFloor(floor: Floor, name: string) {
    setError(null)
    renameFloor(floor.id, name)
      .then((updated) =>
        setFloors((current) => current.map((f) => (f.id === updated.id ? updated : f))),
      )
      .catch((cause: unknown) => setError(messageOf(cause)))
  }

  function handleDeleteFloor(floor: Floor) {
    setError(null)
    deleteFloor(floor.id)
      .then(() => {
        setFloors((current) => current.filter((f) => f.id !== floor.id))
        setActiveFloorId((current) => (current === floor.id ? null : current))
        // Its rooms are now unassigned on the server; refetch so they move to the unassigned bucket.
        loadRooms()
      })
      .catch((cause: unknown) => setError(messageOf(cause)))
  }

  function handleSetRoomFloor(room: Room, floorId: number | null) {
    setError(null)
    const call = floorId === null ? clearRoomFloor(room.id) : assignRoomToFloor(room.id, floorId)
    call
      .then((updated) =>
        setRooms((current) => current.map((r) => (r.id === updated.id ? updated : r))),
      )
      .catch((cause: unknown) => setError(messageOf(cause)))
  }

  function handleAssignDevice(
    roomId: number,
    deviceId: number,
    position?: { fx: number; fy: number },
  ) {
    setError(null)
    if (position !== undefined) {
      setDraftPlacements((current) => upsertPlacement(current, { deviceId, ...position }))
    }
    assignDeviceToRoom(deviceId, roomId)
      .then(onDeviceUpdated)
      .catch((cause: unknown) => setError(messageOf(cause)))
  }

  function handleMovePlacement(deviceId: number, fx: number, fy: number) {
    setDraftPlacements((current) => upsertPlacement(current, { deviceId, fx, fy }))
  }

  function handleClearDevice(device: Device) {
    setError(null)
    clearDeviceRoom(device.id)
      .then((updated) => {
        onDeviceUpdated(updated)
        // Drop the placement only after the clear succeeds; otherwise a failed request would jump
        // the icon to the default slot while the device is still in the room.
        setDraftPlacements((current) =>
          current.filter((placement) => placement.deviceId !== device.id),
        )
      })
      .catch((cause: unknown) => setError(messageOf(cause)))
  }

  function handleRenameRoom(room: Room, name: string) {
    setError(null)
    renameRoom(room.id, name)
      .then((updated) =>
        setRooms((current) => current.map((r) => (r.id === updated.id ? updated : r))),
      )
      .catch((cause: unknown) => setError(messageOf(cause)))
  }

  function handleDeleteRoom(room: Room) {
    setError(null)
    deleteRoom(room.id)
      .then(() => {
        setRooms((current) => current.filter((r) => r.id !== room.id))
        setDraft((current) => removeRoomBox(current, room.id))
      })
      .catch((cause: unknown) => setError(messageOf(cause)))
  }

  return (
    <section className="floor-plan">
      {error !== null && (
        <p className="floor-plan__error" role="alert">
          {error}
        </p>
      )}

      {loadState === 'loading' && <p className="floor-plan__hint">Loading rooms&hellip;</p>}

      {loadState === 'error' && (
        <button type="button" className="floor-plan__retry" onClick={retry}>
          Retry
        </button>
      )}

      {loadState === 'ready' && (
        <>
          <div className="floor-plan__bar">
            <div className="floor-plan__heading">
              <h2 className="floor-plan__title">Rooms</h2>
              {hasFloors &&
                (editing && activeFloor !== null ? (
                  <>
                    <FloorNameField floor={activeFloor} onRename={handleRenameFloor} />
                    <button
                      type="button"
                      className="floor-plan__floor-delete"
                      aria-label={`Delete ${activeFloor.name}`}
                      title="Delete floor"
                      onClick={() => handleDeleteFloor(activeFloor)}
                    >
                      {'×'}
                    </button>
                  </>
                ) : (
                  <span className="floor-plan__floor-name">
                    {activeFloor !== null ? activeFloor.name : 'Unassigned'}
                  </span>
                ))}
            </div>
            <EditToolbar
              editing={editing}
              saving={saving}
              addLabel="Add room"
              onEnterEdit={enterEdit}
              onSave={() => void commitEdit()}
              onCancel={cancelEdit}
              onAddCard={() => void handleAddRoom()}
            />
          </div>

          {editing && unassigned.length > 0 && (
            <div className="floor-plan__tray" aria-label="Unassigned devices">
              <span className="floor-plan__tray-label">Unassigned</span>
              {unassigned.map((device) => (
                <span
                  key={device.id}
                  className="floor-plan__chip"
                  draggable
                  title={`Drag ${device.name} into a room`}
                  onDragStart={(event) =>
                    event.dataTransfer.setData(DEVICE_DND_MIME, String(device.id))
                  }
                >
                  {device.name}
                </span>
              ))}
            </div>
          )}

          <div className="floor-plan__stage">
            <div className="floor-plan__canvas">
              {boxes.length === 0 ? (
                <p className="floor-plan__hint">{emptyHint(editing, hasFloors)}</p>
              ) : (
                <div ref={measureRef}>
                  {gridWidth > 0 && (
                    <ReactGridLayout
                      className={editing ? 'room-grid room-grid--editing' : 'room-grid'}
                      layout={gridLayout}
                      width={gridWidth}
                      gridConfig={GRID_CONFIG}
                      dragConfig={{ enabled: editing, cancel: DRAG_CANCEL }}
                      resizeConfig={{ enabled: editing }}
                      onLayoutChange={editing ? handleLayoutChange : undefined}
                    >
                      {boxes.map((box) => {
                        const room = visibleById.get(box.roomId)
                        if (room === undefined) {
                          return null
                        }
                        const roomDevices = devicesIn(room.id)
                        return (
                          <div key={String(box.roomId)}>
                            <RoomBox
                              room={room}
                              devices={roomDevices}
                              placements={reconcilePlacements(roomDevices, placements)}
                              unassigned={unassigned}
                              floors={floors}
                              editing={editing}
                              busyIds={busyIds}
                              onToggle={onToggle}
                              onCommand={onCommand}
                              onAssignDevice={handleAssignDevice}
                              onClearDevice={handleClearDevice}
                              onMovePlacement={handleMovePlacement}
                              onRenameRoom={handleRenameRoom}
                              onDeleteRoom={handleDeleteRoom}
                              onSetRoomFloor={handleSetRoomFloor}
                            />
                          </div>
                        )
                      })}
                    </ReactGridLayout>
                  )}
                </div>
              )}
            </div>

            {(hasFloors || editing) && (
              <FloorRail
                floors={floors}
                activeFloorId={effectiveFloorId}
                showUnassigned={hasUnassignedRooms}
                editing={editing}
                onSelect={setActiveFloorId}
                onAddFloor={() => void handleAddFloor()}
              />
            )}
          </div>
        </>
      )}
    </section>
  )
}

function FloorNameField({
  floor,
  onRename,
}: {
  floor: Floor
  onRename: (floor: Floor, name: string) => void
}) {
  function commit(value: string) {
    const name = value.trim()
    if (name !== '' && name !== floor.name) {
      onRename(floor, name)
    }
  }
  return (
    <input
      key={floor.name}
      className="floor-plan__floor-rename"
      defaultValue={floor.name}
      aria-label={`Rename ${floor.name}`}
      onBlur={(event) => commit(event.target.value)}
      onKeyDown={(event) => {
        if (event.key === 'Enter') {
          event.currentTarget.blur()
        }
      }}
    />
  )
}

function mergeBoxes(primary: RoomBoxLayout[], fallback: RoomBoxLayout[]): RoomBoxLayout[] {
  const byId = new Map<number, RoomBoxLayout>()
  for (const box of fallback) {
    byId.set(box.roomId, box)
  }
  for (const box of primary) {
    byId.set(box.roomId, box)
  }
  return [...byId.values()]
}

function upsertPlacement(placements: DevicePlacement[], next: DevicePlacement): DevicePlacement[] {
  return placements.some((placement) => placement.deviceId === next.deviceId)
    ? placements.map((placement) => (placement.deviceId === next.deviceId ? next : placement))
    : [...placements, next]
}

function emptyHint(editing: boolean, hasFloors: boolean): string {
  if (hasFloors) {
    return editing
      ? 'No rooms on this floor. Use + to add one.'
      : 'No rooms on this floor. Use Edit, then +, to add one.'
  }
  return editing ? 'No rooms yet. Use + to add one.' : 'No rooms yet. Use Edit, then +, to add one.'
}

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Something went wrong'
}
