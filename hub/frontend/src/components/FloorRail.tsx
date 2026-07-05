import type { Floor } from '../api/floors'

interface FloorRailProps {
  floors: Floor[]
  activeFloorId: number | null
  /** Whether to offer an "Unassigned" dot for rooms not on any floor. */
  showUnassigned: boolean
  editing: boolean
  onSelect: (floorId: number | null) => void
  onAddFloor: () => void
}

/**
 * A vertical rail of dots beside the floor plan, one per floor plus an optional unassigned bucket,
 * for switching which floor's rooms the plan shows. In edit mode it also offers a button to add a
 * floor.
 */
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

/** The class for a floor dot, marked active when it is the shown floor. */
function dotClass(active: boolean): string {
  return active ? 'floor-plan__dot floor-plan__dot--active' : 'floor-plan__dot'
}
