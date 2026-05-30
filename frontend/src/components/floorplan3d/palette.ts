/**
 * Shared color/scale constants for the 3D floor plan.
 * Keeping these here means Room3D and Device3D never disagree on
 * scale, and tweaking the look is a one-file change.
 */

/** Plan rectangles are 0–100 percent. World units map 1:1 — so a full floor
 *  is a 100×100 footprint. Camera/scale constants assume this. */
export const PLAN_SCALE = 1

/** Wall height in world units (~ceiling height). */
export const WALL_HEIGHT = 7

/** Wall thickness in world units. */
export const WALL_THICKNESS = 0.25

/** How tall each floor's base plate is. */
export const FLOOR_THICKNESS = 0.4

/** Gap between two stacked floors so you can clearly see each storey. */
export const FLOOR_GAP = 1

/** Total vertical step from one floor's base to the next floor's base. */
export const STACK_STEP = WALL_HEIGHT + FLOOR_THICKNESS + FLOOR_GAP

/** Room colour palette — same hues as the 2D plan, hex this time. */
export const ROOM_COLORS = [
  '#3b82f6', // blue
  '#22c55e', // green
  '#a855f7', // purple
  '#fb923c', // orange
  '#ec4899', // pink
  '#14b8a6', // teal
  '#eab308', // yellow
  '#ef4444', // red
] as const

export function roomColor(idx: number): string {
  return ROOM_COLORS[idx % ROOM_COLORS.length]
}
