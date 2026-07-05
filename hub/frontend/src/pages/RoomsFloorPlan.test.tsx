import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ComponentProps, ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Device } from '../api/devices'
import { assignRoomToFloor, createFloor, listFloors } from '../api/floors'
import {
  assignDeviceToRoom,
  createRoom,
  getRoomsLayout,
  listRooms,
  saveRoomsLayout,
} from '../api/rooms'
import { RoomsFloorPlan } from './RoomsFloorPlan'

vi.mock('../api/rooms', () => ({
  listRooms: vi.fn(),
  createRoom: vi.fn(),
  renameRoom: vi.fn(),
  deleteRoom: vi.fn(),
  assignDeviceToRoom: vi.fn(),
  clearDeviceRoom: vi.fn(),
  getRoomsLayout: vi.fn(),
  saveRoomsLayout: vi.fn(),
}))

vi.mock('../api/floors', () => ({
  listFloors: vi.fn(),
  createFloor: vi.fn(),
  renameFloor: vi.fn(),
  deleteFloor: vi.fn(),
  assignRoomToFloor: vi.fn(),
  clearRoomFloor: vi.fn(),
}))

// Render react-grid-layout as a plain container: these tests exercise our wiring, not RGL's DOM
// measurement and drag internals, which don't run under jsdom.
vi.mock('react-grid-layout', () => ({
  __esModule: true,
  default: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  useContainerWidth: () => ({ width: 1200, containerRef: { current: null }, mounted: true }),
}))

const kitchen = { id: 10, name: 'Kitchen' }

const lamp: Device = {
  id: 1,
  externalId: 'plug-1',
  name: 'Desk Lamp',
  type: 'SHELLY_PLUG',
  capabilities: ['SWITCHABLE'],
  adapterType: 'shelly',
  state: {},
  sensors: [],
  roomId: 10,
  roomName: 'Kitchen',
}

const thermostat: Device = {
  id: 2,
  externalId: 'sensor-1',
  name: 'Thermostat',
  type: 'SENSOR_NODE',
  capabilities: ['SENSING'],
  adapterType: null,
  state: {},
  sensors: [
    { key: 'temperature', type: 'TEMPERATURE', unit: '°C', value: '21.5', updatedAt: '2026-06-15T12:00:00Z' },
    { key: 'humidity', type: 'HUMIDITY', unit: '%', value: '30', updatedAt: '2026-06-15T12:00:00Z' },
  ],
  roomId: 10,
  roomName: 'Kitchen',
}

const ceiling: Device = {
  id: 4,
  externalId: 'hue-1',
  name: 'Ceiling',
  type: 'HUE_LIGHT',
  capabilities: ['SWITCHABLE', 'DIMMABLE'],
  adapterType: 'hue',
  state: { on: 'true', brightness: '50' },
  sensors: [],
  roomId: 10,
  roomName: 'Kitchen',
}

const fan: Device = {
  id: 3,
  externalId: 'fan-1',
  name: 'Fan',
  type: 'SHELLY_PLUG',
  capabilities: ['SWITCHABLE'],
  adapterType: 'shelly',
  state: {},
  sensors: [],
  roomId: null,
  roomName: null,
}

function renderPlan(overrides: Partial<ComponentProps<typeof RoomsFloorPlan>> = {}) {
  const props: ComponentProps<typeof RoomsFloorPlan> = {
    devices: [lamp, thermostat, ceiling, fan],
    busyIds: new Set<number>(),
    onToggle: vi.fn(),
    onCommand: vi.fn(),
    onDeviceUpdated: vi.fn(),
    ...overrides,
  }
  render(<RoomsFloorPlan {...props} />)
  return props
}

