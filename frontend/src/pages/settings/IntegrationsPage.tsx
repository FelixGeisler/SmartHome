import { useEffect, useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, ChevronDown, ChevronUp, Loader2, Plus, RefreshCw, ScanSearch, Settings2, Trash2, Wifi, X, Zap } from 'lucide-react'
import { api } from '@/api/client'
import type { ConfigField, FoundNetworkDevice, IntegrationInstance, IntegrationTypeInfo } from '@/api/client'
import { toast } from 'sonner'

// ── Type icons ────────────────────────────────────────────────────────────────

const TYPE_ICONS: Record<string, string> = {
  hue: '💡',
  homematic: '🌡️',
  mqtt: '📡',
  shelly: '🔌',
  solakon: '⚡',
  'solakon-gw':  '☀️',
  'solakon-one': '🌞',
}

function typeIcon(adapterType: string) {
  return TYPE_ICONS[adapterType] ?? '🔧'
}

// ── Type picker modal ─────────────────────────────────────────────────────────

function TypePickerModal({ types, onSelect, onClose }: {
  types: IntegrationTypeInfo[]
  onSelect: (t: IntegrationTypeInfo) => void
  onClose: () => void
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-[hsl(var(--card))] rounded-xl shadow-xl w-full max-w-sm mx-4">
        <div className="flex items-center justify-between p-5 border-b border-[hsl(var(--border))]">
          <h2 className="font-semibold text-sm">Choose integration type</h2>
          <button onClick={onClose} className="p-1.5 rounded-md hover:bg-[hsl(var(--accent))] transition-colors">
            <X size={15} />
          </button>
        </div>
        <div className="p-3 space-y-1">
          {types.map(t => (
            <button
              key={t.type}
              onClick={() => onSelect(t)}
              className="w-full flex items-center gap-3 px-3 py-3 rounded-lg hover:bg-[hsl(var(--accent))] transition-colors text-left"
            >
              <span className="text-xl">{typeIcon(t.type)}</span>
              <div>
                <p className="text-sm font-medium">{t.displayName}</p>
              </div>
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}

// ── Delete confirm modal ──────────────────────────────────────────────────────

function DeleteConfirmModal({ instance, deviceCount, onConfirm, onCancel, confirming }: {
  instance: IntegrationInstance
  deviceCount: number
  onConfirm: () => void
  onCancel: () => void
  confirming: boolean
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-[hsl(var(--card))] rounded-xl shadow-xl w-full max-w-sm mx-4 p-6 space-y-4">
        <div className="flex items-start gap-3">
          <AlertTriangle size={20} className="text-red-400 shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold text-sm">Remove "{instance.name}"?</p>
            {deviceCount > 0 && (
              <p className="text-xs text-[hsl(var(--muted-foreground))] mt-1">
                This will also permanently delete {deviceCount} associated device(s).
              </p>
            )}
          </div>
        </div>
        <div className="flex gap-2 justify-end">
          <button
            onClick={onCancel}
            className="px-4 py-2 rounded-lg text-sm bg-[hsl(var(--muted))] hover:opacity-80 transition-opacity"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            disabled={confirming}
            className="px-4 py-2 rounded-lg text-sm bg-red-500 text-white hover:opacity-90 transition-opacity disabled:opacity-40"
          >
            {confirming ? 'Deleting…' : deviceCount > 0 ? `Delete & ${deviceCount} device(s)` : 'Delete'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Countdown ring ────────────────────────────────────────────────────────────

function CountdownRing({ total, remaining }: { total: number; remaining: number }) {
  const r = 44
  const circ = 2 * Math.PI * r
  const offset = circ * (1 - remaining / total)
  const urgent = remaining <= 15

  return (
    <div style={{ position: 'relative', width: 112, height: 112, flexShrink: 0 }}>
      <svg
        viewBox="0 0 100 100"
        width={112}
        height={112}
        style={{ position: 'absolute', inset: 0, transform: 'rotate(-90deg)' }}
      >
        <circle cx="50" cy="50" r={r} fill="none" stroke="hsl(var(--muted))" strokeWidth="7" />
        <circle
          cx="50" cy="50" r={r}
          fill="none"
          stroke={urgent ? '#f87171' : 'hsl(var(--primary))'}
          strokeWidth="7"
          strokeLinecap="round"
          strokeDasharray={circ}
          strokeDashoffset={offset}
          style={{ transition: 'stroke-dashoffset 1s linear, stroke 0.5s' }}
        />
      </svg>
      <div style={{
        position: 'absolute', inset: 0,
        display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center',
      }}>
        <span style={{ fontSize: '1.6rem', fontWeight: 700, fontVariantNumeric: 'tabular-nums', lineHeight: 1 }}>
          {remaining}
        </span>
        <span style={{ fontSize: '0.6rem', opacity: 0.5, marginTop: 2 }}>sec</span>
      </div>
    </div>
  )
}

// ── Instance form ─────────────────────────────────────────────────────────────

function InstanceForm({ typeInfo, initial, onSave, onCancel, saving }: {
  typeInfo: IntegrationTypeInfo
  initial?: IntegrationInstance
  onSave: (name: string, config: Record<string, string>) => void
  onCancel: () => void
  saving: boolean
}) {
  const isHue       = typeInfo.type === 'hue'
  const isHomematic = typeInfo.type === 'homematic'
  const hasExistingConfig = initial != null && Object.values(initial.config).some(v => v?.trim())

  const [name, setName] = useState(initial?.name ?? '')
  const [config, setConfig] = useState<Record<string, string>>(
    Object.fromEntries(typeInfo.schema.map(f => [f.key, initial?.config[f.key] ?? '']))
  )
  const [showHelp, setShowHelp] = useState<string | null>(null)
  const [showAdvanced, setShowAdvanced] = useState(!isHue || hasExistingConfig)

  const [autoState, setAutoState] = useState<'idle' | 'searching' | 'waiting'>('idle')
  const [autoIp, setAutoIp] = useState<string | null>(null)
  const [timeLeft, setTimeLeft] = useState(60)
  const [ccuScanning, setCcuScanning] = useState(false)
  const pollingRef    = useRef<ReturnType<typeof setInterval> | null>(null)
  const timeoutRef    = useRef<ReturnType<typeof setTimeout>  | null>(null)
  const countdownRef  = useRef<ReturnType<typeof setInterval> | null>(null)
  // Ref mirrors autoIp so setInterval callbacks always read the current value
  // (React state is stale inside closures created before the state update)
  const autoIpRef = useRef<string | null>(null)

  useEffect(() => () => stopAll(), [])

  useEffect(() => {
    if (autoState !== 'waiting') return
    setTimeLeft(60)
    countdownRef.current = setInterval(() => setTimeLeft(t => Math.max(0, t - 1)), 1000)
    return () => { if (countdownRef.current) clearInterval(countdownRef.current) }
  }, [autoState])

  function stopAll() {
    if (pollingRef.current)   { clearInterval(pollingRef.current);   pollingRef.current   = null }
    if (timeoutRef.current)   { clearTimeout(timeoutRef.current);    timeoutRef.current   = null }
    if (countdownRef.current) { clearInterval(countdownRef.current); countdownRef.current = null }
  }

  function applyDiscoveredIp(ip: string) {
    autoIpRef.current = ip   // update ref immediately — safe in any closure
    setAutoIp(ip)            // update state for display
  }

  function applySuccess(found: Record<string, string>) {
    // Backend returns keys "bridgeIp" and "appKey"
    setConfig(prev => ({
      ...prev,
      bridgeIp: found['bridgeIp'] ?? prev['bridgeIp'],
      appKey:   found['appKey']   ?? prev['appKey'],
    }))
    stopAll()
    setAutoState('idle')
    setShowAdvanced(true)
    toast.success('Bridge connected — credentials filled in. Click Save to finish.')
  }

  async function handleAutoSetup() {
    autoIpRef.current = null
    setAutoIp(null)
    setAutoState('searching')

    // ── Initial call: run discovery, handle errors visibly ──
    try {
      const result = await api.integrations.autoSetup('hue')
      if (result.success && result.settingsFound) { applySuccess(result.settingsFound); return }
      if (result.linkButtonRequired) {
        if (result.ip) applyDiscoveredIp(result.ip)
      } else {
        // Discovery truly failed (no bridge found at all)
        toast.error(result.message ?? 'Auto-setup failed')
        setAutoState('idle')
        return
      }
    } catch {
      toast.error('Auto-setup request failed')
      setAutoState('idle')
      return
    }

    // ── Bridge found, waiting for button press — poll silently ──
    setAutoState('waiting')
    pollingRef.current = setInterval(async () => {
      try {
        // Always read from ref — never stale, unlike the `autoIp` state variable
        const result = await api.integrations.autoSetup('hue', autoIpRef.current ?? undefined)
        if (result.success && result.settingsFound) { applySuccess(result.settingsFound); return }
        if (result.linkButtonRequired && result.ip)   applyDiscoveredIp(result.ip)
        // Any other result (transient network error, etc.): silently continue
      } catch { /* continue */ }
    }, 2000)

    timeoutRef.current = setTimeout(() => {
      stopAll()
      setAutoState('idle')
      toast.error('Timed out — the button was not pressed in time.')
    }, 60000)
  }

  function cancelAutoSetup() {
    stopAll()
    setAutoState('idle')
  }

  async function handleFindCcu() {
    setCcuScanning(true)
    try {
      const result = await api.integrations.discoverCcu('homematic')
      if (result.found && result.ip) {
        setConfig(prev => ({ ...prev, ccuIp: result.ip! }))
        toast.success(`CCU found at ${result.ip} — enter your password and save.`)
      } else {
        toast.error(result.message ?? 'No CCU found on the network.')
      }
    } catch {
      toast.error('CCU scan failed.')
    } finally {
      setCcuScanning(false)
    }
  }

  const inputClass = 'w-full text-sm px-3 py-2 rounded-md border border-[hsl(var(--border))] bg-[hsl(var(--background))] focus:outline-none focus:ring-2 focus:ring-[hsl(var(--ring))]'

  return (
    <div className="rounded-xl border border-[hsl(var(--primary))] bg-[hsl(var(--card))] p-5 space-y-5">
      {/* Header */}
      <div className="flex items-center gap-3">
        <span className="text-2xl">{typeIcon(typeInfo.type)}</span>
        <p className="font-semibold text-sm">{typeInfo.displayName}</p>
      </div>

      {/* Instance name */}
      <div className="space-y-1.5">
        <label className="text-xs font-medium">Instance name</label>
        <input
          type="text"
          value={name}
          onChange={e => setName(e.target.value)}
          placeholder={`My ${typeInfo.displayName}`}
          className={inputClass}
        />
      </div>

      {/* ── Hue auto-connect ── */}
      {isHue && (
        <div className="rounded-xl border border-[hsl(var(--border))] overflow-hidden">
          {autoState === 'waiting' ? (
            /* Waiting — ring timer */
            <div className="p-6 flex flex-col items-center gap-4 text-center">
              <CountdownRing total={60} remaining={timeLeft} />
              <div className="space-y-1">
                <p className="text-sm font-semibold">Press the button on your Hue Bridge</p>
                {autoIp && (
                  <p className="text-xs text-[hsl(var(--muted-foreground))]">Bridge found at {autoIp}</p>
                )}
                <p className="text-xs text-[hsl(var(--muted-foreground))]">
                  Checking automatically — this will close once authorized.
                </p>
              </div>
              <button
                onClick={cancelAutoSetup}
                className="text-xs px-4 py-2 rounded-lg bg-[hsl(var(--muted))] hover:opacity-80 transition-opacity"
              >
                Cancel
              </button>
            </div>
          ) : (
            /* Idle — auto-connect CTA */
            <div className="p-5 flex items-center gap-4">
              <div className="w-10 h-10 rounded-full bg-[hsl(var(--primary)/0.12)] flex items-center justify-center shrink-0">
                <Wifi size={18} className="text-[hsl(var(--primary))]" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium">Auto-connect</p>
                <p className="text-xs text-[hsl(var(--muted-foreground))]">
                  Find your bridge and generate a key automatically.
                </p>
              </div>
              <button
                onClick={handleAutoSetup}
                disabled={autoState === 'searching'}
                className="shrink-0 px-4 py-2 rounded-lg text-sm font-medium bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] hover:opacity-90 transition-opacity disabled:opacity-50"
              >
                {autoState === 'searching' ? 'Searching…' : 'Connect'}
              </button>
            </div>
          )}
        </div>
      )}

      {/* ── Fields — Hue (advanced toggle), Homematic (scan button on IP), others (plain) ── */}
      {isHue ? (
        <div className="space-y-3">
          <button
            type="button"
            onClick={() => setShowAdvanced(v => !v)}
            className="flex items-center gap-2 text-xs text-[hsl(var(--muted-foreground))] hover:text-[hsl(var(--foreground))] transition-colors"
          >
            <Settings2 size={13} />
            {showAdvanced ? 'Hide manual configuration' : 'Manual configuration'}
            {showAdvanced ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
          </button>

          {showAdvanced && (
            <div className="rounded-xl border border-[hsl(var(--border))] p-4 space-y-4">
              {typeInfo.schema.map((f: ConfigField) => (
                <FieldInput key={f.key} field={f} value={config[f.key] ?? ''}
                  onChange={v => setConfig(prev => ({ ...prev, [f.key]: v }))}
                  showHelp={showHelp} onToggleHelp={k => setShowHelp(showHelp === k ? null : k)}
                />
              ))}
            </div>
          )}
        </div>
      ) : isHomematic ? (
        <div className="space-y-4">
          {typeInfo.schema.map((f: ConfigField) =>
            f.key === 'ccuIp' ? (
              /* IP field gets an inline scan button */
              <div key={f.key} className="space-y-1.5">
                <label className="text-xs font-medium">
                  {f.label}<span className="text-red-400 ml-0.5">*</span>
                </label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={config[f.key] ?? ''}
                    onChange={e => setConfig(prev => ({ ...prev, [f.key]: e.target.value }))}
                    placeholder={f.placeholder}
                    className="flex-1 text-sm px-3 py-2 rounded-md border border-[hsl(var(--border))] bg-[hsl(var(--background))] focus:outline-none focus:ring-2 focus:ring-[hsl(var(--ring))]"
                  />
                  <button
                    type="button"
                    onClick={handleFindCcu}
                    disabled={ccuScanning}
                    title="Scan local network for a CCU"
                    className="flex items-center gap-1.5 px-3 py-2 rounded-md text-sm border border-[hsl(var(--border))] bg-[hsl(var(--muted))] hover:bg-[hsl(var(--accent))] transition-colors disabled:opacity-50 shrink-0"
                  >
                    {ccuScanning
                      ? <Loader2 size={14} className="animate-spin" />
                      : <ScanSearch size={14} />}
                    {ccuScanning ? 'Scanning…' : 'Find CCU'}
                  </button>
                </div>
                {f.description && (
                  <p className="text-xs text-[hsl(var(--muted-foreground))]">{f.description}</p>
                )}
              </div>
            ) : (
              <FieldInput key={f.key} field={f} value={config[f.key] ?? ''}
                onChange={v => setConfig(prev => ({ ...prev, [f.key]: v }))}
                showHelp={showHelp} onToggleHelp={k => setShowHelp(showHelp === k ? null : k)}
              />
            )
          )}
        </div>
      ) : (
        <div className="space-y-4">
          {typeInfo.schema.map((f: ConfigField) => (
            <FieldInput key={f.key} field={f} value={config[f.key] ?? ''}
              onChange={v => setConfig(prev => ({ ...prev, [f.key]: v }))}
              showHelp={showHelp} onToggleHelp={k => setShowHelp(showHelp === k ? null : k)}
            />
          ))}
        </div>
      )}

      {/* Actions */}
      <div className="flex gap-2 justify-end">
        <button onClick={onCancel} className="px-4 py-2 rounded-lg text-sm bg-[hsl(var(--muted))] hover:opacity-80 transition-opacity">
          Cancel
        </button>
        <button
          onClick={() => onSave(name, config)}
          disabled={saving || !name.trim()}
          className="px-4 py-2 rounded-lg text-sm bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] hover:opacity-90 transition-opacity disabled:opacity-40"
        >
          {saving ? 'Saving…' : initial ? 'Update' : 'Create'}
        </button>
      </div>
    </div>
  )
}

// ── Field input (shared) ──────────────────────────────────────────────────────

function FieldInput({ field, value, onChange, showHelp, onToggleHelp }: {
  field: ConfigField
  value: string
  onChange: (v: string) => void
  showHelp: string | null
  onToggleHelp: (k: string) => void
}) {
  const inputClass = 'w-full text-sm px-3 py-2 rounded-md border border-[hsl(var(--border))] bg-[hsl(var(--background))] focus:outline-none focus:ring-2 focus:ring-[hsl(var(--ring))]'
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between">
        <label className="text-xs font-medium">
          {field.label}{field.required && <span className="text-red-400 ml-0.5">*</span>}
        </label>
        {field.description && (
          <button
            onClick={() => onToggleHelp(field.key)}
            className="text-xs text-[hsl(var(--primary))] hover:underline"
          >
            {showHelp === field.key ? 'Hide' : 'Help'}
          </button>
        )}
      </div>
      <input
        type={field.type === 'password' ? 'password' : 'text'}
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder={field.placeholder}
        className={inputClass}
      />
      {showHelp === field.key && (
        <p className="text-xs text-[hsl(var(--muted-foreground))]">{field.description}</p>
      )}
    </div>
  )
}

// ── Generic network-scan panel (used by Shelly + Solakon) ────────────────────

// Integration types hidden from the "Add" picker (still shown if an instance exists,
// so users can delete legacy integrations — but new ones can't be added).
// solakon-one = Modbus TCP inverter (removed per user request)
const HIDDEN_TYPES = new Set(['solakon-one'])

/** Adapter types that use the scan-and-add flow instead of a plain form. */
const SCAN_ADAPTERS: Record<string, {
  displayName: string
  icon: string
  ipConfigKey: string       // the config key that holds the IP (e.g. "deviceIp")
  ipLabel: string           // label shown in the manual-add section
  ipPlaceholder: string
  scanFn: () => Promise<{ found: FoundNetworkDevice[]; error?: string }>
}> = {
  shelly: {
    displayName: 'Shelly Devices',
    icon: '🔌',
    ipConfigKey: 'deviceIp',
    ipLabel: 'Device IP address',
    ipPlaceholder: '192.168.1.100',
    scanFn: () => api.integrations.scan('shelly'),
  },
  solakon: {
    displayName: 'Solakon IR01 Energy Meter',
    icon: '⚡',
    ipConfigKey: 'meterIp',
    ipLabel: 'Meter IP address',
    ipPlaceholder: '192.168.1.100',
    scanFn: () => api.integrations.scan('solakon'),
  },
  mqtt: {
    displayName: 'MQTT Broker',
    icon: '📡',
    ipConfigKey: 'brokerUrl',
    ipLabel: 'Broker URL',
    ipPlaceholder: 'tcp://192.168.1.100:1883',
    scanFn: () => api.integrations.scan('mqtt'),
  },
}

function NetworkScanPanel({ adapterType, onClose }: { adapterType: string; onClose: () => void }) {
  const meta = SCAN_ADAPTERS[adapterType]
  const qc   = useQueryClient()

  const [scanState, setScanState]   = useState<'idle' | 'scanning' | 'done'>('idle')
  const [found, setFound]           = useState<FoundNetworkDevice[]>([])
  const [addingIp, setAddingIp]     = useState<string | null>(null)
  const [addedIps, setAddedIps]     = useState<Set<string>>(new Set())
  const [showManual, setShowManual] = useState(false)
  const [manualName, setManualName] = useState('')
  const [manualIp,   setManualIp]   = useState('')
  const [manualSaving, setManualSaving] = useState(false)

  const inputClass = 'w-full text-sm px-3 py-2 rounded-md border border-[hsl(var(--border))] bg-[hsl(var(--background))] focus:outline-none focus:ring-2 focus:ring-[hsl(var(--ring))]'

  async function handleScan() {
    setScanState('scanning')
    setFound([])
    setAddedIps(new Set())
    try {
      const result = await meta.scanFn()
      setFound(result.found)
      setScanState('done')
      if (result.found.length === 0) toast.info(`No ${meta.displayName} found on the network.`)
    } catch {
      toast.error('Network scan failed.')
      setScanState('idle')
    }
  }

  async function handleAdd(device: FoundNetworkDevice) {
    setAddingIp(device.ip)
    try {
      await api.integrations.create({
        adapterType,
        name: device.name,
        config: { [meta.ipConfigKey]: device.ip },
      })
      setAddedIps(prev => new Set([...prev, device.ip]))
      qc.invalidateQueries({ queryKey: ['integration-instances'] })
      toast.success(`Added ${device.name}`)
    } catch {
      toast.error(`Failed to add ${device.name}`)
    } finally {
      setAddingIp(null)
    }
  }

  async function handleAddManual() {
    const trimIp = manualIp.trim(), trimName = manualName.trim()
    if (!trimIp || !trimName) return
    setManualSaving(true)
    try {
      await api.integrations.create({
        adapterType,
        name: trimName,
        config: { [meta.ipConfigKey]: trimIp },
      })
      qc.invalidateQueries({ queryKey: ['integration-instances'] })
      toast.success(`Added ${trimName}`)
      setManualIp('')
      setManualName('')
    } catch {
      toast.error('Failed to add device')
    } finally {
      setManualSaving(false)
    }
  }

  return (
    <div className="rounded-xl border border-[hsl(var(--primary))] bg-[hsl(var(--card))] p-5 space-y-5">
      {/* Header */}
      <div className="flex items-center gap-3">
        <span className="text-2xl">{meta.icon}</span>
        <div className="flex-1">
          <p className="font-semibold text-sm">{meta.displayName}</p>
          <p className="text-xs text-[hsl(var(--muted-foreground))]">Scan your network or add manually.</p>
        </div>
        <button onClick={onClose} className="p-1.5 rounded-md hover:bg-[hsl(var(--accent))] transition-colors">
          <X size={15} />
        </button>
      </div>

      {/* Scan */}
      <div className="space-y-3">
        <button
          onClick={handleScan}
          disabled={scanState === 'scanning'}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] text-sm font-medium hover:opacity-90 transition-opacity disabled:opacity-50"
        >
          {scanState === 'scanning'
            ? <><Loader2 size={14} className="animate-spin" /> Scanning…</>
            : <><ScanSearch size={14} /> Scan Network</>}
        </button>

        {scanState === 'done' && found.length === 0 && (
          <p className="text-sm text-[hsl(var(--muted-foreground))]">
            No devices found. Make sure they are powered on and on the same network.
          </p>
        )}

        {found.length > 0 && (
          <div className="space-y-2">
            <p className="text-xs text-[hsl(var(--muted-foreground))] font-medium">
              {found.length} device{found.length !== 1 ? 's' : ''} found
            </p>
            {found.map(device => {
              const added = addedIps.has(device.ip) || device.alreadyConfigured
              return (
                <div key={device.ip} className="flex items-center gap-3 p-3 rounded-lg border border-[hsl(var(--border))] bg-[hsl(var(--background))]">
                  <span className="text-base shrink-0">{meta.icon}</span>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium truncate">{device.name}</p>
                    <p className="text-xs text-[hsl(var(--muted-foreground))]">
                      {device.ip}{device.type ? ` · ${device.type}` : ''}
                    </p>
                  </div>
                  {added ? (
                    <span className="flex items-center gap-1 text-xs text-green-500 font-medium shrink-0">
                      <CheckCircle2 size={13} /> Added
                    </span>
                  ) : (
                    <button
                      onClick={() => handleAdd(device)}
                      disabled={addingIp === device.ip}
                      className="shrink-0 flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] hover:opacity-90 transition-opacity disabled:opacity-50"
                    >
                      {addingIp === device.ip ? <Loader2 size={12} className="animate-spin" /> : <Plus size={12} />}
                      Add
                    </button>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Manual add */}
      <div className="border-t border-[hsl(var(--border))] pt-4 space-y-3">
        <button
          type="button"
          onClick={() => setShowManual(v => !v)}
          className="flex items-center gap-2 text-xs text-[hsl(var(--muted-foreground))] hover:text-[hsl(var(--foreground))] transition-colors"
        >
          <Settings2 size={13} />
          Add manually
          {showManual ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
        </button>

        {showManual && (
          <div className="rounded-xl border border-[hsl(var(--border))] p-4 space-y-3">
            <div className="space-y-1.5">
              <label className="text-xs font-medium">Name</label>
              <input type="text" value={manualName} onChange={e => setManualName(e.target.value)}
                placeholder={meta.displayName.replace(/s$/, '')} className={inputClass} />
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-medium">
                {meta.ipLabel} <span className="text-red-400">*</span>
              </label>
              <input type="text" value={manualIp} onChange={e => setManualIp(e.target.value)}
                placeholder={meta.ipPlaceholder} className={inputClass} />
            </div>
            <div className="flex justify-end">
              <button
                onClick={handleAddManual}
                disabled={manualSaving || !manualIp.trim() || !manualName.trim()}
                className="px-4 py-2 rounded-lg text-sm bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] hover:opacity-90 transition-opacity disabled:opacity-40"
              >
                {manualSaving ? 'Adding…' : 'Add'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

// ── Instance card ─────────────────────────────────────────────────────────────

function InstanceCard({ instance, deviceCount, onEdit, onDelete, onTest, onDiscover, testing, discovering, expanded }: {
  instance: IntegrationInstance
  deviceCount: number
  onEdit: () => void
  onDelete: () => void
  onTest: () => void
  onDiscover: () => void
  testing: boolean
  discovering: boolean
  expanded: boolean
}) {
  return (
    <div className="rounded-xl border border-[hsl(var(--border))] bg-[hsl(var(--card))]">
      <div className="flex items-center gap-3 p-4">
        <span className="text-xl">{typeIcon(instance.adapterType)}</span>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold">{instance.name}</p>
          <p className="text-xs text-[hsl(var(--muted-foreground))] truncate">
            {instance.adapterDisplayName}
            {deviceCount > 0 && ` · ${deviceCount} device${deviceCount !== 1 ? 's' : ''}`}
          </p>
        </div>
        <div className="flex items-center gap-1 shrink-0">
          <span className={`w-2 h-2 rounded-full ${instance.running ? 'bg-green-400' : 'bg-[hsl(var(--muted-foreground))]'}`} title={instance.running ? 'Running' : 'Stopped'} />
          <button
            onClick={onTest}
            disabled={testing}
            className="p-1.5 rounded-md hover:bg-[hsl(var(--accent))] transition-colors text-[hsl(var(--muted-foreground))] disabled:opacity-40"
            title="Test connection"
          >
            <Zap size={14} className={testing ? 'animate-pulse' : ''} />
          </button>
          <button
            onClick={onDiscover}
            disabled={discovering}
            className="p-1.5 rounded-md hover:bg-[hsl(var(--accent))] transition-colors text-[hsl(var(--muted-foreground))] disabled:opacity-40"
            title="Discover devices"
          >
            <RefreshCw size={14} className={discovering ? 'animate-spin' : ''} />
          </button>
          <button
            onClick={onEdit}
            className="p-1.5 rounded-md hover:bg-[hsl(var(--accent))] transition-colors text-[hsl(var(--muted-foreground))]"
            title="Edit"
          >
            {expanded ? <ChevronUp size={15} /> : <ChevronDown size={15} />}
          </button>
          <button
            onClick={onDelete}
            className="p-1.5 rounded-md hover:bg-[hsl(var(--accent))] transition-colors text-red-400"
            title="Remove"
          >
            <Trash2 size={14} />
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export function IntegrationsPage() {
  const qc = useQueryClient()
  const [showTypePicker, setShowTypePicker] = useState(false)
  const [addingType, setAddingType] = useState<IntegrationTypeInfo | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [testingId, setTestingId] = useState<number | null>(null)
  const [discoveringId, setDiscoveringId] = useState<number | null>(null)
  const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null)

  const { data: types = [], isLoading: typesLoading } = useQuery({
    queryKey: ['integration-types'],
    queryFn: api.integrations.types,
  })

  const { data: instances = [], isLoading: instancesLoading } = useQuery({
    queryKey: ['integration-instances'],
    queryFn: api.integrations.list,
  })

  const { data: devices = [] } = useQuery({
    queryKey: ['devices'],
    queryFn: api.devices.list,
  })

  const createMutation = useMutation({
    mutationFn: ({ name, config, adapterType }: { name: string; config: Record<string, string>; adapterType: string }) =>
      api.integrations.create({ adapterType, name, config }),
    onSuccess: (inst) => {
      qc.invalidateQueries({ queryKey: ['integration-instances'] })
      setAddingType(null)
      toast.success(`"${inst.name}" created`)
    },
    onError: () => toast.error('Failed to create integration'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, name, config }: { id: number; name: string; config: Record<string, string> }) =>
      api.integrations.update(id, { name, config }),
    onSuccess: (inst) => {
      qc.invalidateQueries({ queryKey: ['integration-instances'] })
      setEditingId(null)
      toast.success(`"${inst.name}" updated`)
    },
    onError: () => toast.error('Failed to update integration'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.integrations.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['integration-instances'] })
      qc.invalidateQueries({ queryKey: ['devices'] })
      setPendingDeleteId(null)
      toast.success('Integration deleted')
    },
    onError: () => toast.error('Failed to delete integration'),
  })

  async function handleTest(instance: IntegrationInstance) {
    setTestingId(instance.id)
    try {
      const result = await api.integrations.test(instance.id)
      if (result.success) toast.success(`${instance.name}: ${result.message}`)
      else toast.error(`${instance.name}: ${result.message}`)
    } catch {
      toast.error(`${instance.name}: Test failed`)
    } finally {
      setTestingId(null)
    }
  }

  async function handleDiscover(instance: IntegrationInstance) {
    setDiscoveringId(instance.id)
    try {
      const result = await api.integrations.discover(instance.id)
      qc.invalidateQueries({ queryKey: ['devices'] })
      toast.success(`${instance.name}: ${result.discovered} device(s) found`)
    } catch {
      toast.error(`${instance.name}: Discovery failed`)
    } finally {
      setDiscoveringId(null)
    }
  }

  function deviceCountForInstance(instanceId: number) {
    return devices.filter(d => d.integrationInstanceId === instanceId).length
  }

  const pendingDeleteInstance = instances.find(i => i.id === pendingDeleteId) ?? null
  const editingInstance = instances.find(i => i.id === editingId) ?? null
  const editingTypeInfo = editingInstance ? types.find(t => t.type === editingInstance.adapterType) ?? null : null

  if (typesLoading || instancesLoading) {
    return <div className="p-6 text-sm text-[hsl(var(--muted-foreground))]">Loading…</div>
  }

  return (
    <div className="p-6 max-w-xl space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">Integrations</h1>
          <p className="text-sm text-[hsl(var(--muted-foreground))] mt-0.5">
            Connect your smart home devices. Multiple instances of the same type are supported.
          </p>
        </div>
        {!addingType && (
          <button
            onClick={() => setShowTypePicker(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] text-sm hover:opacity-90 transition-opacity"
          >
            <Plus size={14} /> Add
          </button>
        )}
      </div>

      {instances.length === 0 && !addingType && (
        <p className="text-sm text-[hsl(var(--muted-foreground))]">
          No integrations yet. Click Add to connect a device.
        </p>
      )}

      <div className="space-y-3">
        {instances.map(instance => {
          if (editingId === instance.id && editingTypeInfo) {
            return (
              <InstanceForm
                key={instance.id}
                typeInfo={editingTypeInfo}
                initial={instance}
                onSave={(name, config) => updateMutation.mutate({ id: instance.id, name, config })}
                onCancel={() => setEditingId(null)}
                saving={updateMutation.isPending}
              />
            )
          }
          return (
            <InstanceCard
              key={instance.id}
              instance={instance}
              deviceCount={deviceCountForInstance(instance.id)}
              expanded={editingId === instance.id}
              onEdit={() => setEditingId(instance.id)}
              onDelete={() => setPendingDeleteId(instance.id)}
              onTest={() => handleTest(instance)}
              onDiscover={() => handleDiscover(instance)}
              testing={testingId === instance.id}
              discovering={discoveringId === instance.id}
            />
          )
        })}

        {addingType && (
          addingType.type in SCAN_ADAPTERS ? (
            <NetworkScanPanel adapterType={addingType.type} onClose={() => setAddingType(null)} />
          ) : (
            <InstanceForm
              typeInfo={addingType}
              onSave={(name, config) => createMutation.mutate({ adapterType: addingType.type, name, config })}
              onCancel={() => setAddingType(null)}
              saving={createMutation.isPending}
            />
          )
        )}
      </div>

      {showTypePicker && (
        <TypePickerModal
          types={types.filter(t => !HIDDEN_TYPES.has(t.type))}
          onSelect={t => { setAddingType(t); setShowTypePicker(false) }}
          onClose={() => setShowTypePicker(false)}
        />
      )}

      {pendingDeleteInstance && (
        <DeleteConfirmModal
          instance={pendingDeleteInstance}
          deviceCount={deviceCountForInstance(pendingDeleteInstance.id)}
          onConfirm={() => deleteMutation.mutate(pendingDeleteInstance.id)}
          onCancel={() => setPendingDeleteId(null)}
          confirming={deleteMutation.isPending}
        />
      )}
    </div>
  )
}
