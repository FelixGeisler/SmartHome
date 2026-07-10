import {
  useRef,
  useState,
  type CSSProperties,
  type DragEvent,
  type PointerEvent,
  type RefObject,
} from 'react'
import type { Device, DeviceCommand } from '../api/devices'
import {
  colorXyOf,
  formatReading,
  hasColor,
  hasColorTemperature,
  isDimmable,
  isOn,
  isSwitchable,
} from '../api/devices'
import type { Floor } from '../api/floors'
import type { DevicePlacement, Room } from '../api/rooms'
import { xyToHex } from '../color'
import {
  deviceIconKind,
  isTemperatureReading,
  primarySensor,
  sensorLabel,
  temperatureColor,
} from '../deviceIcon'
import { pointToFraction } from '../roomsLayout'
import { BrightnessControl, ColorControl, ColorTemperatureControl } from './deviceControls'
import { DeviceGlyph } from './DeviceGlyph'

/** Data-transfer MIME for a dragged device id. */
export const DEVICE_DND_MIME = 'application/x-smarthome-device'

interface RoomBoxProps {
  room: Room
  devices: Device[]
  /** One placement per assigned device (reconciled by the page). */
  placements: DevicePlacement[]
  /** Registered devices not in any room; the picker's choices. */
  unassigned: Device[]
  /** True when the floor plan is in edit mode. */
  editing: boolean
  busyIds: ReadonlySet<number>
  onToggle: (device: Device) => void
  onCommand: (device: Device, command: DeviceCommand) => void
  /** Assigns a device to the room, optionally at a dropped position. */
  onAssignDevice: (roomId: number, deviceId: number, position?: { fx: number; fy: number }) => void
  onClearDevice: (device: Device) => void
  onMovePlacement: (deviceId: number, fx: number, fy: number) => void
  onRenameRoom: (room: Room, name: string) => void
  onDeleteRoom: (room: Room) => void
  floors: Floor[]
  onSetRoomFloor: (room: Room, floorId: number | null) => void
}

/** True when the device has controls beyond a plain on/off toggle. */
function hasControls(device: Device): boolean {
  return isDimmable(device) || hasColor(device) || hasColorTemperature(device)
}

/**
 * One room on the floor plan: assigned devices sit as symbols at their saved spots. In view mode
 * icons show live values and control their devices; in edit mode the box and icons drag, devices
 * drop in from the tray, and the room can be renamed or deleted.
 */
export function RoomBox({
  room,
  devices,
  placements,
  unassigned,
  editing,
  busyIds,
  onToggle,
  onCommand,
  onAssignDevice,
  onClearDevice,
  onMovePlacement,
  onRenameRoom,
  onDeleteRoom,
  floors,
  onSetRoomFloor,
}: RoomBoxProps) {
  const [pickerOpen, setPickerOpen] = useState(false)
  const [dragOver, setDragOver] = useState(false)
  const floorRef = useRef<HTMLDivElement>(null)

  const placementById = new Map(placements.map((placement) => [placement.deviceId, placement]))

  function handleDrop(event: DragEvent) {
    event.preventDefault()
    setDragOver(false)
    const raw = event.dataTransfer.getData(DEVICE_DND_MIME)
    const deviceId = Number(raw)
    if (raw === '' || Number.isNaN(deviceId)) {
      return
    }
    const floor = floorRef.current
    const position =
      floor === null
        ? undefined
        : pointToFraction({ x: event.clientX, y: event.clientY }, floor.getBoundingClientRect())
    onAssignDevice(room.id, deviceId, position)
  }

  const className = ['room-box', dragOver ? 'room-box--drag-over' : ''].filter(Boolean).join(' ')

  return (
    <section
      className={className}
      aria-label={`Room ${room.name}`}
      onDragOver={
        editing
          ? (event) => {
              event.preventDefault()
              setDragOver(true)
            }
          : undefined
      }
      onDragLeave={editing ? () => setDragOver(false) : undefined}
      onDrop={editing ? handleDrop : undefined}
    >
      <header className="room-box__header">
        {editing ? (
          <RenameField room={room} onRename={onRenameRoom} />
        ) : (
          <h3 className="room-box__name">{room.name}</h3>
        )}
        {editing && (
          <div className="room-box__actions">
            {floors.length > 0 && (
              <select
                className="room-box__floor-select"
                aria-label={`Floor for ${room.name}`}
                value={
                  room.floorId === null || room.floorId === undefined
                    ? ''
                    : String(room.floorId)
                }
                onChange={(event) =>
                  onSetRoomFloor(
                    room,
                    event.target.value === '' ? null : Number(event.target.value),
                  )
                }
              >
                <option value="">Unassigned</option>
                {floors.map((floor) => (
                  <option key={floor.id} value={String(floor.id)}>
                    {floor.name}
                  </option>
                ))}
              </select>
            )}
            <button
              type="button"
              className="room-box__add"
              aria-label={`Add a device to ${room.name}`}
              title="Add a device"
              onClick={() => setPickerOpen(true)}
            >
              {'+'}
            </button>
            <button
              type="button"
              className="room-box__delete"
              aria-label={`Delete ${room.name}`}
              title="Delete room"
              onClick={() => onDeleteRoom(room)}
            >
              {'×'}
            </button>
          </div>
        )}
      </header>

      <div className="room-box__floor" ref={floorRef}>
        {devices.map((device) => (
          <RoomDeviceIcon
            key={device.id}
            device={device}
            placement={placementById.get(device.id) ?? { deviceId: device.id, fx: 0.5, fy: 0.5 }}
            room={room}
            editing={editing}
            busy={busyIds.has(device.id)}
            floorRef={floorRef}
            onToggle={onToggle}
            onCommand={onCommand}
            onClear={onClearDevice}
            onMove={onMovePlacement}
          />
        ))}
        {devices.length === 0 && (
          <p className="room-box__empty">
            {editing ? 'Drop a device here, or use + to add one.' : 'No devices in this room yet.'}
          </p>
        )}
      </div>

      {pickerOpen && (
        <DevicePicker
          room={room}
          devices={unassigned}
          onClose={() => setPickerOpen(false)}
          onAdd={(device) => onAssignDevice(room.id, device.id)}
        />
      )}
    </section>
  )
}

