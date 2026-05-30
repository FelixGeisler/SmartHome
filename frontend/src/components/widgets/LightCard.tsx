import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Lightbulb, Loader2, WifiOff } from 'lucide-react'
import { useState, useEffect, useRef } from 'react'
import { api } from '@/api/client'
import { cn } from '@/lib/utils'

// ── CIE XY ↔ sRGB helpers ────────────────────────────────────────────────────

// Both functions use the standard sRGB ↔ CIE XYZ D65 matrices.
// The browser's <input type="color"> works in sRGB; Hue's colorX/colorY are
// absolute CIE 1931 xy chromaticity coordinates (device-independent), so the
// only correct mapping is sRGB primaries → XYZ → xy.
// Using Hue's wide-gamut matrix here would treat sRGB red as Hue's much-more-
// saturated wide-gamut red primary, sending the wrong xy to the bridge.

function xyToHex(x: number, y: number, briPct: number = 100): string {
  if (y === 0) return '#ffffff'
  const Y = 1.0, X = (Y / y) * x, Z = (Y / y) * (1 - x - y)
  // CIE XYZ D65 → linear sRGB
  let r =  X * 3.2404542 - Y * 1.5371385 - Z * 0.4985314
  let g = -X * 0.9692660 + Y * 1.8760108 + Z * 0.0415560
  let b =  X * 0.0556434 - Y * 0.2040259 + Z * 1.0572252
  // Clamp out-of-sRGB-gamut negatives before scaling
  r = Math.max(0, r); g = Math.max(0, g); b = Math.max(0, b)
  const mx = Math.max(r, g, b, 1)
  const scale = Math.max(0.01, Math.min(1, briPct / 100))
  r = r / mx * scale; g = g / mx * scale; b = b / mx * scale
  const gamma = (v: number) => v <= 0.0031308 ? 12.92 * v : 1.055 * v ** (1 / 2.4) - 0.055
  const toB = (v: number) => Math.round(Math.min(1, Math.max(0, gamma(v))) * 255)
  return '#' + [toB(r), toB(g), toB(b)].map(v => v.toString(16).padStart(2, '0')).join('')
}

function hexToXy(hex: string): [number, number] | null {
  const parse = (s: string) => parseInt(s, 16) / 255
  let r = parse(hex.slice(1, 3)), g = parse(hex.slice(3, 5)), b = parse(hex.slice(5, 7))
  const lin = (v: number) => v > 0.04045 ? ((v + 0.055) / 1.055) ** 2.4 : v / 12.92
  r = lin(r); g = lin(g); b = lin(b)
  // Linear sRGB → CIE XYZ D65
  const X = r * 0.4124564 + g * 0.3575761 + b * 0.1804375
  const Y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750
  const Z = r * 0.0193339 + g * 0.1191920 + b * 0.9503041
  const s = X + Y + Z
  if (s === 0) return null   // pure black — no meaningful hue, keep current XY
  return [X / s, Y / s]
}

// Extract Hue brightness (linear 1-100) from an sRGB hex.
// xyToHex applies sRGB gamma (linear 0.5 → sRGB ≈ 0.735), so reading back the
// raw channel value gives ~73% instead of 50%.  Linearising first restores the
// correct round-trip: hexToLinearBrightness(xyToHex(x, y, n)) ≈ n.
function hexToLinearBrightness(hex: string): number {
  const toLinear = (v: number) => {
    const s = v / 255
    return s > 0.04045 ? ((s + 0.055) / 1.055) ** 2.4 : s / 12.92
  }
  const r = toLinear(parseInt(hex.slice(1, 3), 16))
  const g = toLinear(parseInt(hex.slice(3, 5), 16))
  const b = toLinear(parseInt(hex.slice(5, 7), 16))
  return Math.max(1, Math.round(Math.max(r, g, b) * 100))
}

// ─────────────────────────────────────────────────────────────────────────────

interface Props { config: Record<string, unknown> }

