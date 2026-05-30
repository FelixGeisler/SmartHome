import { useState } from 'react'
import { Canvas } from '@react-three/fiber'
import { OrbitControls, Grid } from '@react-three/drei'
import type { Device, Floor, Room } from '@/api/client'
import { Room3D } from './Room3D'
import { Device3D } from './Device3D'
import { FLOOR_THICKNESS } from './palette'

interface FloorPlan3DProps {
  /** The floor to render. When null, shows an empty stage with a hint. */
  floor:   Floor | null
  rooms:   Room[]
  devices: Device[]
}

/**
 * 3D dollhouse view of a single floor.
 *
 *   • Rooms are walled boxes extruded from their planX/Y/W/H rect, with the
 *     optional second rect for L-shapes.
 *   • Devices placed in rooms render as meshes that react to their live state
 *     — Hue lights actually emit light into their room.
 *
 * Multi-floor stacking is intentionally not the default — visually crowded.
 * The floor selector on the left switches which floor renders here.
 */
export function FloorPlan3D({ floor, rooms, devices }: FloorPlan3DProps) {
  const [hoveredDeviceId, setHoveredDeviceId] = useState<number | null>(null)

  const baseY = FLOOR_THICKNESS  // single floor sits on the ground plate

  // Camera target: middle of the floor footprint, slightly above the floor surface.
  const cameraTarget: [number, number, number] = [50, 2, 50]

  const floorRooms = floor
    ? rooms.filter(r => r.floorId === floor.id && r.planX != null)
    : []

  return (
    <div className="w-full h-full relative">
      <Canvas
        shadows
        camera={{
          position: [85, 75, 110],
          fov: 45,
          near: 0.1,
          far: 1000,
        }}
        style={{ background: 'linear-gradient(to bottom, #1e293b 0%, #0f172a 100%)' }}
      >
        {/* Ground / sky lighting */}
        <ambientLight intensity={0.35} />
        <directionalLight
          position={[80, 100, 60]}
          intensity={0.8}
          castShadow
          shadow-mapSize-width={2048}
          shadow-mapSize-height={2048}
        />
        <hemisphereLight args={['#bbddff', '#080820', 0.3]} />

        {/* Reference grid on the ground */}
        <Grid
          args={[200, 200]}
          position={[50, -0.01, 50]}
          cellColor="#334155"
          sectionColor="#475569"
          sectionThickness={1}
          fadeDistance={250}
          fadeStrength={1}
          infiniteGrid={false}
        />

        {/* Rooms on the active floor */}
        {floorRooms.map((room, ri) => {
          const roomDevices = devices.filter(d => d.room === room.name)
          return (
            <Room3D
              key={room.id}
              room={room}
              colorIdx={ri}
              baseY={baseY}
              wallOpacity={0.45}
            >
              {roomDevices.map(d => (
                <Device3D
                  key={d.id}
                  device={d}
                  room={room}
                  baseY={baseY}
                  hover={hoveredDeviceId === d.id}
                  onClick={() => setHoveredDeviceId(prev => prev === d.id ? null : d.id)}
                />
              ))}
            </Room3D>
          )
        })}

        <OrbitControls
          target={cameraTarget}
          enableDamping
          dampingFactor={0.08}
          maxPolarAngle={Math.PI / 2 - 0.05}  // never go fully below ground
          minDistance={20}
          maxDistance={400}
        />
      </Canvas>

      {/* Empty-state hint */}
      {!floor && (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <p className="text-sm text-white/60 bg-black/40 px-4 py-2 rounded-lg backdrop-blur-sm">
            Select a floor on the left to see it in 3D
          </p>
        </div>
      )}
      {floor && floorRooms.length === 0 && (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <p className="text-sm text-white/60 bg-black/40 px-4 py-2 rounded-lg backdrop-blur-sm">
            No rooms placed on "{floor.name}" yet — switch to 2D and add some.
          </p>
        </div>
      )}

      {/* Small legend top-right */}
      <div className="absolute top-3 right-3 px-3 py-2 rounded-lg bg-black/40 text-xs text-white/80 backdrop-blur-sm pointer-events-none">
        <p className="font-semibold mb-1">3D View</p>
        <p>drag: rotate · scroll: zoom · right-drag: pan</p>
      </div>
    </div>
  )
}
