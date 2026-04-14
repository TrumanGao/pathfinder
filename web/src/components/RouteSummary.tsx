import type { RouteObjective, RouteResponse, SelectedLocation } from '../types'
import { formatDuration } from '../utils/format'

interface RouteSummaryProps {
  routes: RouteResponse[]
  startLocation: SelectedLocation | null
  endLocation: SelectedLocation | null
  selectedObjective: RouteObjective
  compareMode: boolean
  balancedWeight: number
  avoidHighway: boolean
  preferMainRoad: boolean
}

export function RouteSummary({
  routes,
  startLocation,
  endLocation,
  selectedObjective,
  compareMode,
  balancedWeight,
  avoidHighway,
  preferMainRoad,
}: RouteSummaryProps) {
  const primaryRoute = routes[0] ?? null
  const timePercent = Math.round(balancedWeight * 100)
  const distancePercent = 100 - timePercent

  return (
    <section className="panel">
      <div className="panel__header">
        <div>
          <h2 className="panel__title">Route Summary</h2>
          <p className="panel__subtitle">Shows the current route response plus the active routing options.</p>
        </div>
      </div>

      {compareMode && routes.length > 0 ? (
        <div className="compare-list">
          {routes.map(route => (
            <div key={route.objective} className="compare-card">
              <div className="compare-card__title">
                {route.objective === 'balanced'
                  ? `balanced (${timePercent}% time / ${distancePercent}% distance)`
                  : route.objective}
              </div>
              <div className="compare-card__meta">Distance: {route.distanceM ? `${route.distanceM.toFixed(0)} m` : 'No route'}</div>
              <div className="compare-card__meta">Estimated time: {formatDuration(route.estimatedTimeSeconds)}</div>
              <div className="compare-card__meta">Path nodes: {route.pathNodeCount}</div>
              <div className="compare-card__meta">Success: {route.success ? 'Yes' : 'No'}</div>
            </div>
          ))}
        </div>
      ) : primaryRoute ? (
        <div className="summary-grid">
          <div className="summary-item">
            <span className="summary-item__label">Algorithm</span>
            <strong>{primaryRoute.algorithm.toUpperCase()}</strong>
          </div>
          <div className="summary-item">
            <span className="summary-item__label">Objective</span>
            <strong>
              {primaryRoute.objective === 'balanced'
                ? `Balanced (${timePercent}% time / ${distancePercent}% distance)`
                : primaryRoute.objective}
            </strong>
          </div>
          <div className="summary-item">
            <span className="summary-item__label">Distance</span>
            <strong>{primaryRoute.distanceM ? `${primaryRoute.distanceM.toFixed(0)} m` : 'No route'}</strong>
          </div>
          <div className="summary-item">
            <span className="summary-item__label">Estimated Time</span>
            <strong>{formatDuration(primaryRoute.estimatedTimeSeconds)}</strong>
          </div>
          <div className="summary-item">
            <span className="summary-item__label">Path Nodes</span>
            <strong>{primaryRoute.pathNodeCount}</strong>
          </div>
          <div className="summary-item">
            <span className="summary-item__label">Success</span>
            <strong>{primaryRoute.success ? 'Yes' : 'No'}</strong>
          </div>
          <div className="summary-item">
            <span className="summary-item__label">Start Snap</span>
            <strong>{primaryRoute.start.snapDistanceM.toFixed(1)} m</strong>
          </div>
          <div className="summary-item">
            <span className="summary-item__label">End Snap</span>
            <strong>{primaryRoute.end.snapDistanceM.toFixed(1)} m</strong>
          </div>
          <div className="summary-item">
            <span className="summary-item__label">Avoid Highway</span>
            <strong>{avoidHighway ? 'On' : 'Off'}</strong>
          </div>
          <div className="summary-item">
            <span className="summary-item__label">Prefer Main Road</span>
            <strong>{preferMainRoad ? 'On' : 'Off'}</strong>
          </div>
        </div>
      ) : (
        <div className="panel-message">
          {startLocation && endLocation
            ? compareMode
              ? 'Ready to compare distance, time, and balanced routes.'
              : `Ready to calculate a ${selectedObjective} route.`
            : 'Choose a start and end point to see route details.'}
        </div>
      )}
    </section>
  )
}
