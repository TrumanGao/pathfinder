import type {
  MapInfo,
  POI,
  NearestNodeResult,
  RouteRequest,
  RouteResponse,
} from '../types'

// ─────────────────────────────────────────────
// Toggle: set to false when backend is ready
// ─────────────────────────────────────────────
const USE_MOCK = false

// ─────────────────────────────────────────────
// Real API calls (used when USE_MOCK = false)
// ─────────────────────────────────────────────

async function apiFetch<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init)
  if (!res.ok) throw new Error(`API error ${res.status}: ${url}`)
  return res.json() as Promise<T>
}

export async function getMapInfo(): Promise<MapInfo> {
  if (USE_MOCK) return mockGetMapInfo()
  return apiFetch<MapInfo>('/api/map-info')
}

export async function searchPOI(q: string, limit = 10): Promise<POI[]> {
  if (USE_MOCK) return mockSearchPOI(q, limit)
  return apiFetch<POI[]>(`/api/poi-search?q=${encodeURIComponent(q)}&limit=${limit}`)
}

export async function getNearestNode(
  lat: number,
  lon: number,
): Promise<NearestNodeResult> {
  if (USE_MOCK) return mockGetNearestNode(lat, lon)
  return apiFetch<NearestNodeResult>(`/api/nearest-node?lat=${lat}&lon=${lon}`)
}

