import { useEffect, useRef } from 'react'
import { Circle, CircleMarker, MapContainer, Marker, Popup, Polyline, TileLayer, Tooltip, useMap, useMapEvents } from 'react-leaflet'
import { divIcon, type LatLngBoundsExpression } from 'leaflet'
import type { Annotation, PendingMapClick, RouteObjective, SearchResult, SelectedLocation } from '../types'

const LIGHT_TILES = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
const DARK_TILES = 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'

interface MapViewProps {
  darkMode: boolean
  center: [number, number]
  zoom: number
  searchResults: SearchResult[]
  selectedSearchResultId: number | null
  startLocation: SelectedLocation | null
  endLocation: SelectedLocation | null
  pendingMapClick: PendingMapClick | null
  routeOverlays: Array<{
    objective: RouteObjective
    path: [number, number][]
  }>
  annotations: Annotation[]
  onMapClick: (latlng: { lat: number; lng: number }) => void
  onSelectSearchResult: (result: SearchResult) => void
  onSetSearchResultStart: (result: SearchResult) => void
  onSetSearchResultEnd: (result: SearchResult) => void
}

/** NEU Arlington campus center */
const NEU_CAMPUS_CENTER: [number, number] = [38.8842, -77.1016]

const campusIcon = divIcon({
  className: 'campus-marker',
  html: '<div class="campus-marker__inner"><span class="campus-marker__icon">\uD83C\uDFEB</span><span class="campus-marker__label">NEU Arlington</span></div>',
  iconSize: [120, 36],
  iconAnchor: [60, 18],
})

/** Dataset coverage bounds — used for initial fit and max bounds */
const DATA_BOUNDS: [[number, number], [number, number]] = [
  [38.78, -77.26],
  [39.04, -76.93],
]

const ROUTE_COLORS: Record<RouteObjective, string> = {
  distance: '#2563eb',
  time: '#dc2626',
  balanced: '#16a34a',
  safe_walk: '#9333ea',
}

const ROUTE_LABELS: Record<RouteObjective, string> = {
  distance: 'Shortest Distance',
  time: 'Fastest Time',
  balanced: 'Balanced',
  safe_walk: 'Safest Walk',
}

const startIcon = divIcon({
  className: 'map-pin map-pin--start',
  html: '<span>S</span>',
  iconSize: [30, 30],
  iconAnchor: [15, 30],
})

const endIcon = divIcon({
  className: 'map-pin map-pin--end',
  html: '<span>E</span>',
  iconSize: [30, 30],
  iconAnchor: [15, 30],
})

const pendingIcon = divIcon({
  className: 'map-pin map-pin--pending',
  html: '<span>?</span>',
  iconSize: [28, 28],
  iconAnchor: [14, 28],
})

const ANNOTATION_ICONS: Record<string, ReturnType<typeof divIcon>> = {
  recommendation: divIcon({ className: 'annotation-marker annotation-marker--rec', html: '\u2B50', iconSize: [24, 24], iconAnchor: [12, 12] }),
  warning: divIcon({ className: 'annotation-marker annotation-marker--warn', html: '\u26A0\uFE0F', iconSize: [24, 24], iconAnchor: [12, 12] }),
  tip: divIcon({ className: 'annotation-marker annotation-marker--tip', html: '\uD83D\uDCA1', iconSize: [24, 24], iconAnchor: [12, 12] }),
}

