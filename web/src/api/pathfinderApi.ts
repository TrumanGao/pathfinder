import type {
  MetadataResponse,
  NearestResponse,
  RouteRequest,
  RouteResponse,
  SearchResponse,
} from '../types'

/** Thin typed API client for the backend. */
async function apiFetch<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  if (!response.ok) {
    const message = await response.text().catch(() => '')
    throw new Error(message || `Request failed: ${response.status}`)
  }
  return response.json() as Promise<T>
}

export async function getMetadata(): Promise<MetadataResponse> {
  return apiFetch<MetadataResponse>('/api/metadata')
}

export async function searchLocations(params: {
  q: string
  types?: string[]
  limit?: number
}): Promise<SearchResponse> {
  const searchParams = new URLSearchParams()
  searchParams.set('q', params.q)

  for (const type of params.types ?? []) {
    if (type.trim()) {
      searchParams.append('types', type)
    }
  }

  if (typeof params.limit === 'number') {
    searchParams.set('limit', String(params.limit))
  }

  return apiFetch<SearchResponse>(`/api/search?${searchParams.toString()}`)
}

export async function getNearest(params: {
  lat: number
  lon: number
}): Promise<NearestResponse> {
  const searchParams = new URLSearchParams({
    lat: String(params.lat),
    lon: String(params.lon),
  })

  return apiFetch<NearestResponse>(`/api/nearest?${searchParams.toString()}`)
}

export async function getRoute(request: RouteRequest): Promise<RouteResponse> {
  return apiFetch<RouteResponse>('/api/route', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
}
