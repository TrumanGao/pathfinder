import type { SelectedLocation } from '../types'

interface LocationCardProps {
  title: string
  location: SelectedLocation | null
  onClear?: () => void
}

export function LocationCard({ title, location, onClear }: LocationCardProps) {
  return (
    <div className="location-card">
      <div className="location-card__header">
        <span>{title}</span>
        {location && onClear && (
          <button type="button" className="link-button" onClick={onClear}>
            Clear
          </button>
        )}
      </div>
      {location ? (
        <div className="location-card__body">
          <div className="location-card__title">{location.label}</div>
          <div className="location-card__meta">
            <span>{location.nodeId}</span>
            <span>{location.snapDistanceM.toFixed(1)} m snap</span>
          </div>
        </div>
      ) : (
        <div className="location-card__empty">Not selected</div>
      )}
    </div>
  )
}