interface RoomDeviceIconProps {
  device: Device
  placement: DevicePlacement
  room: Room
  editing: boolean
  busy: boolean
  floorRef: RefObject<HTMLDivElement | null>
  onToggle: (device: Device) => void
  onCommand: (device: Device, command: DeviceCommand) => void
  onClear: (device: Device) => void
  onMove: (deviceId: number, fx: number, fy: number) => void
}

/**
 * One device as a floor-plan symbol at its placed spot: a toggle button when switchable, a controls
 * popover when dimmable/color, a reading label for a sensor. In edit mode the glyph drags to
 * reposition and controls are inert.
 */
function RoomDeviceIcon({
  device,
  placement,
  room,
  editing,
  busy,
  floorRef,
  onToggle,
  onCommand,
  onClear,
  onMove,
}: RoomDeviceIconProps) {
  const [open, setOpen] = useState(false)
  const dragging = useRef(false)

  const kind = deviceIconKind(device)
  const on = isSwitchable(device) && isOn(device)
  // A lit lamp or a producing inverter glows.
  const glow = on || kind === 'solar'
  const tint = on ? litColor(device) : undefined

  const primary = primarySensor(device)
  const headlineTint =
    primary !== null && primary.value !== null && isTemperatureReading(primary)
      ? temperatureColor(Number(primary.value))
      : undefined
  const socSensor = device.sensors.find((sensor) => sensor.type === 'BATTERY_SOC')
  const soc = socSensor?.value == null ? null : Number(socSensor.value)
  // Readings other than the headline and battery ring.
  const secondary = device.sensors.filter(
    (sensor) => sensor !== primary && sensor.type !== 'BATTERY_SOC',
  )

  const className = [
    'room-icon',
    glow ? 'room-icon--glow' : '',
    on ? 'room-icon--on' : isSwitchable(device) ? 'room-icon--off' : '',
    kind === 'solar' ? 'room-icon--solar' : '',
  ]
    .filter(Boolean)
    .join(' ')

  const style: CSSProperties = {
    left: `${placement.fx * 100}%`,
    top: `${placement.fy * 100}%`,
    ...(tint === undefined ? {} : { color: tint }),
  }

  function startDrag(event: PointerEvent<HTMLDivElement>) {
    event.stopPropagation()
    dragging.current = true
    event.currentTarget.setPointerCapture?.(event.pointerId)
  }

  function moveDrag(event: PointerEvent<HTMLDivElement>) {
    if (!dragging.current) {
      return
    }
    // No primary button held means a missed pointerup/cancel; end the gesture so the icon doesn't
    // trail the cursor.
    if ((event.buttons & 1) === 0) {
      dragging.current = false
      return
    }
    const floor = floorRef.current
    if (floor === null) {
      return
    }
    const { fx, fy } = pointToFraction(
      { x: event.clientX, y: event.clientY },
      floor.getBoundingClientRect(),
    )
    onMove(device.id, fx, fy)
  }

  function endDrag(event: PointerEvent<HTMLDivElement>) {
    dragging.current = false
    event.currentTarget.releasePointerCapture?.(event.pointerId)
  }

  const glyph = (
    <>
      <span className="room-icon__glyph">
        <DeviceGlyph kind={kind} />
      </span>
      <span className="room-icon__name">{device.name}</span>
      {primary !== null && (
        <span
          className="room-icon__value"
          style={headlineTint === undefined ? undefined : { color: headlineTint }}
        >
          {formatReading(primary)}
        </span>
      )}
      {soc !== null && (
        <span className="room-icon__battery">
          <span
            className="room-icon__ring"
            style={{ '--soc': soc } as CSSProperties}
            title={`Battery ${soc}%`}
          >
            <span className="room-icon__ring-label">{soc}%</span>
          </span>
          <span className="room-icon__ring-caption">Battery</span>
        </span>
      )}
      {secondary.length > 0 && (
        <span className="room-icon__stats">
          {secondary.map((sensor) => (
            <span className="room-icon__stat" key={sensor.key}>
              <span className="room-icon__stat-label">{sensorLabel(sensor)}</span>
              <span className="room-icon__stat-value">{formatReading(sensor)}</span>
            </span>
          ))}
        </span>
      )}
    </>
  )

  return (
    <div
      className={className}
      style={style}
      onPointerDown={editing ? startDrag : undefined}
      onPointerMove={editing ? moveDrag : undefined}
      onPointerUp={editing ? endDrag : undefined}
      onPointerCancel={editing ? endDrag : undefined}
    >
      {isSwitchable(device) && !editing ? (
        <button
          type="button"
          className="room-icon__button"
          disabled={busy}
          aria-label={`Turn ${device.name} ${on ? 'off' : 'on'}`}
          onClick={() => onToggle(device)}
        >
          {glyph}
        </button>
      ) : (
        <div className="room-icon__button" role="img" aria-label={describe(device)}>
          {glyph}
        </div>
      )}

      {!editing && hasControls(device) && (
        <button
          type="button"
          className="room-icon__more"
          aria-label={`Controls for ${device.name}`}
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          {'⋯'}
        </button>
      )}

      {editing && (
        <button
          type="button"
          className="room-icon__remove"
          aria-label={`Remove ${device.name} from ${room.name}`}
          onPointerDown={(event) => event.stopPropagation()}
          onClick={() => onClear(device)}
        >
          {'×'}
        </button>
      )}

      {!editing && hasControls(device) && open && (
        <div className="room-icon__popover">
          {isDimmable(device) && <BrightnessControl device={device} onCommand={onCommand} />}
          {hasColor(device) && <ColorControl device={device} onCommand={onCommand} />}
          {hasColorTemperature(device) && (
            <ColorTemperatureControl device={device} onCommand={onCommand} />
          )}
        </div>
      )}
    </div>
  )
}

