import { useEffect, useRef } from 'react'
import { CircleMarker, MapContainer, Marker, Popup, Polyline, Rectangle, TileLayer, Tooltip, useMap, useMapEvents } from 'react-leaflet'
import { divIcon } from 'leaflet'
import type { PendingMapClick, RouteObjective, SearchResult, SelectedLocation } from '../types'

interface MapViewProps {
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
  showDatasetBounds: boolean
  onMapClick: (latlng: { lat: number; lng: number }) => void
  onSelectSearchResult: (result: SearchResult) => void
  onSetSearchResultStart: (result: SearchResult) => void
  onSetSearchResultEnd: (result: SearchResult) => void
}

const ROUTE_COLORS: Record<RouteObjective, string> = {
  distance: '#2563eb',
  time: '#dc2626',
  balanced: '#16a34a',
}

const SMALL_BOUNDS = {
  minLat: 38.81,
  maxLat: 38.99,
  minLon: -77.2,
  maxLon: -76.98,
}

const NORMAL_BOUNDS = {
  minLat: 38.8,
  maxLat: 39.01,
  minLon: -77.23,
  maxLon: -76.95,
}

const LARGE_BOUNDS = {
  minLat: 38.78,
  maxLat: 39.04,
  minLon: -77.26,
  maxLon: -76.93,
}

function toLeafletBounds(bounds: {
  minLat: number
  maxLat: number
  minLon: number
  maxLon: number
}): [[number, number], [number, number]] {
  return [
    [bounds.minLat, bounds.minLon],
    [bounds.maxLat, bounds.maxLon],
  ]
}

const DEBUG_BOUNDS = [
  {
    label: 'SMALL',
    bounds: toLeafletBounds(SMALL_BOUNDS),
    color: '#16a34a',
    weight: 2,
  },
  {
    label: 'NORMAL',
    bounds: toLeafletBounds(NORMAL_BOUNDS),
    color: '#2563eb',
    weight: 3,
  },
  {
    label: 'LARGE',
    bounds: toLeafletBounds(LARGE_BOUNDS),
    color: '#dc2626',
    weight: 2,
  },
] as const

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

/**
 * EN: Main Leaflet map view.
 * 中文：主 Leaflet 地图视图。
 */
export function MapView({
  center,
  zoom,
  searchResults,
  selectedSearchResultId,
  startLocation,
  endLocation,
  pendingMapClick,
  routeOverlays,
  showDatasetBounds,
  onMapClick,
  onSelectSearchResult,
  onSetSearchResultStart,
  onSetSearchResultEnd,
}: MapViewProps) {
  return (
    <main className="map-pane">
      <MapContainer center={center} zoom={zoom} className="map-root">
        <MapViewportSync center={center} zoom={zoom} />
        <MapClickHandler onMapClick={onMapClick} />

        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        {showDatasetBounds &&
          DEBUG_BOUNDS.map(box => (
            <Rectangle
              key={box.label}
              bounds={box.bounds}
              pathOptions={{
                color: box.color,
                weight: box.weight,
                fillColor: box.color,
                fillOpacity: 0.12,
              }}
            >
              <Tooltip sticky>{box.label}</Tooltip>
            </Rectangle>
          ))}

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
            eventHandlers={{
              click: () => onSelectSearchResult(result),
            }}
          >
            <Popup>
              <div className="popup-card">
                <strong>{result.displayName}</strong>
                <div>{result.type} / {result.subType}</div>
                <div className="popup-actions">
                  <button type="button" className="small-button" onClick={() => onSetSearchResultStart(result)}>
                    Set Start
                  </button>
                  <button type="button" className="small-button" onClick={() => onSetSearchResultEnd(result)}>
                    Set End
                  </button>
                </div>
              </div>
            </Popup>
          </CircleMarker>
        ))}

        {startLocation && (
          <Marker position={[startLocation.lat, startLocation.lon]} icon={startIcon}>
            <Popup>
              <div className="popup-card">
                <strong>Start</strong>
                <div>{startLocation.label}</div>
              </div>
            </Popup>
          </Marker>
        )}

        {endLocation && (
          <Marker position={[endLocation.lat, endLocation.lon]} icon={endIcon}>
            <Popup>
              <div className="popup-card">
                <strong>End</strong>
                <div>{endLocation.label}</div>
              </div>
            </Popup>
          </Marker>
        )}

        {pendingMapClick && (
          <Marker position={[pendingMapClick.snapped.lat, pendingMapClick.snapped.lon]} icon={pendingIcon}>
            <Popup>
              <div className="popup-card">
                <strong>Snapped point</strong>
                <div>{pendingMapClick.snapped.nodeId}</div>
              </div>
            </Popup>
          </Marker>
        )}

        {routeOverlays.map(route => (
          <Polyline
            key={route.objective}
            positions={route.path}
            pathOptions={{
              color: ROUTE_COLORS[route.objective],
              weight: 5,
              opacity: 0.85,
            }}
          />
        ))}
      </MapContainer>
    </main>
  )
}

function MapViewportSync({
  center,
  zoom,
}: {
  center: [number, number]
  zoom: number
}) {
  const map = useMap()
  const hasFittedInitialBounds = useRef(false)

  useEffect(() => {
    if (!hasFittedInitialBounds.current) {
      map.fitBounds(toLeafletBounds(NORMAL_BOUNDS), { padding: [20, 20] })
      hasFittedInitialBounds.current = true
      return
    }
    map.setView(center, zoom)
  }, [center, map, zoom])

  return null
}

/**
 * EN: Explicit map click flow for snapping to nearest routable points.
 * 中文：用于最近可路由点吸附的显式地图点击流程。
 */
function MapClickHandler({
  onMapClick,
}: {
  onMapClick: (latlng: { lat: number; lng: number }) => void
}) {
  useMapEvents({
    click(event) {
      onMapClick(event.latlng)
    },
  })

  return null
}
