import { useRef } from 'react'
import { useFrame } from '@react-three/fiber'
import { Billboard, Text } from '@react-three/drei'
import type * as THREE from 'three'
import type { Device, Room } from '@/api/client'
import { PLAN_SCALE } from './palette'

interface Device3DProps {
  device:   Device
  room:     Room
  /** Y of the room's floor surface — devices sit just above. */
  baseY:    number
  hover?:   boolean
  onClick?: () => void
}

/**
 * One device rendered inside its room.
 *
 * • Lights: emissive sphere whose intensity tracks on/brightness, plus a real
 *   pointLight so the room actually lights up when the bulb is on.
 * • Plugs: cube that glows when the relay is on; height grows with wattage.
 * • Sensors / meters / radiators: solid coloured marker — no animation for v1.
 */
export function Device3D({ device, room, baseY, hover, onClick }: Device3DProps) {
  // Place the device inside its room — roomX/roomY are 0–100 percent within the room rect.
  // Default to the room center if no position is set.
  const rx = device.roomX ?? 50
  const ry = device.roomY ?? 50
  const planSegX = room.planX ?? 0
  const planSegY = room.planY ?? 0
  const planSegW = room.planW ?? 0
  const planSegH = room.planH ?? 0
  const x = (planSegX + (rx / 100) * planSegW) * PLAN_SCALE
  const z = (planSegY + (ry / 100) * planSegH) * PLAN_SCALE
  const y = baseY + 0.8

  const state = parseState(device)
  const visual = visualFor(device, state)

  return (
    <group position={[x, y, z]} onClick={onClick ? (e) => { e.stopPropagation(); onClick() } : undefined}>
      {visual.kind === 'light' && (
        <LightDevice baseY={y} color={visual.color} on={visual.on} intensity={visual.intensity} />
      )}
      {visual.kind === 'plug' && (
        <PlugDevice color={visual.color} on={visual.on} powerNorm={visual.powerNorm} />
      )}
      {visual.kind === 'marker' && (
        <MarkerDevice color={visual.color} online={device.online} />
      )}

      {/* Hover/selected label */}
      {hover && (
        <Billboard position={[0, 2.2, 0]}>
          <Text
            fontSize={0.7}
            color="white"
            anchorX="center"
            anchorY="bottom"
            outlineWidth={0.04}
            outlineColor="#000"
          >
            {device.name}
          </Text>
        </Billboard>
      )}
    </group>
  )
}

// ── Per-type meshes ───────────────────────────────────────────────────────────

function LightDevice({
  baseY, color, on, intensity,
}: { baseY: number; color: string; on: boolean; intensity: number }) {
  const lightRef = useRef<THREE.PointLight>(null)
  // Subtle pulse on the emissive intensity so "on" lights look alive
  useFrame(({ clock }) => {
    if (!lightRef.current) return
    const t = clock.getElapsedTime()
    lightRef.current.intensity = on ? intensity * (1 + 0.04 * Math.sin(t * 2)) : 0
  })
  return (
    <>
      <mesh>
        <sphereGeometry args={[0.5, 16, 16]} />
        <meshStandardMaterial
          color={on ? color : '#666'}
          emissive={on ? color : '#000'}
          emissiveIntensity={on ? Math.max(0.4, intensity * 0.8) : 0}
          roughness={0.25}
        />
      </mesh>
      {on && (
        <pointLight
          ref={lightRef}
          color={color}
          intensity={intensity}
          distance={18}
          decay={1.8}
          // Sit a hair above the bulb so it lights the ceiling and walls evenly.
          position={[0, 0.4, 0]}
        />
      )}
      {/* Pole down to the floor — visual hint that the device is fixed to the room. */}
      <mesh position={[0, -(0.8 + (0 - baseY) * 0) / 2, 0]}>
        <cylinderGeometry args={[0.04, 0.04, 0.8, 6]} />
        <meshStandardMaterial color="#444" />
      </mesh>
    </>
  )
}

function PlugDevice({
  color, on, powerNorm,
}: { color: string; on: boolean; powerNorm: number }) {
  // Height grows with wattage so a heavy load is visually obvious from a distance.
  const h = 0.4 + powerNorm * 1.4
  return (
    <mesh position={[0, h / 2 - 0.5, 0]}>
      <boxGeometry args={[0.7, h, 0.7]} />
      <meshStandardMaterial
        color={on ? color : '#444'}
        emissive={on ? color : '#000'}
        emissiveIntensity={on ? 0.5 + powerNorm * 0.8 : 0}
        roughness={0.4}
      />
    </mesh>
  )
}