export function MapView({
  darkMode,
  center,
  zoom,
  searchResults,
  selectedSearchResultId,
  startLocation,
  endLocation,
  pendingMapClick,
  routeOverlays,
  annotations,
  onMapClick,
  onSelectSearchResult,
  onSetSearchResultStart,
  onSetSearchResultEnd,
}: MapViewProps) {
  return (
    <main className="map-pane">
      <MapContainer
        center={Number.isFinite(center[0]) && Number.isFinite(center[1]) ? center : [38.8951, -77.0703]}
        zoom={zoom}
        className="map-root"
        maxBounds={DATA_BOUNDS as LatLngBoundsExpression}
        maxBoundsViscosity={1.0}
        minZoom={11}
      >
        <MapViewportSync center={center} zoom={zoom} />
        <MapClickHandler onMapClick={onMapClick} />

        <TileLayer
          key={darkMode ? 'dark' : 'light'}
          attribution={darkMode
            ? '&copy; <a href="https://carto.com/">CARTO</a> &copy; <a href="https://www.openstreetmap.org/copyright">OSM</a>'
            : '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}
          url={darkMode ? DARK_TILES : LIGHT_TILES}
        />

        <Circle
          center={NEU_CAMPUS_CENTER}
          radius={300}
          pathOptions={{ color: '#7c3aed', weight: 1.5, fillColor: '#7c3aed', fillOpacity: 0.06, dashArray: '4 4' }}
        />
        <Marker position={NEU_CAMPUS_CENTER} icon={campusIcon}>
          <Popup>
            <strong>Northeastern University - Arlington Campus</strong>
            <br />3120 N Washington Blvd, Arlington, VA
          </Popup>
        </Marker>

        {searchResults.map(result => (
          <CircleMarker
            key={result.id}
            center={[result.lat, result.lon]}
            radius={selectedSearchResultId === result.id ? 8 : 6}
            pathOptions={{
              color: selectedSearchResultId === result.id ? '#0f766e' : '#2563eb',
              fillColor: selectedSearchResultId === result.id ? '#14b8a6' : '#60a5fa',
              fillOpacity: 0.85,
              weight: 2,
            }}
            eventHandlers={{ click: () => onSelectSearchResult(result) }}
          >
            <Popup>
              <div className="popup-card">
                <strong>{result.displayName}</strong>
                <div>{result.type} / {result.subType}</div>
                <div className="popup-actions">
                  <button type="button" className="small-button" onClick={() => onSetSearchResultStart(result)}>Set Start</button>
                  <button type="button" className="small-button" onClick={() => onSetSearchResultEnd(result)}>Set End</button>
                </div>
              </div>
            </Popup>
          </CircleMarker>
        ))}

        {startLocation && (
          <Marker position={[startLocation.lat, startLocation.lon]} icon={startIcon}>
            <Popup><div className="popup-card"><strong>Start</strong><div>{startLocation.label}</div></div></Popup>
          </Marker>
        )}

        {endLocation && (
          <Marker position={[endLocation.lat, endLocation.lon]} icon={endIcon}>
            <Popup><div className="popup-card"><strong>End</strong><div>{endLocation.label}</div></div></Popup>
          </Marker>
        )}

        {pendingMapClick && (
          <Marker position={[pendingMapClick.snapped.lat, pendingMapClick.snapped.lon]} icon={pendingIcon}>
            <Popup><div className="popup-card"><strong>Snapped point</strong><div>{pendingMapClick.snapped.nodeId}</div></div></Popup>
          </Marker>
        )}

        {routeOverlays.map(route => (
          <Polyline
            key={route.objective}
            positions={route.path}
            pathOptions={{ color: ROUTE_COLORS[route.objective], weight: 5, opacity: 0.85 }}
          />
        ))}

        {annotations.map(a => (
          <Marker
            key={`ann-${a.id}`}
            position={[a.lat, a.lon]}
            icon={ANNOTATION_ICONS[a.category] ?? ANNOTATION_ICONS.tip}
          >
            <Popup>
              <strong>{a.category === 'recommendation' ? '\u2B50 Recommendation' : a.category === 'warning' ? '\u26A0\uFE0F Warning' : '\uD83D\uDCA1 Tip'}</strong>
              <br />{a.text}
              <br /><em style={{ fontSize: '0.8em', color: '#64748b' }}>- {a.author}</em>
            </Popup>
          </Marker>
        ))}
      </MapContainer>

      {routeOverlays.length > 1 && (
        <div className="route-legend">
          {routeOverlays.map(r => (
            <div key={r.objective} className="route-legend__item">
              <span className="route-legend__line" style={{ backgroundColor: ROUTE_COLORS[r.objective] }} />
              <span className="route-legend__label">{ROUTE_LABELS[r.objective]}</span>
            </div>
          ))}
        </div>
      )}
    </main>
  )
}

function MapViewportSync({ center, zoom }: { center: [number, number]; zoom: number }) {
  const map = useMap()
  const hasFittedInitialBounds = useRef(false)

  useEffect(() => {
    if (!hasFittedInitialBounds.current) {
      map.fitBounds(DATA_BOUNDS, { padding: [20, 20] })
      hasFittedInitialBounds.current = true
      return
    }
    if (Number.isFinite(center[0]) && Number.isFinite(center[1])) {
      map.setView(center, zoom)
    }
  }, [center, map, zoom])

  return null
}

function MapClickHandler({ onMapClick }: { onMapClick: (latlng: { lat: number; lng: number }) => void }) {
  useMapEvents({ click(event) { onMapClick(event.latlng) } })
  return null
}
