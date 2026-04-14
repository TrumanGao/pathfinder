import type { SearchResult } from '../types'

interface GuideItem {
  id: string
  label: string
  icon: string
  types: string[]
  tags: string[]
  radius: number
}

const GUIDE_ITEMS: GuideItem[] = [
  { id: 'grocery', label: 'Grocery Store', icon: '\uD83D\uDED2', types: ['shop'], tags: ['grocery'], radius: 2000 },
  { id: 'pharmacy', label: 'Pharmacy', icon: '\uD83D\uDC8A', types: [], tags: ['pharmacy'], radius: 2000 },
  { id: 'bank', label: 'Bank / ATM', icon: '\uD83C\uDFE6', types: [], tags: ['bank_atm'], radius: 2000 },
  { id: 'metro', label: 'Metro / Bus Stop', icon: '\uD83D\uDE87', types: ['transport'], tags: [], radius: 2000 },
  { id: 'asian', label: 'Asian Food', icon: '\uD83C\uDF5C', types: [], tags: ['asian_food'], radius: 3000 },
  { id: 'boba', label: 'Bubble Tea', icon: '\uD83E\uDD64', types: [], tags: ['bubble_tea'], radius: 3000 },
]

interface FirstWeekGuideProps {
  loading: boolean
  results: SearchResult[]
  activeGuideId: string | null
  onGuideItemClick: (item: GuideItem) => void
  onSetStart: (result: SearchResult) => void
  onSetEnd: (result: SearchResult) => void
}

export type { GuideItem }

export function FirstWeekGuide({ loading, results, activeGuideId, onGuideItemClick, onSetStart, onSetEnd }: FirstWeekGuideProps) {
  return (
    <section className="panel">
      <div className="panel__header">
        <div>
          <h2 className="panel__title">First Week Guide</h2>
          <p className="panel__subtitle">Find essential places near NEU Arlington campus.</p>
        </div>
      </div>

      <div className="guide-grid">
        {GUIDE_ITEMS.map(item => (
          <button
            key={item.id}
            type="button"
            className={`guide-btn ${activeGuideId === item.id ? 'guide-btn--active' : ''}`}
            onClick={() => onGuideItemClick(item)}
            disabled={loading}
          >
            <span className="guide-btn__icon">{item.icon}</span>
            <span className="guide-btn__label">{item.label}</span>
          </button>
        ))}
      </div>

      {loading && <div className="panel-message">Searching...</div>}

      {results.length > 0 && (
        <div className="nearby-results">
          {results.map(result => (
            <div key={result.id} className="nearby-result">
              <div className="nearby-result__name">{result.displayName}</div>
              <div className="nearby-result__meta">
                {result.type} / {result.subType}
                {result.distanceM != null && (
                  <span className="nearby-result__distance">
                    {result.distanceM < 1000
                      ? `${Math.round(result.distanceM)} m`
                      : `${(result.distanceM / 1000).toFixed(1)} km`}
                  </span>
                )}
              </div>
              {result.studentTags && result.studentTags.length > 0 && (
                <div>
                  {result.studentTags.map(tag => (
                    <span key={tag} className="student-tag">{tag}</span>
                  ))}
                </div>
              )}
              <div className="nearby-result__actions">
                <button type="button" className="small-button" onClick={() => onSetStart(result)}>Set Start</button>
                <button type="button" className="small-button" onClick={() => onSetEnd(result)}>Set End</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
