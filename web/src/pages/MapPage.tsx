import { useEffect, useMemo, useState } from 'react'
import { getMetadata, getNearest, getRoute, searchLocations } from '../api/pathfinderApi'
import { MapView } from '../components/MapView'
import { RoutePanel } from '../components/RoutePanel'
import { RouteSummary } from '../components/RouteSummary'
import { SearchPanel } from '../components/SearchPanel'
import { Sidebar } from '../components/Sidebar'
import type {
  MetadataResponse,
  PendingMapClick,
  RouteAlgorithm,
  RouteObjective,
  RouteRequest,
  RouteResponse,
  SearchResult,
  SelectedLocation,
} from '../types'

const DEFAULT_CENTER: [number, number] = [38.8951, -77.0703]
const DEFAULT_ZOOM = 14
const COMPARE_OBJECTIVES: RouteObjective[] = ['distance', 'time', 'balanced']

/**
 * EN: Main page/container for the rebuilt map application.
 * The state shape stays explicit and lightweight so search, snapping, and routing remain easy to trace.
 * 中文：重建后地图应用的主页面容器。
 * 状态结构刻意保持显式和轻量，方便团队理解搜索、吸附和路由的完整流转。
 */
export function MapPage() {
  const [metadata, setMetadata] = useState<MetadataResponse | null>(null)
  const [metadataError, setMetadataError] = useState<string | null>(null)

  const [searchQuery, setSearchQuery] = useState('')
  const [searchTypes, setSearchTypes] = useState<string[]>([])
  const [searchResults, setSearchResults] = useState<SearchResult[]>([])
  const [selectedSearchResult, setSelectedSearchResult] = useState<SearchResult | null>(null)
  const [searchLoading, setSearchLoading] = useState(false)
  const [searchError, setSearchError] = useState<string | null>(null)

  const [startLocation, setStartLocation] = useState<SelectedLocation | null>(null)
  const [endLocation, setEndLocation] = useState<SelectedLocation | null>(null)
  const [pendingMapClick, setPendingMapClick] = useState<PendingMapClick | null>(null)
  const [nearestLoading, setNearestLoading] = useState(false)
  const [nearestError, setNearestError] = useState<string | null>(null)

  const [selectedAlgorithm, setSelectedAlgorithm] = useState<RouteAlgorithm>('astar')
  const [selectedObjective, setSelectedObjective] = useState<RouteObjective>('distance')
  const [compareMode, setCompareMode] = useState(false)
  const [balancedWeight, setBalancedWeight] = useState(0.5)
  const [avoidHighway, setAvoidHighway] = useState(false)
  const [preferMainRoad, setPreferMainRoad] = useState(false)
  const [routeResults, setRouteResults] = useState<RouteResponse[]>([])
  const [routeLoading, setRouteLoading] = useState(false)
  const [routeError, setRouteError] = useState<string | null>(null)
  const [showDatasetBounds, setShowDatasetBounds] = useState(false)

  const [mapCenter, setMapCenter] = useState<[number, number]>(DEFAULT_CENTER)
  const [mapZoom, setMapZoom] = useState(DEFAULT_ZOOM)

  useEffect(() => {
    let active = true

    async function loadMetadata() {
      try {
        const data = await getMetadata()
        if (!active) return
        setMetadata(data)
        setSelectedAlgorithm(data.defaultAlgorithm)
        setSelectedObjective(data.routing.defaultObjective)
        setBalancedWeight(data.routing.defaultWeights.timeWeight)
      } catch (error) {
        if (!active) return
        setMetadataError(error instanceof Error ? error.message : 'Failed to load metadata')
      }
    }

    void loadMetadata()
    return () => {
      active = false
    }
  }, [])

  const canRoute = Boolean(startLocation && endLocation && !routeLoading)

  const routeOverlays = useMemo(
    () =>
      routeResults
        .filter(route => route.path.length > 1)
        .map(route => ({
          objective: route.objective,
          path: route.path.map(point => [point.lat, point.lon] as [number, number]),
        })),
    [routeResults],
  )

  async function handleSearchSubmit() {
    const query = searchQuery.trim()
    if (!query) {
      setSearchResults([])
      setSelectedSearchResult(null)
      return
    }

    setSearchLoading(true)
    setSearchError(null)

    try {
      const response = await searchLocations({
        q: query,
        types: searchTypes,
        limit: metadata?.search.defaultLimit,
      })
      setSearchResults(response.results)
      setSelectedSearchResult(response.results[0] ?? null)

      if (response.results[0]) {
        setMapCenter([response.results[0].lat, response.results[0].lon])
        setMapZoom(15)
      }
    } catch (error) {
      setSearchError(error instanceof Error ? error.message : 'Search failed')
    } finally {
      setSearchLoading(false)
    }
  }

  async function snapLocation(source: {
    lat: number
    lon: number
    label: string
    source: 'search' | 'map'
    type?: string
    subType?: string
    metadata?: Record<string, string>
  }): Promise<SelectedLocation> {
    const nearest = await getNearest({ lat: source.lat, lon: source.lon })

    return {
      label: source.label,
      nodeId: nearest.nodeId,
      lat: nearest.lat,
      lon: nearest.lon,
      snapDistanceM: nearest.distanceM,
      source: source.source,
      type: source.type,
      subType: source.subType,
      metadata: source.metadata,
      input: {
        lat: source.lat,
        lon: source.lon,
      },
    }
  }

  async function assignSearchResult(role: 'start' | 'end', result: SearchResult) {
    setNearestLoading(true)
    setNearestError(null)

    try {
      const snapped = await snapLocation({
        lat: result.lat,
        lon: result.lon,
        label: result.displayName,
        source: 'search',
        type: result.type,
        subType: result.subType,
        metadata: result.metadata,
      })

      if (role === 'start') setStartLocation(snapped)
      else setEndLocation(snapped)

      setSelectedSearchResult(result)
      setMapCenter([snapped.lat, snapped.lon])
      setMapZoom(16)
    } catch (error) {
      setNearestError(error instanceof Error ? error.message : 'Failed to snap search result')
    } finally {
      setNearestLoading(false)
    }
  }

  async function handleMapClick(lat: number, lon: number) {
    setNearestLoading(true)
    setNearestError(null)

    try {
      const snapped = await snapLocation({
        lat,
        lon,
        label: `Pinned point (${lat.toFixed(5)}, ${lon.toFixed(5)})`,
        source: 'map',
      })

      setPendingMapClick({
        lat,
        lon,
        snapped,
      })
    } catch (error) {
      setNearestError(error instanceof Error ? error.message : 'Failed to snap map click')
    } finally {
      setNearestLoading(false)
    }
  }

  function applyPendingMapClick(role: 'start' | 'end') {
    if (!pendingMapClick) return
    if (role === 'start') setStartLocation(pendingMapClick.snapped)
    else setEndLocation(pendingMapClick.snapped)
    setPendingMapClick(null)
  }

  function buildRouteRequest(objective: RouteObjective): RouteRequest | null {
    if (!startLocation || !endLocation) {
      return null
    }

    const request: RouteRequest = {
      start: { nodeId: startLocation.nodeId },
      end: { nodeId: endLocation.nodeId },
      algorithm: selectedAlgorithm,
      objective,
      roadPreferences: {
        avoidHighway,
        preferMainRoad,
      },
    }

    if (objective === 'balanced') {
      request.weights = {
        distanceWeight: 1 - balancedWeight,
        timeWeight: balancedWeight,
      }
    }

    return request
  }

  async function handleRouteSubmit() {
    if (!startLocation || !endLocation) return

    setRouteLoading(true)
    setRouteError(null)

    try {
      if (compareMode) {
        const results: RouteResponse[] = []
        for (const objective of COMPARE_OBJECTIVES) {
          const request = buildRouteRequest(objective)
          if (!request) {
            continue
          }
          const response = await getRoute(request)
          results.push(response)
        }
        setRouteResults(results)
      } else {
        const request = buildRouteRequest(selectedObjective)
        if (!request) {
          return
        }
        const response = await getRoute(request)
        setRouteResults([response])
      }
    } catch (error) {
      setRouteError(error instanceof Error ? error.message : 'Failed to load route')
      setRouteResults([])
    } finally {
      setRouteLoading(false)
    }
  }

  function handleClearRoute() {
    setRouteResults([])
    setRouteError(null)
  }

  function handleResetAll() {
    setSearchQuery('')
    setSearchTypes([])
    setSearchResults([])
    setSelectedSearchResult(null)
    setStartLocation(null)
    setEndLocation(null)
    setPendingMapClick(null)
    setSelectedObjective(metadata?.routing.defaultObjective ?? 'distance')
    setCompareMode(false)
    setBalancedWeight(metadata?.routing.defaultWeights.timeWeight ?? 0.5)
    setAvoidHighway(false)
    setPreferMainRoad(false)
    setRouteResults([])
    setSearchError(null)
    setNearestError(null)
    setRouteError(null)
    setShowDatasetBounds(false)
    setMapCenter(DEFAULT_CENTER)
    setMapZoom(DEFAULT_ZOOM)
  }

  return (
    <div className="app-shell">
      <Sidebar>
        <SearchPanel
          query={searchQuery}
          onQueryChange={setSearchQuery}
          selectedTypes={searchTypes}
          onSelectedTypesChange={setSearchTypes}
          supportedTypes={metadata?.search.supportedTypes ?? []}
          loading={searchLoading}
          error={searchError}
          results={searchResults}
          selectedResultId={selectedSearchResult?.id ?? null}
          onSubmit={handleSearchSubmit}
          onSelectResult={setSelectedSearchResult}
          onSetStart={result => void assignSearchResult('start', result)}
          onSetEnd={result => void assignSearchResult('end', result)}
        />

        <RoutePanel
          metadata={metadata}
          startLocation={startLocation}
          endLocation={endLocation}
          pendingMapClick={pendingMapClick}
          selectedAlgorithm={selectedAlgorithm}
          selectedObjective={selectedObjective}
          compareMode={compareMode}
          balancedWeight={balancedWeight}
          avoidHighway={avoidHighway}
          preferMainRoad={preferMainRoad}
          showDatasetBounds={showDatasetBounds}
          onAlgorithmChange={setSelectedAlgorithm}
          onObjectiveChange={setSelectedObjective}
          onCompareModeChange={setCompareMode}
          onBalancedWeightChange={setBalancedWeight}
          onAvoidHighwayChange={setAvoidHighway}
          onPreferMainRoadChange={setPreferMainRoad}
          onShowDatasetBoundsChange={setShowDatasetBounds}
          onApplyPendingStart={() => applyPendingMapClick('start')}
          onApplyPendingEnd={() => applyPendingMapClick('end')}
          onClearPending={() => setPendingMapClick(null)}
          onClearStart={() => setStartLocation(null)}
          onClearEnd={() => setEndLocation(null)}
          onSubmit={handleRouteSubmit}
          onClearRoute={handleClearRoute}
          onResetAll={handleResetAll}
          canRoute={canRoute}
          routeLoading={routeLoading}
          nearestLoading={nearestLoading}
          nearestError={nearestError}
          routeError={routeError}
        />

        <RouteSummary
          routes={routeResults}
          startLocation={startLocation}
          endLocation={endLocation}
          selectedObjective={selectedObjective}
          compareMode={compareMode}
          balancedWeight={balancedWeight}
          avoidHighway={avoidHighway}
          preferMainRoad={preferMainRoad}
        />

        {metadataError && <div className="panel-message panel-message--error">{metadataError}</div>}
      </Sidebar>

      <MapView
        center={mapCenter}
        zoom={mapZoom}
        searchResults={searchResults}
        selectedSearchResultId={selectedSearchResult?.id ?? null}
        startLocation={startLocation}
        endLocation={endLocation}
        pendingMapClick={pendingMapClick}
        routeOverlays={routeOverlays}
        showDatasetBounds={showDatasetBounds}
        onMapClick={latlng => void handleMapClick(latlng.lat, latlng.lng)}
        onSelectSearchResult={setSelectedSearchResult}
        onSetSearchResultStart={result => void assignSearchResult('start', result)}
        onSetSearchResultEnd={result => void assignSearchResult('end', result)}
      />
    </div>
  )
}