export function LightCard({ config }: Props) {
  const deviceId = Number(config.deviceId)
  const qc = useQueryClient()

  const { data: devices = [] } = useQuery({ queryKey: ['devices'], queryFn: api.devices.list })
  const device = devices.find(d => d.id === deviceId)

  const { data: state } = useQuery({
    queryKey: ['deviceState', deviceId],
    queryFn:  () => api.devices.getState(deviceId),
    enabled:  !!deviceId,
    refetchInterval: 30_000,
  })

  const [pendingOn,  setPendingOn]  = useState<boolean | null>(null)
  const [pendingBri, setPendingBri] = useState<number  | null>(null)
  const [pendingCt,  setPendingCt]  = useState<number  | null>(null)
  const [localBri,   setLocalBri]   = useState<number  | null>(null)
  const [localCt,    setLocalCt]    = useState<number  | null>(null)
  const [localHex,   setLocalHex]   = useState<string  | null>(null)
  const colorTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const cmd = useMutation({
    mutationFn: (payload: Record<string, unknown>) => api.devices.command(deviceId, payload),
    onSuccess: (_, variables) => {
      qc.setQueryData(
        ['deviceState', deviceId],
        (old: Record<string, unknown> | undefined) => old ? { ...old, ...variables } : old,
      )
      setTimeout(() => qc.invalidateQueries({ queryKey: ['deviceState', deviceId] }), 3_000)
    },
    onError: () => { setPendingOn(null); setPendingBri(null); setPendingCt(null) },
  })

  const busy = cmd.isPending

  useEffect(() => {
    if (!busy) { setPendingOn(null); setPendingBri(null); setPendingCt(null) }
  }, [busy])

  const isOn       = pendingOn  ?? (state?.on as boolean | undefined)
  const brightness = pendingBri ?? localBri ?? (state?.brightness as number | undefined) ?? 100
  const reachable  = (state?.reachable as boolean | undefined) ?? true
  const ctMin      = (state?.colorTempMin as number | undefined) ?? 153
  const ctMax      = (state?.colorTempMax as number | undefined) ?? 500
  const colorX     = state?.colorX as number | undefined
  const colorY     = state?.colorY as number | undefined
  const hasColor   = colorX !== undefined
  // CT slider visibility is based on device capability (colorTempMin present), not current state
  // value — prevents slider from disappearing when the light switches to XY mode
  const hasColorTemp = state?.colorTempMin !== undefined
  const colorTemp  = pendingCt ?? localCt ?? (state?.colorTemp as number | undefined) ?? Math.round((ctMin + ctMax) / 2)
  const ctPercent  = ((colorTemp - ctMin) / (ctMax - ctMin)) * 100

  // Picker shows the actual colour at current brightness — what you see is what you get.
  // With sRGB matrices the round-trip is accurate, so colours in the picker match the light.
  const currentHex = localHex
    ?? (colorX != null && colorY != null ? xyToHex(colorX, colorY, brightness) : '#ffffff')

  function sendToggle() { const n = !isOn; setPendingOn(n); cmd.mutate({ on: n }) }
  function commitBri(v: number) { setLocalBri(null); setPendingBri(v); cmd.mutate({ brightness: v }) }
  function commitCt(v: number)  { setLocalCt(null);  setPendingCt(v); cmd.mutate({ colorTemp: v }) }

  function handleColorChange(hex: string) {
    const bri = hexToLinearBrightness(hex)
    setLocalHex(hex)
    setLocalBri(bri)
    if (colorTimer.current) clearTimeout(colorTimer.current)
    colorTimer.current = setTimeout(() => {
      setLocalHex(null)
      setLocalBri(null)
      const xy = hexToXy(hex)
      if (!xy) return  // pure black — no valid hue, keep current XY unchanged
      cmd.mutate({ colorX: xy[0], colorY: xy[1], brightness: bri })
    }, 400)
  }

  if (!device) {
    return <div className="p-4 text-sm text-[hsl(var(--muted-foreground))]">Device #{deviceId} not found</div>
  }

  return (
    <div className="relative flex flex-col h-full p-4 gap-3 select-none">

      {busy && (
        <div className="absolute inset-0 z-10 rounded-xl bg-[hsl(var(--card))]/70 flex items-center justify-center">
          <Loader2 size={20} className="animate-spin text-[hsl(var(--muted-foreground))]" />
        </div>
      )}

      {/* Header */}
      <div className="flex items-center gap-2 min-w-0">
        <Lightbulb
          size={16}
          className={cn('shrink-0', isOn ? 'text-yellow-400' : 'text-[hsl(var(--muted-foreground))]')}
        />
        <span className="font-medium text-sm truncate flex-1">{device.name}</span>
        {!reachable && <WifiOff size={13} className="shrink-0 text-[hsl(var(--muted-foreground))]" />}
        <button
          onClick={sendToggle}
          aria-label={isOn ? 'Turn off' : 'Turn on'}
          className={cn(
            'shrink-0 w-10 h-5 rounded-full transition-colors duration-200 flex items-center px-0.5',
            isOn ? 'bg-yellow-400 justify-end' : 'bg-zinc-600 justify-start'
          )}
        >
          <span className="block w-4 h-4 rounded-full bg-white shadow-sm" />
        </button>
      </div>

      {device.room && (
        <p className="text-xs text-[hsl(var(--muted-foreground))] -mt-1">{device.room}</p>
      )}

      <div className="mt-auto space-y-3">

        {/* Brightness */}
        <div className="space-y-1">
          <div className="flex justify-between text-xs text-[hsl(var(--muted-foreground))]">
            <span>Brightness</span><span>{Math.round(brightness)}%</span>
          </div>
          <input type="range" min={1} max={100} value={brightness}
            onChange={e => setLocalBri(Number(e.target.value))}
            onPointerUp={e => commitBri(Number((e.target as HTMLInputElement).value))}
            className="w-full accent-yellow-400"
          />
        </div>

        {/* Color temperature */}
        {hasColorTemp && (
          <div className="space-y-1">
            <div className="flex justify-between text-xs text-[hsl(var(--muted-foreground))]">
              <span>Color temp</span><span>{Math.round(1_000_000 / colorTemp)}K</span>
            </div>
            <input type="range" min={ctMin} max={ctMax} value={colorTemp}
              onChange={e => setLocalCt(Number(e.target.value))}
              onPointerUp={e => commitCt(Number((e.target as HTMLInputElement).value))}
              style={{ background: `linear-gradient(to right, #ffa726 0%, #fff9c4 ${ctPercent}%, #b3e5fc 100%)` }}
              className="w-full"
            />
          </div>
        )}

        {/* Color picker */}
        {hasColor && (
          <div className="space-y-1">
            <p className="text-xs text-[hsl(var(--muted-foreground))]">Color</p>
            <div className="flex items-center gap-2">
              <div
                className="w-8 h-8 rounded-lg border border-white/20 shrink-0"
                style={{ background: currentHex }}
              />
              <input
                type="color"
                value={currentHex}
                onChange={e => handleColorChange(e.target.value)}
                className="flex-1 h-8 rounded-lg cursor-pointer bg-transparent border-0"
              />
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
