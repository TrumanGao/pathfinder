import { LocationCard } from './LocationCard'
import type {
  MetadataResponse,
  PendingMapClick,
  RouteAlgorithm,
  RouteObjective,
  SelectedLocation,
} from '../types'

interface RoutePanelProps {
  metadata: MetadataResponse | null
  startLocation: SelectedLocation | null
  endLocation: SelectedLocation | null
  pendingMapClick: PendingMapClick | null
  selectedAlgorithm: RouteAlgorithm
  onAlgorithmChange: (algorithm: RouteAlgorithm) => void
  selectedObjective: RouteObjective
  onObjectiveChange: (objective: RouteObjective) => void
  avoidHighway: boolean
  onAvoidHighwayChange: (value: boolean) => void
  preferMainRoad: boolean
  onPreferMainRoadChange: (value: boolean) => void
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

const OBJECTIVE_LABELS: Record<RouteObjective, string> = {
  distance: 'Shortest distance',
  time: 'Fastest time',
  balanced: 'Balanced',
}

export function RoutePanel({
  metadata,
  startLocation,
  endLocation,
  pendingMapClick,
  selectedAlgorithm,
  onAlgorithmChange,
  selectedObjective,
  onObjectiveChange,
  avoidHighway,
  onAvoidHighwayChange,
  preferMainRoad,
  onPreferMainRoadChange,
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
  const supportedObjectives = metadata?.routing?.supportedObjectives ?? ['distance', 'time', 'balanced']
  const supportedRoadPreferences = metadata?.routing?.supportedRoadPreferences ?? ['avoidHighway', 'preferMainRoad']

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

      <div className="field-group">
        <label className="field-label" htmlFor="objective-select">
          Objective
        </label>
        <select
          id="objective-select"
          className="text-input"
          value={selectedObjective}
          onChange={event => onObjectiveChange(event.target.value as RouteObjective)}
        >
          {supportedObjectives.map(objective => (
            <option key={objective} value={objective}>
              {OBJECTIVE_LABELS[objective] ?? objective}
            </option>
          ))}
        </select>
      </div>

      {supportedRoadPreferences.length > 0 && (
        <div className="field-group">
          <span className="field-label">Road preferences</span>
          <div className="filter-grid">
            {supportedRoadPreferences.includes('avoidHighway') && (
              <label className="checkbox-chip">
                <input
                  type="checkbox"
                  checked={avoidHighway}
                  onChange={event => onAvoidHighwayChange(event.target.checked)}
                />
                <span>Avoid highways</span>
              </label>
            )}
            {supportedRoadPreferences.includes('preferMainRoad') && (
              <label className="checkbox-chip">
                <input
                  type="checkbox"
                  checked={preferMainRoad}
                  onChange={event => onPreferMainRoadChange(event.target.checked)}
                />
                <span>Prefer main roads</span>
              </label>
            )}
          </div>
        </div>
      )}

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
