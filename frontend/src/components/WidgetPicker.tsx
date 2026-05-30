import { useState } from 'react'
import { X } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/api/client'
import type { WidgetDef } from '@/api/client'
import { WIDGET_REGISTRY } from './widgets/registry'

interface Props {
  onAdd: (widget: WidgetDef) => void
  onClose: () => void
}

export function WidgetPicker({ onAdd, onClose }: Props) {
  const [step, setStep] = useState<'pick' | 'configure'>('pick')
  const [selectedType, setSelectedType] = useState<WidgetDef['type'] | null>(null)
  const [config, setConfig] = useState<Record<string, unknown>>({})

  const { data: devices = [] } = useQuery({ queryKey: ['devices'], queryFn: api.devices.list })

  const entries = Object.entries(WIDGET_REGISTRY) as [WidgetDef['type'], typeof WIDGET_REGISTRY[keyof typeof WIDGET_REGISTRY]][]

  function handleTypeSelect(type: WidgetDef['type']) {
    setSelectedType(type)
    setConfig({})
    setStep('configure')
  }

  function handleAdd() {
    if (!selectedType) return
    onAdd({ id: `widget-${Date.now()}`, type: selectedType, config })
  }

  const selectedEntry = selectedType ? WIDGET_REGISTRY[selectedType as keyof typeof WIDGET_REGISTRY] : null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-[hsl(var(--card))] rounded-xl shadow-xl w-full max-w-lg mx-4 max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-[hsl(var(--border))]">
          <h2 className="font-semibold text-base">
            {step === 'pick' ? 'Add Widget' : `Configure ${selectedEntry?.label ?? selectedType}`}
          </h2>
          <button onClick={onClose} className="p-1.5 rounded-md hover:bg-[hsl(var(--accent))] transition-colors">
            <X size={16} />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-5">
          {step === 'pick' && (
            <div className="grid grid-cols-2 gap-3">
              {entries.map(([type, entry]) => {
                const Icon = entry.icon
                return (
                  <button
                    key={type}
                    onClick={() => handleTypeSelect(type)}
                    className="flex flex-col items-start gap-2 p-4 rounded-xl border border-[hsl(var(--border))] hover:border-[hsl(var(--primary))] hover:bg-[hsl(var(--accent))] transition-colors text-left"
                  >
                    <Icon size={20} className="text-[hsl(var(--primary))]" />
                    <span className="font-medium text-sm">{entry.label}</span>
                    <span className="text-xs text-[hsl(var(--muted-foreground))]">{entry.desc}</span>
                  </button>
                )
              })}
            </div>
          )}

          {step === 'configure' && selectedType && selectedEntry && (
            <div className="space-y-4">
              <selectedEntry.ConfigForm config={config} setConfig={setConfig} devices={devices} />
            </div>
          )}
        </div>

        {/* Footer */}
        {step === 'configure' && (
          <div className="flex gap-3 p-5 border-t border-[hsl(var(--border))]">
            <button
              onClick={() => setStep('pick')}
              className="flex-1 px-4 py-2 rounded-lg text-sm border border-[hsl(var(--border))] hover:bg-[hsl(var(--accent))] transition-colors"
            >
              Back
            </button>
            <button
              onClick={handleAdd}
              className="flex-1 px-4 py-2 rounded-lg text-sm bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] hover:opacity-90 transition-opacity"
            >
              Add Widget
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
