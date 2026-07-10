import { useEffect, useState } from 'react'
import type { ActionKind, Automation } from '../api/automations'
import {
  createAutomation,
  deleteAutomation,
  listAutomations,
  runAutomation,
  setAutomationEnabled,
  updateAutomation,
} from '../api/automations'
import type { Device } from '../api/devices'
import type { ActionDraft, AutomationDraft, ConditionDraft, TriggerDraft } from '../automationForm'
import {
  COMPARISON_OPTIONS,
  DAY_OPTIONS,
  commandDevices,
  draftError,
  draftFromAutomation,
  draftToInput,
  emptyAction,
  emptyCondition,
  emptyDraft,
  sensingDevices,
  summarize,
  switchableDevices,
} from '../automationForm'

interface AutomationsPageProps {
  devices: Device[]
}

/** Lists automations and hosts a guided builder for creating and editing them. */
export function AutomationsPage({ devices }: AutomationsPageProps) {
  const [automations, setAutomations] = useState<Automation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [status, setStatus] = useState<string | null>(null)
  const [draft, setDraft] = useState<AutomationDraft | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let active = true
    listAutomations()
      .then((loaded) => {
        if (active) {
          setAutomations(loaded)
          setLoading(false)
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(messageOf(cause))
          setLoading(false)
        }
      })
    return () => {
      active = false
    }
  }, [])

  function startNew() {
    setError(null)
    setStatus(null)
    setDraft(emptyDraft())
  }

  function startEdit(automation: Automation) {
    setError(null)
    setStatus(null)
    setDraft(draftFromAutomation(automation))
  }

  function cancel() {
    setError(null)
    setDraft(null)
  }

  function patchDraft(patch: Partial<AutomationDraft>) {
    setDraft((current) => (current ? { ...current, ...patch } : current))
  }

  function patchTrigger(patch: Partial<TriggerDraft>) {
    setDraft((current) =>
      current ? { ...current, trigger: { ...current.trigger, ...patch } } : current,
    )
  }

  function patchCondition(index: number, patch: Partial<ConditionDraft>) {
    setDraft((current) =>
      current
        ? {
            ...current,
            conditions: current.conditions.map((condition, i) =>
              i === index ? { ...condition, ...patch } : condition,
            ),
          }
        : current,
    )
  }

  function patchAction(index: number, patch: Partial<ActionDraft>) {
    setDraft((current) =>
      current
        ? {
            ...current,
            actions: current.actions.map((action, i) =>
              i === index ? { ...action, ...patch } : action,
            ),
          }
        : current,
    )
  }

  async function save() {
    if (!draft) {
      return
    }
    const problem = draftError(draft)
    if (problem) {
      setError(problem)
      return
    }
    setSaving(true)
    setError(null)
    try {
      const input = draftToInput(draft)
      if (draft.id === null) {
        const created = await createAutomation(input)
        setAutomations((current) => [...current, created])
      } else {
        const updated = await updateAutomation(draft.id, input)
        setAutomations((current) =>
          current.map((existing) => (existing.id === updated.id ? updated : existing)),
        )
      }
      setDraft(null)
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setSaving(false)
    }
  }

  async function toggleEnabled(automation: Automation) {
    setError(null)
    try {
      const updated = await setAutomationEnabled(automation.id, !automation.enabled)
      setAutomations((current) =>
        current.map((existing) => (existing.id === updated.id ? updated : existing)),
      )
    } catch (cause) {
      setError(messageOf(cause))
    }
  }

  async function run(automation: Automation) {
    setError(null)
    setStatus(null)
    try {
      await runAutomation(automation.id)
      setStatus(`Ran "${automation.name}".`)
    } catch (cause) {
      setError(messageOf(cause))
    }
  }

  async function remove(automation: Automation) {
    setError(null)
    try {
      await deleteAutomation(automation.id)
      setAutomations((current) => current.filter((existing) => existing.id !== automation.id))
    } catch (cause) {
      setError(messageOf(cause))
    }
  }

  return (
    <section className="automations">
      <div className="automations__header">
        <h2>Automations</h2>
        {draft === null && (
          <button type="button" className="automations__new" onClick={startNew}>
            New automation
          </button>
        )}
      </div>

      {error && (
        <p className="automations__error" role="alert">
          {error}
        </p>
      )}
      {status && (
        <p className="automations__status" role="status">
          {status}
        </p>
      )}

      {draft !== null ? (
        <AutomationBuilder
          draft={draft}
          devices={devices}
          saving={saving}
          onPatch={patchDraft}
          onPatchTrigger={patchTrigger}
          onAddCondition={() => patchDraft({ conditions: [...draft.conditions, emptyCondition()] })}
          onPatchCondition={patchCondition}
          onRemoveCondition={(index) =>
            patchDraft({ conditions: draft.conditions.filter((_, i) => i !== index) })
          }
          onAddAction={() => patchDraft({ actions: [...draft.actions, emptyAction()] })}
          onPatchAction={patchAction}
          onRemoveAction={(index) =>
            patchDraft({ actions: draft.actions.filter((_, i) => i !== index) })
          }
          onSave={() => void save()}
          onCancel={cancel}
        />
      ) : loading ? (
        <p className="automations__empty">Loading automations.</p>
      ) : automations.length === 0 ? (
        <p className="automations__empty">
          No automations yet. Create one to react to your sensors automatically.
        </p>
      ) : (
        <ul className="automations__list">
          {automations.map((automation) => (
            <li
              key={automation.id}
              className={
                automation.enabled ? 'automation-card' : 'automation-card automation-card--off'
              }
            >
              <div className="automation-card__head">
                <span className="automation-card__name">{automation.name}</span>
                <label className="automation-card__enable">
                  <input
                    type="checkbox"
                    checked={automation.enabled}
                    onChange={() => void toggleEnabled(automation)}
                    aria-label={`Enable ${automation.name}`}
                  />
                  <span>{automation.enabled ? 'Enabled' : 'Disabled'}</span>
                </label>
              </div>
              <p className="automation-card__summary">{summarize(automation, devices)}</p>
              <div className="automation-card__actions">
                <button type="button" onClick={() => void run(automation)}>
                  Run now
                </button>
                <button type="button" onClick={() => startEdit(automation)}>
                  Edit
                </button>
                <button
                  type="button"
                  className="automation-card__delete"
                  onClick={() => void remove(automation)}
                >
                  Delete
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

interface AutomationBuilderProps {
  draft: AutomationDraft
  devices: Device[]
  saving: boolean
  onPatch: (patch: Partial<AutomationDraft>) => void
  onPatchTrigger: (patch: Partial<TriggerDraft>) => void
  onAddCondition: () => void
  onPatchCondition: (index: number, patch: Partial<ConditionDraft>) => void
  onRemoveCondition: (index: number) => void
  onAddAction: () => void
  onPatchAction: (index: number, patch: Partial<ActionDraft>) => void
  onRemoveAction: (index: number) => void
  onSave: () => void
  onCancel: () => void
}

function AutomationBuilder({
  draft,
  devices,
  saving,
  onPatch,
  onPatchTrigger,
  onAddCondition,
  onPatchCondition,
  onRemoveCondition,
  onAddAction,
  onPatchAction,
  onRemoveAction,
  onSave,
  onCancel,
}: AutomationBuilderProps) {
  const sensing = sensingDevices(devices)
  const commandable = commandDevices(devices)
  const switchable = switchableDevices(devices)
  const triggerDevice = devices.find((device) => String(device.id) === draft.trigger.deviceId)
  const triggerSensor = triggerDevice?.sensors.find((sensor) => sensor.key === draft.trigger.sensorKey)

  return (
    <form
      className="builder"
      onSubmit={(event) => {
        event.preventDefault()
        onSave()
      }}
    >
      <label className="builder__field">
        <span>Name</span>
        <input
          value={draft.name}
          onChange={(event) => onPatch({ name: event.target.value })}
          placeholder="e.g. Vent the office"
        />
      </label>

      <fieldset className="builder__section">
        <legend>When</legend>
        <div className="builder__row">
          <select
            aria-label="Trigger type"
            value={draft.trigger.kind}
            onChange={(event) =>
              onPatchTrigger({ kind: event.target.value as TriggerDraft['kind'] })
            }
          >
            <option value="SENSOR_THRESHOLD">A sensor crosses a threshold</option>
            <option value="SCHEDULE">At a time of day</option>
          </select>
        </div>

        {draft.trigger.kind === 'SENSOR_THRESHOLD' ? (
          <div className="builder__row">
            <select
              aria-label="Trigger device"
              value={draft.trigger.deviceId}
              onChange={(event) => onPatchTrigger({ deviceId: event.target.value, sensorKey: '' })}
            >
              <option value="">Choose a sensor device</option>
              {sensing.map((device) => (
                <option key={device.id} value={device.id}>
                  {device.name}
                </option>
              ))}
            </select>
            <select
              aria-label="Trigger sensor"
              value={draft.trigger.sensorKey}
              onChange={(event) => onPatchTrigger({ sensorKey: event.target.value })}
              disabled={!triggerDevice}
            >
              <option value="">Choose a reading</option>
              {triggerDevice?.sensors.map((sensor) => (
                <option key={sensor.key} value={sensor.key}>
                  {sensor.key}
                </option>
              ))}
            </select>
            <select
              aria-label="Comparison"
              value={draft.trigger.comparison}
              onChange={(event) =>
                onPatchTrigger({ comparison: event.target.value as TriggerDraft['comparison'] })
              }
            >
              {COMPARISON_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <input
              aria-label="Threshold"
              type="number"
              value={draft.trigger.threshold}
              onChange={(event) => onPatchTrigger({ threshold: event.target.value })}
              placeholder="Threshold"
            />
            {triggerSensor && <span className="builder__unit">{triggerSensor.unit}</span>}
          </div>
        ) : (
          <div className="builder__row builder__schedule">
            <input
              aria-label="Schedule time"
              type="time"
              value={draft.trigger.atTime}
              onChange={(event) => onPatchTrigger({ atTime: event.target.value })}
            />
            <span className="builder__days">
              {DAY_OPTIONS.map((day) => (
                <label key={day.value} className="builder__day">
                  <input
                    type="checkbox"
                    aria-label={day.value}
                    checked={draft.trigger.onDays.includes(day.value)}
                    onChange={(event) =>
                      onPatchTrigger({
                        onDays: event.target.checked
                          ? [...draft.trigger.onDays, day.value]
                          : draft.trigger.onDays.filter((value) => value !== day.value),
                      })
                    }
                  />
                  {day.label}
                </label>
              ))}
            </span>
          </div>
        )}
      </fieldset>

      <fieldset className="builder__section">
        <legend>If (optional)</legend>
        {draft.conditions.map((condition, index) => (
          <div key={condition.key} className="builder__row">
            <select
              aria-label={`Condition ${index + 1} device`}
              value={condition.deviceId}
              onChange={(event) => onPatchCondition(index, { deviceId: event.target.value })}
            >
              <option value="">Choose a device</option>
              {switchable.map((device) => (
                <option key={device.id} value={device.id}>
                  {device.name}
                </option>
              ))}
            </select>
            <select
              aria-label={`Condition ${index + 1} state`}
              value={condition.expected}
              onChange={(event) =>
                onPatchCondition(index, { expected: event.target.value as ConditionDraft['expected'] })
              }
            >
              <option value="true">is on</option>
              <option value="false">is off</option>
            </select>
            <button type="button" onClick={() => onRemoveCondition(index)}>
              Remove
            </button>
          </div>
        ))}
        <button type="button" className="builder__add" onClick={onAddCondition}>
          Add condition
        </button>
      </fieldset>

      <fieldset className="builder__section">
        <legend>Then</legend>
        {draft.actions.map((action, index) => {
          const actionDevice = devices.find((device) => String(device.id) === action.deviceId)
          return (
            <div key={action.key} className="builder__action">
              <div className="builder__row">
                <select
                  aria-label={`Action ${index + 1} device`}
                  value={action.deviceId}
                  onChange={(event) => onPatchAction(index, { deviceId: event.target.value })}
                >
                  <option value="">Choose a device</option>
                  {commandable.map((device) => (
                    <option key={device.id} value={device.id}>
                      {device.name}
                    </option>
                  ))}
                </select>
                <select
                  aria-label={`Action ${index + 1} type`}
                  value={action.kind}
                  onChange={(event) =>
                    onPatchAction(index, { kind: event.target.value as ActionKind })
                  }
                >
                  <option value="DEVICE_TOGGLE">Toggle on/off</option>
                  <option value="DEVICE_COMMAND">Set state</option>
                </select>
                <button type="button" onClick={() => onRemoveAction(index)}>
                  Remove
                </button>
              </div>
              {action.kind === 'DEVICE_COMMAND' && (
                <div className="builder__row builder__command">
                  <label>
                    <span>Power</span>
                    <select
                      aria-label={`Action ${index + 1} power`}
                      value={action.power}
                      onChange={(event) =>
                        onPatchAction(index, { power: event.target.value as ActionDraft['power'] })
                      }
                    >
                      <option value="">Leave unchanged</option>
                      <option value="on">Turn on</option>
                      <option value="off">Turn off</option>
                    </select>
                  </label>
                  {actionDevice?.capabilities.includes('DIMMABLE') && (
                    <label>
                      <span>Brightness %</span>
                      <input
                        aria-label={`Action ${index + 1} brightness`}
                        type="number"
                        min="0"
                        max="100"
                        value={action.brightness}
                        onChange={(event) =>
                          onPatchAction(index, { brightness: event.target.value })
                        }
                      />
                    </label>
                  )}
                  {actionDevice?.capabilities.includes('COLOR_TEMPERATURE') && (
                    <label>
                      <span>Color temp K</span>
                      <input
                        aria-label={`Action ${index + 1} color temperature`}
                        type="number"
                        value={action.colorTemperatureK}
                        onChange={(event) =>
                          onPatchAction(index, { colorTemperatureK: event.target.value })
                        }
                      />
                    </label>
                  )}
                </div>
              )}
            </div>
          )
        })}
        <button type="button" className="builder__add" onClick={onAddAction}>
          Add action
        </button>
      </fieldset>

      <div className="builder__buttons">
        <button type="submit" className="builder__save" disabled={saving}>
          {saving ? 'Saving' : 'Save automation'}
        </button>
        <button type="button" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Something went wrong'
}