describe('RoomsFloorPlan', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(listRooms).mockResolvedValue([kitchen])
    vi.mocked(getRoomsLayout).mockResolvedValue(null)
    vi.mocked(listFloors).mockResolvedValue([])
  })

  it('renders all of a device\'s readings on its icon', async () => {
    renderPlan()

    expect(await screen.findByRole('heading', { name: 'Kitchen' })).toBeInTheDocument()
    // The headline temperature and the secondary humidity both show, the latter with its label.
    expect(screen.getByText(/21\.5 °C/)).toBeInTheDocument()
    expect(screen.getByText('Humidity')).toBeInTheDocument()
    expect(screen.getByText(/30 %/)).toBeInTheDocument()
  })

  it('renders one icon per device in the room', async () => {
    renderPlan()
    await screen.findByRole('heading', { name: 'Kitchen' })

    // A lamp, a sensor, and a bulb are placed in the room; the unassigned fan is not.
    expect(document.querySelectorAll('.room-icon')).toHaveLength(3)
  })

  it('toggles a device from its icon', async () => {
    const user = userEvent.setup()
    const props = renderPlan()

    await user.click(await screen.findByRole('button', { name: 'Turn Desk Lamp on' }))

    expect(props.onToggle).toHaveBeenCalledWith(lamp)
  })

  it('opens the brightness control from a dimmable lamp icon', async () => {
    const user = userEvent.setup()
    renderPlan()

    await user.click(await screen.findByRole('button', { name: 'Controls for Ceiling' }))

    expect(screen.getByLabelText('Brightness for Ceiling')).toBeInTheDocument()
  })

  it('assigns a device to a room from the picker in edit mode', async () => {
    vi.mocked(assignDeviceToRoom).mockResolvedValue({ ...fan, roomId: 10, roomName: 'Kitchen' })
    const user = userEvent.setup()
    const props = renderPlan()
    await screen.findByRole('heading', { name: 'Kitchen' })

    await user.click(screen.getByRole('button', { name: 'Edit layout' }))
    await user.click(screen.getByRole('button', { name: 'Add a device to Kitchen' }))
    const dialog = screen.getByRole('dialog', { name: 'Add a device to Kitchen' })
    await user.click(within(dialog).getByRole('button', { name: 'Fan' }))

    expect(assignDeviceToRoom).toHaveBeenCalledWith(3, 10)
    await waitFor(() => expect(props.onDeviceUpdated).toHaveBeenCalled())
  })

  it('assigns a device dropped onto a room in edit mode', async () => {
    vi.mocked(assignDeviceToRoom).mockResolvedValue({ ...fan, roomId: 10, roomName: 'Kitchen' })
    const user = userEvent.setup()
    renderPlan()
    await screen.findByRole('heading', { name: 'Kitchen' })
    await user.click(screen.getByRole('button', { name: 'Edit layout' }))

    const box = screen.getByRole('region', { name: 'Room Kitchen' })
    fireEvent.drop(box, { dataTransfer: { getData: () => '3' }, clientX: 20, clientY: 20 })

    expect(assignDeviceToRoom).toHaveBeenCalledWith(3, 10)
  })

  it('shows rename and delete controls in edit mode', async () => {
    const user = userEvent.setup()
    renderPlan()
    await screen.findByRole('heading', { name: 'Kitchen' })

    await user.click(screen.getByRole('button', { name: 'Edit layout' }))

    expect(screen.getByLabelText('Rename Kitchen')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Delete Kitchen' })).toBeInTheDocument()
  })

  it('saves the arrangement from the toolbar', async () => {
    vi.mocked(saveRoomsLayout).mockResolvedValue({ rooms: [], devices: [] })
    const user = userEvent.setup()
    renderPlan()
    await screen.findByRole('heading', { name: 'Kitchen' })

    await user.click(screen.getByRole('button', { name: 'Edit layout' }))
    await user.click(screen.getByRole('button', { name: 'Save layout' }))

    expect(saveRoomsLayout).toHaveBeenCalled()
  })

  it('persists a device position after it is dragged in edit mode', async () => {
    vi.mocked(saveRoomsLayout).mockResolvedValue({ rooms: [], devices: [] })
    const user = userEvent.setup()
    renderPlan()
    await screen.findByRole('heading', { name: 'Kitchen' })
    await user.click(screen.getByRole('button', { name: 'Edit layout' }))

    const icon = document.querySelector('.room-icon')
    expect(icon).not.toBeNull()
    fireEvent.pointerDown(icon as Element, { pointerId: 1, buttons: 1 })
    fireEvent.pointerMove(icon as Element, { pointerId: 1, buttons: 1, clientX: 30, clientY: 40 })
    fireEvent.pointerUp(icon as Element, { pointerId: 1 })

    await user.click(screen.getByRole('button', { name: 'Save layout' }))

    expect(saveRoomsLayout).toHaveBeenCalledWith(
      expect.objectContaining({
        devices: expect.arrayContaining([expect.objectContaining({ deviceId: 1 })]),
      }),
    )
  })

  it('does not reposition an icon on a button-less hover after an interrupted drag', async () => {
    vi.mocked(saveRoomsLayout).mockResolvedValue({ rooms: [], devices: [] })
    const user = userEvent.setup()
    renderPlan()
    await screen.findByRole('heading', { name: 'Kitchen' })
    await user.click(screen.getByRole('button', { name: 'Edit layout' }))

    const icon = document.querySelector('.room-icon') as Element
    const before = (icon as HTMLElement).style.left
    // A drag whose pointerup never arrives, followed by a plain hover (no button held).
    fireEvent.pointerDown(icon, { pointerId: 1, buttons: 1 })
    fireEvent.pointerCancel(icon, { pointerId: 1 })
    fireEvent.pointerMove(icon, { pointerId: 1, buttons: 0, clientX: 80, clientY: 80 })

    expect((icon as HTMLElement).style.left).toBe(before)
  })

  it('shows an empty state when there are no rooms', async () => {
    vi.mocked(listRooms).mockResolvedValue([])
    renderPlan({ devices: [] })

    expect(
      await screen.findByText('No rooms yet. Use Edit, then +, to add one.'),
    ).toBeInTheDocument()
  })

  it('creates a room from the toolbar', async () => {
    vi.mocked(listRooms).mockResolvedValue([])
    vi.mocked(createRoom).mockResolvedValue({ id: 11, name: 'Bedroom' })
    vi.spyOn(window, 'prompt').mockReturnValue('Bedroom')
    const user = userEvent.setup()
    renderPlan({ devices: [] })
    await screen.findByText('No rooms yet. Use Edit, then +, to add one.')

    await user.click(screen.getByRole('button', { name: 'Edit layout' }))
    await user.click(screen.getByRole('button', { name: 'Add room' }))

    expect(createRoom).toHaveBeenCalledWith('Bedroom')
    expect(await screen.findByLabelText('Rename Bedroom')).toBeInTheDocument()
  })

  it('switches the shown rooms when a floor dot is clicked', async () => {
    vi.mocked(listFloors).mockResolvedValue([
      { id: 100, name: 'Ground', level: 0 },
      { id: 101, name: 'First', level: 1 },
    ])
    vi.mocked(listRooms).mockResolvedValue([
      { id: 10, name: 'Kitchen', floorId: 100 },
      { id: 20, name: 'Bedroom', floorId: 101 },
    ])
    const user = userEvent.setup()
    renderPlan({ devices: [] })

    // The lowest floor opens by default: its room shows, the other floor's room does not.
    expect(await screen.findByRole('heading', { name: 'Kitchen' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Bedroom' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'First' }))

    expect(await screen.findByRole('heading', { name: 'Bedroom' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Kitchen' })).not.toBeInTheDocument()
  })

  it('adds a floor from the rail in edit mode', async () => {
    vi.mocked(listRooms).mockResolvedValue([])
    vi.mocked(createFloor).mockResolvedValue({ id: 200, name: 'Attic', level: 0 })
    vi.spyOn(window, 'prompt').mockReturnValue('Attic')
    const user = userEvent.setup()
    renderPlan({ devices: [] })
    await screen.findByRole('heading', { name: 'Rooms' })

    await user.click(screen.getByRole('button', { name: 'Edit layout' }))
    await user.click(screen.getByRole('button', { name: 'Add floor' }))

    expect(createFloor).toHaveBeenCalledWith('Attic')
    expect(await screen.findByRole('button', { name: 'Attic' })).toBeInTheDocument()
  })

  it('moves a room to another floor from its floor select', async () => {
    vi.mocked(listFloors).mockResolvedValue([
      { id: 100, name: 'Ground', level: 0 },
      { id: 101, name: 'First', level: 1 },
    ])
    vi.mocked(listRooms).mockResolvedValue([{ id: 10, name: 'Kitchen', floorId: 100 }])
    vi.mocked(assignRoomToFloor).mockResolvedValue({ id: 10, name: 'Kitchen', floorId: 101 })
    const user = userEvent.setup()
    renderPlan({ devices: [] })
    await screen.findByRole('heading', { name: 'Kitchen' })

    await user.click(screen.getByRole('button', { name: 'Edit layout' }))
    await user.selectOptions(screen.getByLabelText('Floor for Kitchen'), '101')

    expect(assignRoomToFloor).toHaveBeenCalledWith(10, 101)
  })

  it('puts a new room on the active floor', async () => {
    vi.mocked(listFloors).mockResolvedValue([{ id: 100, name: 'Ground', level: 0 }])
    vi.mocked(listRooms).mockResolvedValue([])
    vi.mocked(createRoom).mockResolvedValue({ id: 11, name: 'Bedroom' })
    vi.mocked(assignRoomToFloor).mockResolvedValue({ id: 11, name: 'Bedroom', floorId: 100 })
    vi.spyOn(window, 'prompt').mockReturnValue('Bedroom')
    const user = userEvent.setup()
    renderPlan({ devices: [] })
    await screen.findByRole('button', { name: 'Ground' })

    await user.click(screen.getByRole('button', { name: 'Edit layout' }))
    await user.click(screen.getByRole('button', { name: 'Add room' }))

    expect(createRoom).toHaveBeenCalledWith('Bedroom')
    await waitFor(() => expect(assignRoomToFloor).toHaveBeenCalledWith(11, 100))
  })

  it("keeps a floor's rooms visible when switching floors while editing", async () => {
    vi.mocked(listFloors).mockResolvedValue([
      { id: 100, name: 'Ground', level: 0 },
      { id: 101, name: 'First', level: 1 },
    ])
    vi.mocked(listRooms).mockResolvedValue([
      { id: 10, name: 'Kitchen', floorId: 100 },
      { id: 20, name: 'Bedroom', floorId: 101 },
    ])
    const user = userEvent.setup()
    renderPlan({ devices: [] })
    await screen.findByRole('heading', { name: 'Kitchen' })

    await user.click(screen.getByRole('button', { name: 'Edit layout' }))
    await user.click(screen.getByRole('button', { name: 'First' }))

    // The First-floor room renders in edit mode, not the empty-floor hint.
    expect(await screen.findByLabelText('Rename Bedroom')).toBeInTheDocument()
  })

  it('recovers the view after the last unassigned room is placed on a floor', async () => {
    vi.mocked(listFloors).mockResolvedValue([{ id: 100, name: 'Ground', level: 0 }])
    vi.mocked(listRooms).mockResolvedValue([{ id: 30, name: 'Garage', floorId: null }])
    vi.mocked(assignRoomToFloor).mockResolvedValue({ id: 30, name: 'Garage', floorId: 100 })
    const user = userEvent.setup()
    renderPlan({ devices: [] })
    await screen.findByRole('button', { name: 'Unassigned' })

    await user.click(screen.getByRole('button', { name: 'Unassigned' }))
    await screen.findByRole('heading', { name: 'Garage' })
    await user.click(screen.getByRole('button', { name: 'Edit layout' }))
    await user.selectOptions(screen.getByLabelText('Floor for Garage'), '100')

    // Garage left the Unassigned bucket, which emptied; the view falls back to a floor rather than
    // stranding on an empty, dot-less bucket.
    expect(await screen.findByLabelText('Rename Garage')).toBeInTheDocument()
  })
})
