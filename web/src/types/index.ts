export type RouteAlgorithm = 'astar' | 'dijkstra'

export interface SearchResult {
  id: number
  name: string
  displayName: string
  type: string
  subType: string
  lat: number
  lon: number
  source: string
  routable: boolean
  metadata: Record<string, string>
}

export interface SearchResponse {
  query: string
  count: number
  results: SearchResult[]
}

export interface NearestResponse {
  matched: boolean
  input: {
    lat: number
    lon: number
  }
  nodeId: string
  lat: number
  lon: number
  distanceM: number
}

export interface RouteLocationInput {
  nodeId?: string
  lat?: number
  lon?: number
}

export interface RouteRequest {
  start: RouteLocationInput
  end: RouteLocationInput
  algorithm?: RouteAlgorithm
}

export interface RouteResolvedLocation {
  input: RouteLocationInput
  resolvedNodeId: string
  lat: number
  lon: number
  snapDistanceM: number
}

export interface RoutePathNode {
  nodeId: string
  lat: number
  lon: number
}

export interface RouteResponse {
  success: boolean
  algorithm: RouteAlgorithm
  start: RouteResolvedLocation
  end: RouteResolvedLocation
  path: RoutePathNode[]
  distanceM: number | null
  pathNodeCount: number
}

export interface MetadataResponse {
  algorithms: RouteAlgorithm[]
  defaultAlgorithm: RouteAlgorithm
  search: {
    supportedTypes: string[]
    defaultLimit: number
    maxLimit: number
  }
  routing: {
    supportedObjectives: string[]
  }
  dataset?: {
    nodeCount: number
    edgeCount: number
  }
}

export interface SelectedLocation {
  label: string
  nodeId: string
  lat: number
  lon: number
  snapDistanceM: number
  source: 'search' | 'map'
  type?: string
  subType?: string
  metadata?: Record<string, string>
  input: RouteLocationInput
}

export interface PendingMapClick {
  lat: number
  lon: number
  snapped: SelectedLocation
}
