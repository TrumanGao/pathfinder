import type {
  Annotation,
  AnnotationCategory,
  AnnotationListResponse,
  MetadataResponse,
  NearestResponse,
  RouteRequest,
  RouteResponse,
  SearchResponse,
} from '../types'

/**
 * EN: Thin typed API client for the current backend contracts.
 * 中文：面向当前后端契约的轻量类型化 API 客户端。
 */
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

export async function searchNearby(params: {
  lat: number
  lon: number
  types?: string[]
  tags?: string[]
  radius?: number
  limit?: number
}): Promise<SearchResponse> {
  const searchParams = new URLSearchParams({
    lat: String(params.lat),
    lon: String(params.lon),
  })

  for (const type of params.types ?? []) {
    if (type.trim()) {
      searchParams.append('types', type)
    }
  }

  for (const tag of params.tags ?? []) {
    if (tag.trim()) {
      searchParams.append('tags', tag)
    }
  }

  if (typeof params.radius === 'number') {
    searchParams.set('radius', String(params.radius))
  }

  if (typeof params.limit === 'number') {
    searchParams.set('limit', String(params.limit))
  }

  return apiFetch<SearchResponse>(`/api/search/nearby?${searchParams.toString()}`)
}

export async function getAnnotations(params: {
  lat: number
  lon: number
  radius?: number
}): Promise<AnnotationListResponse> {
  const searchParams = new URLSearchParams({
    lat: String(params.lat),
    lon: String(params.lon),
  })
  if (typeof params.radius === 'number') {
    searchParams.set('radius', String(params.radius))
  }
  return apiFetch<AnnotationListResponse>(`/api/annotations?${searchParams.toString()}`)
}

export async function createAnnotation(params: {
  lat: number
  lon: number
  category: AnnotationCategory
  text: string
  author?: string
}): Promise<Annotation> {
  return apiFetch<Annotation>('/api/annotations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  })
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
