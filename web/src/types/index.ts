export type RouteAlgorithm = 'astar' | 'dijkstra' | 'bidirectional'
export type RouteObjective = 'distance' | 'time' | 'balanced' | 'safe_walk'
export type RoadPreferenceKey = 'avoidHighway' | 'preferMainRoad'

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
  distanceM?: number | null
  studentTags?: string[]
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

export interface RouteWeights {
  distanceWeight?: number
  timeWeight?: number
}

export interface RouteRoadPreferences {
  avoidHighway?: boolean
  preferMainRoad?: boolean
}

export interface RouteRequest {
  start: RouteLocationInput
  end: RouteLocationInput
  algorithm?: RouteAlgorithm
  objective?: RouteObjective
  weights?: RouteWeights
  roadPreferences?: RouteRoadPreferences
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
  objective: RouteObjective
  start: RouteResolvedLocation
  end: RouteResolvedLocation
  path: RoutePathNode[]
  distanceM: number | null
  estimatedTimeSeconds: number | null
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
    supportedObjectives: RouteObjective[]
    defaultObjective: RouteObjective
    defaultWeights: {
      distanceWeight: number
      timeWeight: number
    }
    supportedRoadPreferences: RoadPreferenceKey[]
    defaultAlgorithm: RouteAlgorithm
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

export type AnnotationCategory = 'recommendation' | 'warning' | 'tip'

export interface Annotation {
  id: number
  lat: number
  lon: number
  category: AnnotationCategory
  text: string
  author: string
  createdAt: string
}

export interface AnnotationListResponse {
  count: number
  annotations: Annotation[]
}

export interface RouteStop {
  label: string
  nodeId: string
  lat: number
  lon: number
}

export interface PendingMapClick {
  lat: number
  lon: number
  snapped: SelectedLocation
}