export async function postRoute(req: RouteRequest): Promise<RouteResponse> {
  if (USE_MOCK) return mockPostRoute(req)
  return apiFetch<RouteResponse>('/api/route', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
}

// ─────────────────────────────────────────────
// Mock implementations
// ─────────────────────────────────────────────

async function mockGetMapInfo(): Promise<MapInfo> {
  await delay(80)
  return {
    bounds: {
      minLat: 38.8807778,
      maxLat: 38.9007444,
      minLon: -77.0860286,
      maxLon: -77.0558452,
    },
    nodeCount: 2605,
    edgeCount: 3993,
  }
}

async function mockSearchPOI(q: string, limit: number): Promise<POI[]> {
  await delay(120)
  const all: POI[] = [
    { poiId: 1,  name: 'Sunoco Gas Station',    lat: 38.8930, lon: -77.0720, amenity: 'fuel',       tourism: '', shop: '',        leisure: '', addrStreet: 'Lee Hwy' },
    { poiId: 2,  name: 'Mele Bistro',           lat: 38.8950, lon: -77.0680, amenity: 'restaurant', tourism: '', shop: '',        leisure: '', addrStreet: 'Wilson Blvd' },
    { poiId: 3,  name: 'Wells Fargo',           lat: 38.8910, lon: -77.0750, amenity: 'bank',       tourism: '', shop: '',        leisure: '', addrStreet: 'Clarendon Blvd' },
    { poiId: 4,  name: 'Rosslyn Metro Station', lat: 38.8963, lon: -77.0708, amenity: 'subway',     tourism: '', shop: '',        leisure: '', addrStreet: 'N Fort Myer Dr' },
    { poiId: 5,  name: 'Whole Foods Market',    lat: 38.8920, lon: -77.0660, amenity: '',           tourism: '', shop: 'grocery', leisure: '', addrStreet: 'Clarendon Blvd' },
    { poiId: 6,  name: 'Arlington Central Library', lat: 38.8856, lon: -77.0836, amenity: 'library', tourism: '', shop: '',   leisure: '', addrStreet: 'N Quincy St' },
    { poiId: 7,  name: 'Custis Trail Trailhead', lat: 38.8895, lon: -77.0785, amenity: '',          tourism: 'information', shop: '', leisure: 'park', addrStreet: '' },
    { poiId: 8,  name: 'Crystal City Marriott', lat: 38.8838, lon: -77.0600, amenity: '',           tourism: 'hotel', shop: '', leisure: '', addrStreet: 'Jefferson Davis Hwy' },
    { poiId: 9,  name: 'Pentagon City Mall',    lat: 38.8630, lon: -77.0594, amenity: '',           tourism: '', shop: 'mall', leisure: '', addrStreet: 'Army Navy Dr' },
    { poiId: 10, name: 'National Airport Metro', lat: 38.8723, lon: -77.0404, amenity: 'subway',    tourism: '', shop: '',    leisure: '', addrStreet: 'Jefferson Davis Hwy' },
  ]
  const lower = q.toLowerCase()
  const filtered = all.filter(
    p =>
      p.name.toLowerCase().includes(lower) ||
      p.amenity.toLowerCase().includes(lower) ||
      p.shop.toLowerCase().includes(lower),
  )
  return (filtered.length > 0 ? filtered : all).slice(0, limit)
}

async function mockGetNearestNode(
  lat: number,
  lon: number,
): Promise<NearestNodeResult> {
  await delay(50)
  // Load the static mapData to find the genuinely nearest node
  try {
    const res = await fetch('/mapData.json')
    const data = await res.json()
    const nodes: Record<string, { lat: number; lon: number }> = data.nodes

    let best = { nodeId: 1, lat: 38.8988, lon: -77.0737, distanceM: Infinity }
    for (const [idStr, n] of Object.entries(nodes)) {
      const d = haversineM(lat, lon, n.lat, n.lon)
      if (d < best.distanceM) {
        best = { nodeId: Number(idStr), lat: n.lat, lon: n.lon, distanceM: d }
      }
    }
    return best
  } catch {
    // fallback if file not loaded yet
    return { nodeId: 1, lat: 38.8988289, lon: -77.0737412, distanceM: 0 }
  }
}

async function mockPostRoute(req: RouteRequest): Promise<RouteResponse> {
  await delay(300)

  // Build a simple path using the static node list
  let nodes: Record<number, { lat: number; lon: number }> = {}
  try {
    const res = await fetch('/mapData.json')
    const data = await res.json()
    nodes = data.nodes
  } catch {
    /* ignore */
  }

  // Generate a fake route: straight-line interpolation between start and end
  const startNode = nodes[req.startNodeId] ?? { lat: 38.896, lon: -77.071 }
  const endNode   = nodes[req.endNodeId]   ?? { lat: 38.885, lon: -77.060 }

  const mockResults = req.algorithms.map(algo => {
    const visitedCount = algo === 'dijkstra' ? 480 : algo === 'astar' ? 200 : 155
    const durationMs   = algo === 'dijkstra' ?  45 : algo === 'astar' ?  18 :  12

    // Interpolated "path" — 12 waypoints
    const path = interpolatePath(startNode, endNode, 12)

    // Simulated visited nodes — a wider scatter around the path
    const visitedOrder = generateFakeVisited(startNode, endNode, visitedCount, algo)

    const distanceM = haversineM(startNode.lat, startNode.lon, endNode.lat, endNode.lon)

    return { algorithm: algo, path, visitedOrder, distanceM, durationMs, visitedCount }
  })

  return { results: mockResults }
}

// ─────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────

function delay(ms: number) {
  return new Promise(r => setTimeout(r, ms))
}

function haversineM(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6_371_000
  const dLat = ((lat2 - lat1) * Math.PI) / 180
  const dLon = ((lon2 - lon1) * Math.PI) / 180
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) ** 2
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

function interpolatePath(
  start: { lat: number; lon: number },
  end: { lat: number; lon: number },
  steps: number,
) {
  return Array.from({ length: steps }, (_, i) => {
    const t = i / (steps - 1)
    // add slight sinusoidal deviation to look road-like
    const deviation = Math.sin(t * Math.PI) * 0.0015
    return {
      nodeId: i,
      lat: start.lat + (end.lat - start.lat) * t + deviation,
      lon: start.lon + (end.lon - start.lon) * t,
    }
  })
}

function generateFakeVisited(
  start: { lat: number; lon: number },
  end:   { lat: number; lon: number },
  count: number,
  algo:  string,
) {
  const nodes = []
  // Dijkstra spreads wide; A* hugs the straight line; BiBFS fans from both ends
  const spread = algo === 'dijkstra' ? 0.012 : algo === 'astar' ? 0.004 : 0.008
  for (let i = 0; i < count; i++) {
    const t = algo === 'bibfs'
      ? i < count / 2 ? i / count : 1 - i / count   // meet in the middle
      : i / count
    const lat = start.lat + (end.lat - start.lat) * t + (Math.random() - 0.5) * spread
    const lon = start.lon + (end.lon - start.lon) * t + (Math.random() - 0.5) * spread
    nodes.push({ nodeId: 10000 + i, lat, lon })
  }
  return nodes
}
