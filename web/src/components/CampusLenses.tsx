export type LensId = 'quick_lunch' | 'study_spot' | 'safe_walk' | 'essentials'

interface LensDef {
  id: LensId
  label: string
  icon: string
  description: string
}

const LENSES: LensDef[] = [
  { id: 'quick_lunch', label: 'Quick Lunch', icon: '\uD83C\uDF5C', description: 'Nearby restaurants & cafes' },
  { id: 'study_spot', label: 'Study Spot', icon: '\uD83D\uDCDA', description: 'Libraries, cafes & quiet places' },
  { id: 'safe_walk', label: 'Safe Walk', icon: '\uD83D\uDEE1\uFE0F', description: 'Well-lit, pedestrian-friendly route' },
  { id: 'essentials', label: 'Essentials', icon: '\uD83D\uDED2', description: 'Grocery, pharmacy, bank & more' },
]

interface CampusLensesProps {
  activeLens: LensId | null
  onSelectLens: (lens: LensId) => void
}

export function CampusLenses({ activeLens, onSelectLens }: CampusLensesProps) {
  return (
    <section className="panel">
      <div className="panel__header">
        <div>
          <h2 className="panel__title">Campus Life</h2>
          <p className="panel__subtitle">Quick actions for NEU Arlington students.</p>
        </div>
      </div>
      <div className="lens-grid">
        {LENSES.map(lens => (
          <button
            key={lens.id}
            type="button"
            className={`lens-card ${activeLens === lens.id ? 'lens-card--active' : ''}`}
            onClick={() => onSelectLens(lens.id)}
          >
            <span className="lens-card__icon">{lens.icon}</span>
            <span className="lens-card__label">{lens.label}</span>
            <span className="lens-card__desc">{lens.description}</span>
          </button>
        ))}
      </div>
    </section>
  )
}
