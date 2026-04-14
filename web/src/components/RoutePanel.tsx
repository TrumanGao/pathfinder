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
  selectedObjective: RouteObjective
  compareMode: boolean
  balancedWeight: number
  avoidHighway: boolean
  preferMainRoad: boolean
  showDatasetBounds: boolean
  onAlgorithmChange: (algorithm: RouteAlgorithm) => void
  onObjectiveChange: (objective: RouteObjective) => void
  onCompareModeChange: (value: boolean) => void
  onBalancedWeightChange: (value: number) => void
  onAvoidHighwayChange: (value: boolean) => void
  onPreferMainRoadChange: (value: boolean) => void
  onShowDatasetBoundsChange: (value: boolean) => void
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
  selectedObjective,
  compareMode,
  balancedWeight,
  avoidHighway,
  preferMainRoad,
  showDatasetBounds,
  onAlgorithmChange,
  onObjectiveChange,
  onCompareModeChange,
  onBalancedWeightChange,
  onAvoidHighwayChange,
  onPreferMainRoadChange,
  onShowDatasetBoundsChange,
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
  const timePercent = Math.round(balancedWeight * 100)
  const distancePercent = 100 - timePercent
  const showBalancedSlider = selectedObjective === 'balanced' || compareMode

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

      <label className="checkbox-row checkbox-row--boxed">
        <input
          type="checkbox"
          checked={compareMode}
          onChange={event => onCompareModeChange(event.target.checked)}
        />
        <span>Compare modes</span>
      </label>

      <div className="field-group">
        <label className="field-label" htmlFor="objective-select">
          Objective
        </label>
        <select
          id="objective-select"
          className="text-input"
          value={selectedObjective}
          onChange={event => onObjectiveChange(event.target.value as RouteObjective)}
          disabled={compareMode}
        >
          {(metadata?.routing.supportedObjectives ?? ['distance']).map(objective => (
            <option key={objective} value={objective}>
              {objective}
            </option>
          ))}
        </select>
        {compareMode && (
          <div className="panel-message">
            Compare mode will request distance, time, and balanced routes for the same start/end.
          </div>
        )}
      </div>

      {showBalancedSlider && (
        <div className="slider-box">
          <div className="slider-box__title">Distance ↔ Time</div>
          <input
            id="balanced-weight-slider"
            className="weight-slider"
            type="range"
            min="0"
            max="100"
            step="1"
            value={timePercent}
            onChange={event => onBalancedWeightChange(Number(event.target.value) / 100)}
          />
          <div className="slider-box__labels">
            <span>More distance</span>
            <span>More time</span>
          </div>
          <div className="slider-box__value">
            Current: {timePercent}% time / {distancePercent}% distance
          </div>
          {compareMode && (
            <div className="panel-message">
              This slider affects only the balanced route inside compare mode.
            </div>
          )}
        </div>
      )}

      {metadata?.routing.supportedRoadPreferences.length ? (
        <div className="option-box">
          <div className="option-box__title">Road Preferences</div>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={avoidHighway}
              onChange={event => onAvoidHighwayChange(event.target.checked)}
            />
            <span>Avoid highway</span>
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={preferMainRoad}
              onChange={event => onPreferMainRoadChange(event.target.checked)}
            />
            <span>Prefer main road</span>
          </label>
        </div>
      ) : null}

      <div className="option-box">
        <div className="option-box__title">Debug Map Overlays</div>
        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={showDatasetBounds}
            onChange={event => onShowDatasetBoundsChange(event.target.checked)}
          />
          <span>Show dataset bounds</span>
        </label>
      </div>

      <div className="future-box">
        <div className="future-box__title">Compare Preview</div>
        <div className="future-box__text">
          This demo compares the same start/end across distance, time, and balanced objectives using
          repeated route requests.
        </div>
      </div>

      {nearestLoading && <div className="panel-message">Snapping to nearest routable point...</div>}
      {nearestError && <div className="panel-message panel-message--error">{nearestError}</div>}
      {routeError && <div className="panel-message panel-message--error">{routeError}</div>}

      <div className="panel-actions">
        <button type="button" className="primary-button" disabled={!canRoute} onClick={onSubmit}>
          {routeLoading ? 'Calculating...' : compareMode ? 'Compare Routes' : 'Calculate Route'}
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
