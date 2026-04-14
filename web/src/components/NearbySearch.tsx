import type { PendingMapClick, SearchResult } from '../types'

const TYPE_OPTIONS = [
  { value: 'food', label: 'Restaurant / Cafe' },
  { value: 'fuel', label: 'Gas Station' },
  { value: 'parking', label: 'Parking' },
  { value: 'healthcare', label: 'Healthcare' },
  { value: 'education', label: 'Education' },
  { value: 'transport', label: 'Transport' },
  { value: 'shop', label: 'Shop' },
  { value: 'recreation', label: 'Recreation' },
  { value: 'lodging', label: 'Lodging' },
  { value: 'poi', label: 'POI' },
]

interface NearbySearchProps {
  pendingMapClick: PendingMapClick | null
  nearbyResults: SearchResult[]
  nearbyTypes: string[]
  nearbyRadius: number
  nearbyLoading: boolean
  nearbyError: string | null
  onNearbyTypesChange: (types: string[]) => void
  onNearbyRadiusChange: (radius: number) => void
  onNearbySearch: () => void
  onClearNearby: () => void
  onSetStart: (result: SearchResult) => void
  onSetEnd: (result: SearchResult) => void
}

export function NearbySearch({
  pendingMapClick,
  nearbyResults,
  nearbyTypes,
  nearbyRadius,
  nearbyLoading,
  nearbyError,
  onNearbyTypesChange,
  onNearbyRadiusChange,
  onNearbySearch,
  onClearNearby,
  onSetStart,
  onSetEnd,
}: NearbySearchProps) {
  function toggleType(type: string) {
    if (nearbyTypes.includes(type)) {
      onNearbyTypesChange(nearbyTypes.filter(t => t !== type))
    } else {
      onNearbyTypesChange([...nearbyTypes, type])
    }
  }

  return (
    <section className="panel">
      <div className="panel__header">
        <div>
          <h2 className="panel__title">Search Nearby</h2>
          <p className="panel__subtitle">
            {pendingMapClick
              ? `Searching around (${pendingMapClick.lat.toFixed(4)}, ${pendingMapClick.lon.toFixed(4)})`
              : 'Click the map to pick a location, then search nearby.'}
          </p>
        </div>
      </div>

      <div className="nearby-box__filters">
        {TYPE_OPTIONS.map(opt => (
          <label key={opt.value} className="checkbox-chip">
            <input
              type="checkbox"
              checked={nearbyTypes.includes(opt.value)}
              onChange={() => toggleType(opt.value)}
            />
            <span>{opt.label}</span>
          </label>
        ))}
      </div>

      <div className="nearby-box__radius">
        <label className="field-label" htmlFor="nearby-radius">
          Radius: {nearbyRadius} m
        </label>
        <input
          id="nearby-radius"
          className="weight-slider"
          type="range"
          min="100"
          max="5000"
          step="100"
          value={nearbyRadius}
          onChange={e => onNearbyRadiusChange(Number(e.target.value))}
        />
      </div>

      <div className="nearby-box__actions">
        <button
          type="button"
          className="primary-button"
          disabled={!pendingMapClick || nearbyLoading}
          onClick={onNearbySearch}
        >
          {nearbyLoading ? 'Searching...' : 'Search Nearby'}
        </button>
        {nearbyResults.length > 0 && (
          <button type="button" className="secondary-button" onClick={onClearNearby}>
            Clear
          </button>
        )}
      </div>

      {nearbyError && <div className="panel-message panel-message--error">{nearbyError}</div>}

      {nearbyResults.length > 0 && (
        <div className="nearby-results">
          {nearbyResults.map(result => (
            <div key={result.id} className="nearby-result">
              <div className="nearby-result__name">{result.displayName}</div>
              <div className="nearby-result__meta">
                {result.type} / {result.subType}
                {result.distanceM != null && (
                  <span className="nearby-result__distance">
                    {result.distanceM < 1000 ? `${Math.round(result.distanceM)} m` : `${(result.distanceM / 1000).toFixed(1)} km`}
                  </span>
                )}
              </div>
              {result.studentTags && result.studentTags.length > 0 && (
                <div>{result.studentTags.map(tag => <span key={tag} className="student-tag">{tag}</span>)}</div>
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