/** A lit lamp's color as hex, or undefined when none is set. */
function litColor(device: Device): string | undefined {
  if (!hasColor(device)) {
    return undefined
  }
  const xy = colorXyOf(device)
  return xy === null ? undefined : xyToHex(xy.x, xy.y)
}

/** Accessible label for a non-toggle icon: name plus readings. */
function describe(device: Device): string {
  if (device.sensors.length === 0) {
    return device.name
  }
  const readings = device.sensors.map((sensor) => `${sensor.key} ${formatReading(sensor)}`).join(', ')
  return `${device.name}: ${readings}`
}

/** Uncontrolled field; renames on Enter or blur, ignoring an empty or unchanged name. */
function RenameField({ room, onRename }: { room: Room; onRename: (room: Room, name: string) => void }) {
  function commit(value: string) {
    const name = value.trim()
    if (name !== '' && name !== room.name) {
      onRename(room, name)
    }
  }
  return (
    <input
      key={room.name}
      className="room-box__rename"
      defaultValue={room.name}
      aria-label={`Rename ${room.name}`}
      onBlur={(event) => commit(event.target.value)}
      onKeyDown={(event) => {
        if (event.key === 'Enter') {
          event.currentTarget.blur()
        }
      }}
    />
  )
}

interface DevicePickerProps {
  room: Room
  devices: Device[]
  onClose: () => void
  onAdd: (device: Device) => void
}

/** Modal of unassigned devices; stays open after a pick so several can be added in one sitting. */
function DevicePicker({ room, devices, onClose, onAdd }: DevicePickerProps) {
  return (
    <div className="card-picker" role="presentation" onClick={onClose}>
      <section
        className="card-picker__panel"
        role="dialog"
        aria-modal="true"
        aria-label={`Add a device to ${room.name}`}
        onClick={(event) => event.stopPropagation()}
      >
        <header className="card-picker__head">
          <h2 className="card-picker__title">Add a device to {room.name}</h2>
          <button type="button" className="card-picker__close" aria-label="Close" onClick={onClose}>
            {'×'}
          </button>
        </header>
        {devices.length === 0 ? (
          <p className="card-picker__empty">
            Every device is already in a room. Register more in Configuration.
          </p>
        ) : (
          <ul className="card-picker__list">
            {devices.map((device) => (
              <li key={device.id}>
                <button
                  type="button"
                  className="card-picker__item"
                  onClick={() => onAdd(device)}
                >
                  {device.name}
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
