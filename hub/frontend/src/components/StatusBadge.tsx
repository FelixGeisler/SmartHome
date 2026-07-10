interface StatusBadgeProps {
  /** Whether the described thing is in its positive state. */
  on: boolean
  onLabel: string
  offLabel: string
}

/** A small status pill: a colored dot plus a label, green when on and neutral otherwise. */
export function StatusBadge({ on, onLabel, offLabel }: StatusBadgeProps) {
  return (
    <span className={`status-badge ${on ? 'status-badge--on' : 'status-badge--off'}`}>
      <span className="status-badge__dot" aria-hidden="true" />
      {on ? onLabel : offLabel}
    </span>
  )
}
