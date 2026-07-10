interface StatusBadgeProps {
  /** Whether the thing being described is in its positive state (connected, configured, ...). */
  on: boolean
  /** Label shown in the positive state, e.g. "Connected". */
  onLabel: string
  /** Label shown in the negative state, e.g. "Not connected". */
  offLabel: string
}

/**
 * A small pill that shows a connection or configuration status at a glance: a colored dot plus a
 * label, green when {@code on} and neutral otherwise. Used by the Configuration panels in place of
 * a plain "Status: ..." line.
 */
export function StatusBadge({ on, onLabel, offLabel }: StatusBadgeProps) {
  return (
    <span className={`status-badge ${on ? 'status-badge--on' : 'status-badge--off'}`}>
      <span className="status-badge__dot" aria-hidden="true" />
      {on ? onLabel : offLabel}
    </span>
  )
}
