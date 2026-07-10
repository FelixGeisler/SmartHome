interface EditToolbarProps {
  editing: boolean
  /** True while a save is in flight; disables Save and Cancel. */
  saving?: boolean
  /** Label and tooltip for the add button. */
  addLabel?: string
  onEnterEdit: () => void
  onSave: () => void
  onCancel: () => void
  onAddCard: () => void
}

/** The dashboard's edit controls: enter edit mode, or add a card / save / discard while editing. */
export function EditToolbar({
  editing,
  saving = false,
  addLabel = 'Add card',
  onEnterEdit,
  onSave,
  onCancel,
  onAddCard,
}: EditToolbarProps) {
  if (!editing) {
    return (
      <div className="edit-toolbar">
        <button
          type="button"
          className="edit-toolbar__icon"
          aria-label="Edit layout"
          title="Edit layout"
          onClick={onEnterEdit}
        >
          <PencilIcon />
        </button>
      </div>
    )
  }
  return (
    <div className="edit-toolbar">
      <button
        type="button"
        className="edit-toolbar__icon"
        aria-label={addLabel}
        title={addLabel}
        onClick={onAddCard}
      >
        <PlusIcon />
      </button>
      <button
        type="button"
        className="edit-toolbar__icon"
        aria-label="Cancel"
        title="Cancel"
        disabled={saving}
        onClick={onCancel}
      >
        <CloseIcon />
      </button>
      <button
        type="button"
        className="edit-toolbar__icon edit-toolbar__save"
        aria-label="Save layout"
        title="Save layout"
        disabled={saving}
        onClick={onSave}
      >
        <CheckIcon />
      </button>
    </div>
  )
}

/** Shared attributes for the toolbar's line-art glyphs. */
const ICON = {
  viewBox: '0 0 24 24',
  width: 18,
  height: 18,
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
  focusable: false,
} as const

function PencilIcon() {
  return (
    <svg {...ICON}>
      <path d="M12 20h9" />
      <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4Z" />
    </svg>
  )
}

function PlusIcon() {
  return (
    <svg {...ICON}>
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5" y1="12" x2="19" y2="12" />
    </svg>
  )
}

function CloseIcon() {
  return (
    <svg {...ICON}>
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  )
}

function CheckIcon() {
  return (
    <svg {...ICON}>
      <polyline points="20 6 9 17 4 12" />
    </svg>
  )
}
