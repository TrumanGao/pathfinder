import { LocationCard } from './LocationCard'
import type { MetadataResponse, PendingMapClick, RouteAlgorithm, SelectedLocation } from '../types'

interface RoutePanelProps {
  metadata: MetadataResponse | null
  startLocation: SelectedLocation | null
  endLocation: SelectedLocation | null
  pendingMapClick: PendingMapClick | null
  selectedAlgorithm: RouteAlgorithm
  onAlgorithmChange: (algorithm: RouteAlgorithm) => void
  onApplyPendingStart: () => void
  onApplyPendingEnd: () => void
  onClearPending: () => void
  onClearStart: () => void
  onClearEnd: () => void
  onSubmit: () => void
  onClearRoute: () => void
  onResetAll: () => void
  canRoute: boolean
  routeLoading: boolean
  nearestLoading: boolean
  nearestError: string | null
  routeError: string | null
}

export function RoutePanel({
  metadata,
  startLocation,
  endLocation,
  pendingMapClick,
  selectedAlgorithm,
  onAlgorithmChange,
  onApplyPendingStart,
  onApplyPendingEnd,
  onClearPending,
  onClearStart,
  onClearEnd,
  onSubmit,
  onClearRoute,
  onResetAll,
  canRoute,
  routeLoading,
  nearestLoading,
  nearestError,
  routeError,
}: RoutePanelProps) {
  return (
    <section className="panel">
      <div className="panel__header">
        <div>
          <h2 className="panel__title">Route</h2>
          <p className="panel__subtitle">Click the map or choose search results as start/end.</p>
        </div>
      </div>

      <LocationCard title="Start" location={startLocation} onClear={onClearStart} />
      <LocationCard title="End" location={endLocation} onClear={onClearEnd} />

      {pendingMapClick && (
        <div className="pending-card">
          <div className="pending-card__title">Last map click</div>
          <div className="pending-card__text">
            Clicked at {pendingMapClick.lat.toFixed(5)}, {pendingMapClick.lon.toFixed(5)}
          </div>
          <div className="pending-card__text">
            Snapped to {pendingMapClick.snapped.nodeId} ({pendingMapClick.snapped.snapDistanceM.toFixed(1)} m)
          </div>
          <div className="pending-card__actions">
            <button type="button" className="small-button" onClick={onApplyPendingStart}>
              Set Start
            </button>
            <button type="button" className="small-button" onClick={onApplyPendingEnd}>
              Set End
            </button>
            <button type="button" className="small-button small-button--ghost" onClick={onClearPending}>
              Dismiss
            </button>
          </div>
        </div>
      )}

      <div className="field-group">
        <label className="field-label" htmlFor="algorithm-select">
          Algorithm
        </label>
        <select
          id="algorithm-select"
          className="text-input"
          value={selectedAlgorithm}
          onChange={event => onAlgorithmChange(event.target.value as RouteAlgorithm)}
        >
          {(metadata?.algorithms ?? ['astar', 'dijkstra']).map(algorithm => (
            <option key={algorithm} value={algorithm}>
              {algorithm.toUpperCase()}
            </option>
          ))}
        </select>
      </div>

      <div className="future-box">
        <div className="future-box__title">Objective</div>
        <div className="future-box__text">
          Distance is the only supported objective right now.
        </div>
      </div>

      {nearestLoading && <div className="panel-message">Snapping to nearest routable point…</div>}
      {nearestError && <div className="panel-message panel-message--error">{nearestError}</div>}
      {routeError && <div className="panel-message panel-message--error">{routeError}</div>}

      <div className="panel-actions">
        <button type="button" className="primary-button" disabled={!canRoute} onClick={onSubmit}>
          {routeLoading ? 'Calculating…' : 'Calculate Route'}
        </button>
        <button type="button" className="secondary-button" onClick={onClearRoute}>
          Clear Route
        </button>
        <button type="button" className="secondary-button" onClick={onResetAll}>
          Reset All
        </button>
      </div>
    </section>
  )
}