function MarkerDevice({ color, online }: { color: string; online: boolean }) {
  return (
    <mesh>
      <icosahedronGeometry args={[0.45, 0]} />
      <meshStandardMaterial
        color={online ? color : '#555'}
        emissive={online ? color : '#000'}
        emissiveIntensity={online ? 0.35 : 0}
        roughness={0.55}
      />
    </mesh>
  )
}

// ── State parsing ────────────────────────────────────────────────────────────

type Visual =
  | { kind: 'light';  on: boolean; color: string; intensity: number }
  | { kind: 'plug';   on: boolean; color: string; powerNorm: number }
  | { kind: 'marker'; color: string }

function visualFor(device: Device, s: Record<string, unknown>): Visual {
  switch (device.type) {
    case 'HUE_LIGHT': {
      const on = !!s.on
      const brightness = typeof s.brightness === 'number' ? s.brightness : 80
      const color = hueColor(s)
      // intensity: 0..6 mapped from brightness 0..100, with a small floor when on
      const intensity = on ? 0.6 + (brightness / 100) * 5.4 : 0
      return { kind: 'light', on, color, intensity }
    }
    case 'SHELLY_PLUG': {
      const on = !!(s.relay ?? s.on)
      const pw = typeof s.power === 'number' ? s.power
              : typeof s.power_w === 'number' ? s.power_w : 0
      const powerNorm = Math.min(1, pw / 2000)  // 2 kW = full glow
      return { kind: 'plug', on, color: '#60a5fa', powerNorm }
    }
    case 'HOMEMATIC_RADIATOR': {
      const tgt = numFrom(s, 'setPointTemperature', 'set_point_temperature') ?? 20
      // Cold (15°C) = blue, warm (25°C) = red — smoothly interpolated.
      const t = Math.max(0, Math.min(1, (tgt - 15) / 10))
      const r = Math.round(60 + t * 195)
      const b = Math.round(255 - t * 195)
      return { kind: 'marker', color: `rgb(${r}, 120, ${b})` }
    }
    case 'SOLAKON_METER':
    case 'SOLAKON_INVERTER':
    case 'SOLAKON_ONE':
      return { kind: 'marker', color: '#facc15' }   // amber for energy
    case 'MQTT_SENSOR':
      return { kind: 'marker', color: '#10b981' }   // green for environment
    default:
      return { kind: 'marker', color: '#94a3b8' }
  }
}

function parseState(d: Device): Record<string, unknown> {
  try { return JSON.parse(d.lastStateJson ?? '{}') } catch { return {} }
}

function numFrom(s: Record<string, unknown>, ...keys: string[]): number | null {
  for (const k of keys) {
    const v = s[k]
    if (typeof v === 'number') return v
  }
  return null
}

/** Map Hue CIE xy → approximate sRGB hex. Falls back to warm white when missing. */
function hueColor(s: Record<string, unknown>): string {
  const cx = typeof s.colorX === 'number' ? s.colorX : null
  const cy = typeof s.colorY === 'number' ? s.colorY : null
  if (cx == null || cy == null || cy === 0) {
    // Map colorTemp (mirek) → warm/cool white. 153 cool → 500 warm.
    const ct = typeof s.colorTemp === 'number' ? s.colorTemp : 366
    const warm = Math.max(0, Math.min(1, (ct - 153) / (500 - 153)))
    // Warm: more red/orange. Cool: more blue.
    const r = 255
    const g = Math.round(255 - warm * 60)
    const b = Math.round(255 - warm * 180)
    return `rgb(${r},${g},${b})`
  }
  // CIE xyY → XYZ with Y = 1 (luminance handled by intensity).
  const Y = 1
  const X = (Y / cy) * cx
  const Z = (Y / cy) * (1 - cx - cy)
  // Linear sRGB
  let r =  3.2406 * X - 1.5372 * Y - 0.4986 * Z
  let g = -0.9689 * X + 1.8758 * Y + 0.0415 * Z
  let b =  0.0557 * X - 0.2040 * Y + 1.0570 * Z
  // Normalize & gamma
  const m = Math.max(r, g, b, 0.0001)
  r = Math.max(0, r / m); g = Math.max(0, g / m); b = Math.max(0, b / m)
  const gamma = (c: number) => c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055
  return `rgb(${Math.round(gamma(r) * 255)},${Math.round(gamma(g) * 255)},${Math.round(gamma(b) * 255)})`
}
