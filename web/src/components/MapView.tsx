import { useEffect } from 'react'
import { CircleMarker, MapContainer, Marker, Popup, Polyline, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import { divIcon } from 'leaflet'
import type { PendingMapClick, SearchResult, SelectedLocation } from '../types'

interface MapViewProps {
  center: [number, number]
  zoom: number
  searchResults: SearchResult[]
  selectedSearchResultId: number | null
  startLocation: SelectedLocation | null
  endLocation: SelectedLocation | null
  pendingMapClick: PendingMapClick | null
  routePath: [number, number][]
  onMapClick: (latlng: { lat: number; lng: number }) => void
  onSelectSearchResult: (result: SearchResult) => void
  onSetSearchResultStart: (result: SearchResult) => void
  onSetSearchResultEnd: (result: SearchResult) => void
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
  routePath,
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

        {routePath.length > 1 && (
          <Polyline
            positions={routePath}
            pathOptions={{
              color: '#d97706',
              weight: 5,
              opacity: 0.85,
            }}
          />
        )}
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

  useEffect(() => {
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
