// ─────────────────────────────────────────────
// API Contract Types — must stay in sync with backend
// ─────────────────────────────────────────────

export type AlgorithmType = 'dijkstra' | 'astar' | 'bibfs'

// GET /api/map-info
export interface MapBounds {
  minLat: number
  maxLat: number
  minLon: number
  maxLon: number
}

export interface MapInfo {
  bounds: MapBounds
  nodeCount: number
  edgeCount: number
}

// GET /api/poi-search?q=<keyword>&limit=10
export interface POI {
  poiId: number
  name: string
  lat: number
  lon: number
  amenity: string
  tourism: string
  shop: string
  leisure: string
  addrStreet: string
}

// GET /api/nearest-node?lat=<>&lon=<>
export interface NearestNodeResult {
  nodeId: number
  lat: number
  lon: number
  distanceM: number
}

// POST /api/route
export interface RouteRequest {
  startNodeId: number
  endNodeId: number
  algorithms: AlgorithmType[]
}

export interface PathNode {
  nodeId: number
  lat: number
  lon: number
}

export interface AlgorithmResult {
  algorithm: AlgorithmType
  path: PathNode[]
  /** visited nodes in exploration order — used for animation */
  visitedOrder: PathNode[]
  distanceM: number
  /** wall-clock time measured on the server */
  durationMs: number
  visitedCount: number
}

export interface RouteResponse {
  results: AlgorithmResult[]
}

// ─────────────────────────────────────────────
// Static map data (web/public/mapData.json)
// ─────────────────────────────────────────────

export type HighwayType =
  | 'motorway'
  | 'trunk'
  | 'primary'
  | 'secondary'
  | 'tertiary'
  | 'residential'
  | 'service'
  | 'living_street'
  | 'unclassified'
  | string

export interface MapEdge {
  u: number
  v: number
  highway: HighwayType
  uLat: number
  uLon: number
  vLat: number
  vLon: number
}

export interface MapData {
  bounds: MapBounds
  nodes: Record<number, { lat: number; lon: number }>
  edges: MapEdge[]
}

// ─────────────────────────────────────────────
// UI State types
// ─────────────────────────────────────────────

export type WaypointRole = 'start' | 'end'

export interface Waypoint {
  nodeId: number
  lat: number
  lon: number
  label: string
}

export interface AnimationState {
  isPlaying: boolean
  frame: number
  /** how many visited nodes to advance per animation frame */
  speed: number
}
