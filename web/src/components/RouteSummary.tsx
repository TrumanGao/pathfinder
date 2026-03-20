import type { RouteResponse, SelectedLocation } from '../types'

interface RouteSummaryProps {
  route: RouteResponse | null
  startLocation: SelectedLocation | null
  endLocation: SelectedLocation | null
}

export function RouteSummary({ route, startLocation, endLocation }: RouteSummaryProps) {
  return (
    <section className="panel">
      <div className="panel__header">
        <div>
          <h2 className="panel__title">Route Summary</h2>
          <p className="panel__subtitle">Current backend capability: distance-based routing only.</p>
        </div>
      </div>

      {route ? (
        <div className="summary-grid">
          <div className="summary-item">
            <span className="summary-item__label">Algorithm</span>
            <strong>{route.algorithm.toUpperCase()}</strong>
          </div>
          <div className="summary-item">
            <span className="summary-item__label">Distance</span>
            <strong>{route.distanceM ? `${route.distanceM.toFixed(0)} m` : 'No route'}</strong>
          </div>
          <div className="summary-item">
            <span className="summary-item__label">Path Nodes</span>
            <strong>{route.pathNodeCount}</strong>
          </div>
          <div className="summary-item">
            <span className="summary-item__label">Success</span>
            <strong>{route.success ? 'Yes' : 'No'}</strong>
          </div>
          <div className="summary-item">
            <span className="summary-item__label">Start Snap</span>
            <strong>{route.start.snapDistanceM.toFixed(1)} m</strong>
          </div>
          <div className="summary-item">
            <span className="summary-item__label">End Snap</span>
            <strong>{route.end.snapDistanceM.toFixed(1)} m</strong>
          </div>
        </div>
      ) : (
        <div className="panel-message">
          {startLocation && endLocation
            ? 'Ready to calculate a route.'
            : 'Choose a start and end point to see route details.'}
        </div>
      )}
    </section>
  )
}
