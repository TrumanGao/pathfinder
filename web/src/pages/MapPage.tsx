import { useEffect, useMemo, useState } from 'react'
import { createAnnotation, getAnnotations, getMetadata, getNearest, getRoute, searchLocations, searchNearby } from '../api/pathfinderApi'
import { AnnotationPanel } from '../components/AnnotationPanel'
import { CampusLenses, type LensId } from '../components/CampusLenses'
import { FirstWeekGuide, type GuideItem } from '../components/FirstWeekGuide'
import { MapView } from '../components/MapView'
import { formatDuration } from '../utils/format'
import { WelcomeBanner } from '../components/WelcomeBanner'
import { NearbySearch } from '../components/NearbySearch'
import { RoutePanel } from '../components/RoutePanel'
import { RouteSummary } from '../components/RouteSummary'
import { SearchPanel } from '../components/SearchPanel'
import { Sidebar, type SidebarTab } from '../components/Sidebar'
import type {
  Annotation,
  AnnotationCategory,
  MetadataResponse,
  PendingMapClick,
  RouteAlgorithm,
  RouteObjective,
  RouteRequest,
  RouteResponse,
  RouteStop,
  SearchResult,
  SelectedLocation,
} from '../types'

const DEFAULT_CENTER: [number, number] = [38.8951, -77.0703]
const DEFAULT_ZOOM = 14
const COMPARE_OBJECTIVES: RouteObjective[] = ['distance', 'time', 'balanced', 'safe_walk']

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

  const [activeGuideId, setActiveGuideId] = useState<string | null>(null)
  const [guideResults, setGuideResults] = useState<SearchResult[]>([])
  const [guideLoading, setGuideLoading] = useState(false)
  const [activeLens, setActiveLens] = useState<LensId | null>(null)
  const [lensResults, setLensResults] = useState<SearchResult[]>([])
  const [lensLoading, setLensLoading] = useState(false)

  const [annotations, setAnnotations] = useState<Annotation[]>([])
  const [annotationLoading, setAnnotationLoading] = useState(false)

  const [nearbyResults, setNearbyResults] = useState<SearchResult[]>([])
  const [nearbyTypes, setNearbyTypes] = useState<string[]>([])
  const [nearbyRadius, setNearbyRadius] = useState(1000)
  const [nearbyLoading, setNearbyLoading] = useState(false)
  const [nearbyError, setNearbyError] = useState<string | null>(null)

  const [startLocation, setStartLocation] = useState<SelectedLocation | null>(null)
  const [endLocation, setEndLocation] = useState<SelectedLocation | null>(null)
  const [waypoints, setWaypoints] = useState<RouteStop[]>([])
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

  const [activeTab, setActiveTab] = useState<SidebarTab>('explore')
  const [darkMode, setDarkMode] = useState(false)

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
        // Load annotations on startup
        try {
          const annResponse = await getAnnotations({ lat: DEFAULT_CENTER[0], lon: DEFAULT_CENTER[1], radius: 5000 })
          if (active) setAnnotations(annResponse.annotations)
        } catch { /* ignore */ }
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

  // Restore route from URL params on startup
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const sLat = params.get('slat')
    const sLon = params.get('slon')
    const eLat = params.get('elat')
    const eLon = params.get('elon')
    const obj = params.get('objective')
    const alg = params.get('algorithm')

    const parsedSLat = Number(sLat)
    const parsedSLon = Number(sLon)
    const parsedELat = Number(eLat)
    const parsedELon = Number(eLon)
    if (!Number.isFinite(parsedSLat) || !Number.isFinite(parsedSLon) || !Number.isFinite(parsedELat) || !Number.isFinite(parsedELon)) return

    async function restoreFromUrl() {
      try {
        if (alg) setSelectedAlgorithm(alg as RouteAlgorithm)
        if (obj) setSelectedObjective(obj as RouteObjective)
        setActiveTab('route')

        const [startSnap, endSnap] = await Promise.all([
          snapLocation({ lat: parsedSLat, lon: parsedSLon, label: `Shared start`, source: 'map' }),
          snapLocation({ lat: parsedELat, lon: parsedELon, label: `Shared end`, source: 'map' }),
        ])

        setStartLocation(startSnap)
        setEndLocation(endSnap)
        setMapCenter([startSnap.lat, startSnap.lon])
        setMapZoom(14)
      } catch { /* ignore */ }
    }

    void restoreFromUrl()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Sync route state to URL
  function updateUrlParams() {
    if (!startLocation || !endLocation) return
    const params = new URLSearchParams()
    params.set('slat', startLocation.input.lat?.toString() ?? startLocation.lat.toString())
    params.set('slon', startLocation.input.lon?.toString() ?? startLocation.lon.toString())
    params.set('elat', endLocation.input.lat?.toString() ?? endLocation.lat.toString())
    params.set('elon', endLocation.input.lon?.toString() ?? endLocation.lon.toString())
    params.set('objective', selectedObjective)
    params.set('algorithm', selectedAlgorithm)
    const url = `${window.location.pathname}?${params.toString()}`
    window.history.replaceState(null, '', url)
  }

  function handleToggleDarkMode() {
    setDarkMode(prev => {
      const next = !prev
      document.documentElement.setAttribute('data-theme', next ? 'dark' : 'light')
      return next
    })
  }

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

  async function handleNearbySearch() {
    if (!pendingMapClick) return

    setNearbyLoading(true)
    setNearbyError(null)

    try {
      const response = await searchNearby({
        lat: pendingMapClick.lat,
        lon: pendingMapClick.lon,
        types: nearbyTypes,
        radius: nearbyRadius,
        limit: metadata?.search.maxLimit ?? 50,
      })
      setNearbyResults(response.results)
    } catch (error) {
      setNearbyError(error instanceof Error ? error.message : 'Nearby search failed')
      setNearbyResults([])
    } finally {
      setNearbyLoading(false)
    }
  }

  function handleClearNearby() {
    setNearbyResults([])
    setNearbyError(null)
  }

  async function handleLensSelect(lens: LensId) {
    setActiveLens(lens)

    if (lens === 'safe_walk') {
      setSelectedObjective('safe_walk')
      setPreferMainRoad(true)
      setLensResults([])
      return
    }

    // For search-based lenses, use map center as the reference point
    const [lat, lon] = mapCenter
    setLensLoading(true)

    try {
      const config: Record<string, { types: string[]; tags: string[]; radius: number }> = {
        quick_lunch: { types: ['food'], tags: [], radius: 800 },
        study_spot: { types: ['education'], tags: [], radius: 1000 },
        essentials: { types: ['shop', 'fuel', 'healthcare', 'parking'], tags: ['grocery', 'pharmacy', 'bank_atm', 'laundry'], radius: 1500 },
      }

      const c = config[lens]
      if (!c) return

      const response = await searchNearby({
        lat,
        lon,
        types: c.types,
        tags: c.tags,
        radius: c.radius,
        limit: 20,
      })
      setLensResults(response.results)
    } catch {
      setLensResults([])
    } finally {
      setLensLoading(false)
    }
  }

  async function handleRefreshAnnotations() {
    const [lat, lon] = mapCenter
    try {
      const response = await getAnnotations({ lat, lon, radius: 5000 })
      setAnnotations(response.annotations)
    } catch {
      // silently fail
    }
  }

  async function handleCreateAnnotation(params: { lat: number; lon: number; category: AnnotationCategory; text: string; author: string }) {
    setAnnotationLoading(true)
    try {
      await createAnnotation(params)
      await handleRefreshAnnotations()
    } catch {
      // silently fail
    } finally {
      setAnnotationLoading(false)
    }
  }

  // NEU Arlington campus center (approximate)
  const NEU_ARLINGTON: [number, number] = [38.8842, -77.1016]

  async function handleGuideItemClick(item: GuideItem) {
    setActiveGuideId(item.id)
    setGuideLoading(true)
    try {
      const response = await searchNearby({
        lat: NEU_ARLINGTON[0],
        lon: NEU_ARLINGTON[1],
        types: item.types,
        tags: item.tags,
        radius: item.radius,
        limit: 15,
      })
      setGuideResults(response.results)
      if (response.results[0]) {
        setMapCenter([response.results[0].lat, response.results[0].lon])
        setMapZoom(14)
      }
    } catch {
      setGuideResults([])
    } finally {
      setGuideLoading(false)
    }
  }

  function buildRouteRequestForLeg(startNodeId: string, endNodeId: string, objective: RouteObjective): RouteRequest {
    const request: RouteRequest = {
      start: { nodeId: startNodeId },
      end: { nodeId: endNodeId },
      algorithm: selectedAlgorithm,
      objective,
      roadPreferences: { avoidHighway, preferMainRoad },
    }
    if (objective === 'balanced') {
      request.weights = { distanceWeight: 1 - balancedWeight, timeWeight: balancedWeight }
    }
    return request
  }

  /** Build ordered list of nodeIds: start -> waypoints -> end */
  function getAllStopNodeIds(): string[] | null {
    if (!startLocation || !endLocation) return null
    const ids = [startLocation.nodeId]
    for (const wp of waypoints) ids.push(wp.nodeId)
    ids.push(endLocation.nodeId)
    return ids
  }

  /** Route through all legs for one objective, merge into single RouteResponse */
  async function routeAllLegs(objective: RouteObjective): Promise<RouteResponse> {
    const stopIds = getAllStopNodeIds()
    if (!stopIds || stopIds.length < 2) throw new Error('Need at least start and end')

    if (stopIds.length === 2) {
      return getRoute(buildRouteRequestForLeg(stopIds[0], stopIds[1], objective))
    }

    // Multi-leg: request each leg, merge path/distance/time
    const legs: RouteResponse[] = []
    for (let i = 0; i < stopIds.length - 1; i++) {
      legs.push(await getRoute(buildRouteRequestForLeg(stopIds[i], stopIds[i + 1], objective)))
    }

    const mergedPath = legs.flatMap((leg, i) => i === 0 ? leg.path : leg.path.slice(1))
    const totalDistance = legs.reduce((sum, leg) => sum + (leg.distanceM ?? 0), 0)
    const totalTime = legs.reduce((sum, leg) => sum + (leg.estimatedTimeSeconds ?? 0), 0)
    const allSuccess = legs.every(leg => leg.success)

    return {
      ...legs[0],
      success: allSuccess,
      path: mergedPath,
      distanceM: totalDistance,
      estimatedTimeSeconds: totalTime,
      pathNodeCount: mergedPath.length,
    }
  }

  async function handleRouteSubmit() {
    if (!startLocation || !endLocation) return

    setRouteLoading(true)
    setRouteError(null)

    try {
      if (compareMode) {
        const results: RouteResponse[] = []
        for (const objective of COMPARE_OBJECTIVES) {
          results.push(await routeAllLegs(objective))
        }
        setRouteResults(results)
      } else {
        setRouteResults([await routeAllLegs(selectedObjective)])
      }
      updateUrlParams()
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
    setWaypoints([])
    setPendingMapClick(null)
    setSelectedObjective(metadata?.routing.defaultObjective ?? 'distance')
    setCompareMode(false)
    setBalancedWeight(metadata?.routing.defaultWeights.timeWeight ?? 0.5)
    setAvoidHighway(false)
    setPreferMainRoad(false)
    setRouteResults([])
    setActiveGuideId(null)
    setGuideResults([])
    setActiveLens(null)
    setLensResults([])
    setAnnotations([])
    setNearbyResults([])
    setNearbyTypes([])
    setNearbyRadius(1000)
    setNearbyError(null)
    setSearchError(null)
    setNearestError(null)
    setRouteError(null)
    setMapCenter(DEFAULT_CENTER)
    setMapZoom(DEFAULT_ZOOM)
  }

  const exploreTab = (
    <>
      <WelcomeBanner />
      <CampusLenses activeLens={activeLens} onSelectLens={lens => void handleLensSelect(lens)} />

      {lensLoading && <div className="panel-message">Finding places...</div>}
      {lensResults.length > 0 && (
        <section className="panel">
          <div className="panel__header"><h2 className="panel__title">Results</h2></div>
          <div className="nearby-results">
            {lensResults.map(result => (
              <div key={result.id} className="nearby-result">
                <div className="nearby-result__name">{result.displayName}</div>
                <div className="nearby-result__meta">
                  {result.type} / {result.subType}
                  {result.distanceM != null && (
                    <span className="nearby-result__distance">
                      {result.distanceM < 1000 ? `${Math.round(result.distanceM)} m` : `${(result.distanceM / 1000).toFixed(1)} km`}
                    </span>
                  )}
                </div>
                {result.studentTags && result.studentTags.length > 0 && (
                  <div>{result.studentTags.map(tag => <span key={tag} className="student-tag">{tag}</span>)}</div>
                )}
                <div className="nearby-result__actions">
                  <button type="button" className="small-button" onClick={() => void assignSearchResult('start', result)}>Set Start</button>
                  <button type="button" className="small-button" onClick={() => void assignSearchResult('end', result)}>Set End</button>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      <FirstWeekGuide
        loading={guideLoading}
        results={guideResults}
        activeGuideId={activeGuideId}
        onGuideItemClick={item => void handleGuideItemClick(item)}
        onSetStart={result => void assignSearchResult('start', result)}
        onSetEnd={result => void assignSearchResult('end', result)}
      />

      <NearbySearch
        pendingMapClick={pendingMapClick}
        nearbyResults={nearbyResults}
        nearbyTypes={nearbyTypes}
        nearbyRadius={nearbyRadius}
        nearbyLoading={nearbyLoading}
        nearbyError={nearbyError}
        onNearbyTypesChange={setNearbyTypes}
        onNearbyRadiusChange={setNearbyRadius}
        onNearbySearch={handleNearbySearch}
        onClearNearby={handleClearNearby}
        onSetStart={result => void assignSearchResult('start', result)}
        onSetEnd={result => void assignSearchResult('end', result)}
      />
    </>
  )

  const searchTab = (
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
  )

  const routeTab = (
    <>
      <RoutePanel
        metadata={metadata}
        startLocation={startLocation}
        endLocation={endLocation}
        waypoints={waypoints}
        pendingMapClick={pendingMapClick}
        selectedAlgorithm={selectedAlgorithm}
        selectedObjective={selectedObjective}
        compareMode={compareMode}
        balancedWeight={balancedWeight}
        avoidHighway={avoidHighway}
        preferMainRoad={preferMainRoad}
        onAlgorithmChange={setSelectedAlgorithm}
        onObjectiveChange={setSelectedObjective}
        onCompareModeChange={setCompareMode}
        onBalancedWeightChange={setBalancedWeight}
        onAvoidHighwayChange={setAvoidHighway}
        onPreferMainRoadChange={setPreferMainRoad}
        onAddWaypoint={() => {
          if (!pendingMapClick) return
          const s = pendingMapClick.snapped
          setWaypoints(prev => [...prev, { label: s.label, nodeId: s.nodeId, lat: s.lat, lon: s.lon }])
          setPendingMapClick(null)
        }}
        onRemoveWaypoint={(index: number) => setWaypoints(prev => prev.filter((_, i) => i !== index))}
        onClearWaypoints={() => setWaypoints([])}
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
    </>
  )

  const communityTab = (
    <AnnotationPanel
      annotations={annotations}
      pendingMapClick={pendingMapClick}
      loading={annotationLoading}
      onCreateAnnotation={params => void handleCreateAnnotation(params)}
      onRefresh={() => void handleRefreshAnnotations()}
    />
  )

  return (
    <div className="app-shell">
      <Sidebar
        activeTab={activeTab}
        onTabChange={setActiveTab}
        darkMode={darkMode}
        onToggleDarkMode={handleToggleDarkMode}
        panels={{ explore: exploreTab, search: searchTab, route: routeTab, community: communityTab }}
        footer={metadataError ? <div className="panel-message panel-message--error" style={{ margin: 16 }}>{metadataError}</div> : undefined}
      />

      <div className="map-wrapper">
        <MapView
          darkMode={darkMode}
          center={mapCenter}
          zoom={mapZoom}
          searchResults={searchResults}
          selectedSearchResultId={selectedSearchResult?.id ?? null}
          startLocation={startLocation}
          endLocation={endLocation}
          pendingMapClick={pendingMapClick}
          routeOverlays={routeOverlays}
          annotations={annotations}
          onMapClick={latlng => void handleMapClick(latlng.lat, latlng.lng)}
          onSelectSearchResult={setSelectedSearchResult}
          onSetSearchResultStart={result => void assignSearchResult('start', result)}
          onSetSearchResultEnd={result => void assignSearchResult('end', result)}
        />

        {routeResults.length > 0 && routeResults[0].success && (
          <div className="route-stats-overlay">
            {routeResults.map(r => (
              <div key={r.objective} className="route-stats-card">
                <span className="route-stats-card__dot" style={{ backgroundColor: r.objective === 'distance' ? '#2563eb' : r.objective === 'time' ? '#dc2626' : r.objective === 'balanced' ? '#16a34a' : '#9333ea' }} />
                <div className="route-stats-card__info">
                  <div className="route-stats-card__label">{r.objective === 'safe_walk' ? 'Safe Walk' : r.objective}</div>
                  <div className="route-stats-card__value">
                    {r.distanceM ? `${(r.distanceM / 1000).toFixed(1)} km` : '--'}
                    {' \u00B7 '}
                    {formatDuration(r.estimatedTimeSeconds)}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
