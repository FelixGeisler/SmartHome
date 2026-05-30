import { useMemo } from 'react'
import { Text } from '@react-three/drei'
import type { Room } from '@/api/client'
import {
  FLOOR_THICKNESS, PLAN_SCALE, WALL_HEIGHT, WALL_THICKNESS, roomColor,
} from './palette'

interface Rect { x: number; y: number; w: number; h: number }

interface Room3DProps {
  room:        Room
  colorIdx:    number
  /** Y position of this floor's base surface (top of the floor plate). */
  baseY:       number
  /** Wall opacity 0..1 — lets the caller dim walls so contents of the floor are visible. */
  wallOpacity: number
  selected?:   boolean
  onSelect?:   () => void
  children?:   React.ReactNode  // device meshes placed inside the room
}

/**
 * One room rendered as four walls extruded from the floor plate.
 * Supports the optional second rectangle (L-shape) the room can carry.
 *
 * Coordinate convention: plan X → world X, plan Y → world Z, walls extrude on Y.
 */
export function Room3D({
  room, colorIdx, baseY, wallOpacity, selected, onSelect, children,
}: Room3DProps) {
  const color = roomColor(colorIdx)

  const rects: Rect[] = useMemo(() => {
    const out: Rect[] = []
    if (room.planX != null) {
      out.push({ x: room.planX, y: room.planY!, w: room.planW!, h: room.planH! })
    }
    if (room.planX2 != null) {
      out.push({ x: room.planX2, y: room.planY2!, w: room.planW2!, h: room.planH2! })
    }
    return out
  }, [room.planX, room.planY, room.planW, room.planH, room.planX2, room.planY2, room.planW2, room.planH2])

  if (rects.length === 0) return null

  return (
    <group onClick={onSelect ? (e) => { e.stopPropagation(); onSelect() } : undefined}>
      {rects.map((rect, ri) => (
        <RoomSegment
          key={ri}
          rect={rect}
          color={color}
          baseY={baseY}
          wallOpacity={wallOpacity}
          highlight={!!selected}
          showLabel={ri === 0}
          label={`${room.icon ?? ''} ${room.name}`.trim()}
        />
      ))}
      {children}
    </group>
  )
}

function RoomSegment({
  rect, color, baseY, wallOpacity, highlight, showLabel, label,
}: {
  rect: Rect
  color: string
  baseY: number
  wallOpacity: number
  highlight: boolean
  showLabel: boolean
  label: string
}) {
  const cx = (rect.x + rect.w / 2) * PLAN_SCALE
  const cz = (rect.y + rect.h / 2) * PLAN_SCALE
  const w  = rect.w * PLAN_SCALE
  const d  = rect.h * PLAN_SCALE
  const wallY = baseY + WALL_HEIGHT / 2

  // Wall material — kept slightly translucent so you can always see what's inside.
  const wallOpacityFinal = highlight ? Math.min(1, wallOpacity + 0.2) : wallOpacity

  return (
    <group>
      {/* Floor plate */}
      <mesh position={[cx, baseY - FLOOR_THICKNESS / 2, cz]} receiveShadow>
        <boxGeometry args={[w, FLOOR_THICKNESS, d]} />
        <meshStandardMaterial
          color={color}
          opacity={0.18}
          transparent
          roughness={0.9}
        />
      </mesh>

      {/* Four walls — north, south, east, west */}
      {/* North wall (low Z edge) */}
      <mesh position={[cx, wallY, cz - d / 2]} castShadow>
        <boxGeometry args={[w, WALL_HEIGHT, WALL_THICKNESS]} />
        <meshStandardMaterial color={color} opacity={wallOpacityFinal} transparent roughness={0.6} />
      </mesh>
      {/* South wall (high Z edge) */}
      <mesh position={[cx, wallY, cz + d / 2]} castShadow>
        <boxGeometry args={[w, WALL_HEIGHT, WALL_THICKNESS]} />
        <meshStandardMaterial color={color} opacity={wallOpacityFinal} transparent roughness={0.6} />
      </mesh>
      {/* West wall (low X edge) */}
      <mesh position={[cx - w / 2, wallY, cz]} castShadow>
        <boxGeometry args={[WALL_THICKNESS, WALL_HEIGHT, d]} />
        <meshStandardMaterial color={color} opacity={wallOpacityFinal} transparent roughness={0.6} />
      </mesh>
      {/* East wall (high X edge) */}
      <mesh position={[cx + w / 2, wallY, cz]} castShadow>
        <boxGeometry args={[WALL_THICKNESS, WALL_HEIGHT, d]} />
        <meshStandardMaterial color={color} opacity={wallOpacityFinal} transparent roughness={0.6} />
      </mesh>

      {/* Floating room label */}
      {showLabel && (
        <Text
          position={[cx, baseY + WALL_HEIGHT + 0.4, cz]}
          fontSize={1.4}
          color="white"
          anchorX="center"
          anchorY="bottom"
          outlineWidth={0.04}
          outlineColor="#000"
        >
          {label}
        </Text>
      )}
    </group>
  )
}
