import type { Floor } from '../api/floors'

interface FloorRailProps {
  floors: Floor[]
  activeFloorId: number | null
  /** Whether to offer an unassigned dot for rooms on no floor. */
  showUnassigned: boolean
  editing: boolean
  onSelect: (floorId: number | null) => void
  onAddFloor: () => void
}

/** A vertical rail of dots for switching floors, plus an add-floor button in edit mode. */
export function FloorRail({
  floors,
  activeFloorId,
  showUnassigned,
  editing,
  onSelect,
  onAddFloor,
}: FloorRailProps) {
  return (
    <div className="floor-plan__floors" role="group" aria-label="Floors">
      {floors.map((floor) => (
        <button
          key={floor.id}
          type="button"
          className={dotClass(floor.id === activeFloorId)}
          aria-label={floor.name}
          aria-current={floor.id === activeFloorId ? 'true' : undefined}
          title={floor.name}
          onClick={() => onSelect(floor.id)}
        />
      ))}
      {showUnassigned && (
        <button
          type="button"
          className={`${dotClass(activeFloorId === null)} floor-plan__dot--unassigned`}
          aria-label="Unassigned"
          aria-current={activeFloorId === null ? 'true' : undefined}
          title="Unassigned rooms"
          onClick={() => onSelect(null)}
        />
      )}
      {editing && (
        <button
          type="button"
          className="floor-plan__floor-add"
          aria-label="Add floor"
          title="Add a floor"
          onClick={onAddFloor}
        >
          {'+'}
        </button>
      )}
    </div>
  )
}

function dotClass(active: boolean): string {
  return active ? 'floor-plan__dot floor-plan__dot--active' : 'floor-plan__dot'
}
