import type { MapBounds } from '../types'

/**
 * Maps geographic coordinates (lat/lon) to Canvas pixel coordinates (x, y)
 * and vice-versa.
 *
 * Uses a Mercator-style linear projection within the given bounds, with
 * uniform padding on all sides so the map doesn't touch canvas edges.
 *
 * Longitude → X  (left = minLon, right = maxLon)
 * Latitude  → Y  (top  = maxLat, bottom = minLat)  ← Y axis is inverted
 */
export class CoordinateTransformer {
  private readonly bounds: MapBounds

  /** effective drawing area after padding */
  private readonly drawW: number
  private readonly drawH: number
  private readonly offsetX: number
  private readonly offsetY: number

  /** degree spans */
  private readonly lonSpan: number
  private readonly latSpan: number

  constructor(
    bounds: MapBounds,
    canvasW: number,
    canvasH: number,
    paddingFraction = 0.04,
  ) {
    this.bounds = bounds

    const padX = canvasW * paddingFraction
    const padY = canvasH * paddingFraction

    this.offsetX = padX
    this.offsetY = padY
    this.drawW = canvasW - padX * 2
    this.drawH = canvasH - padY * 2

    this.lonSpan = bounds.maxLon - bounds.minLon
    this.latSpan = bounds.maxLat - bounds.minLat
  }

  /** Geographic → Canvas pixel */
  latLonToXY(lat: number, lon: number): { x: number; y: number } {
    const x =
      this.offsetX + ((lon - this.bounds.minLon) / this.lonSpan) * this.drawW
    // Latitude increases upward, but canvas Y increases downward
    const y =
      this.offsetY +
      ((this.bounds.maxLat - lat) / this.latSpan) * this.drawH

    return { x, y }
  }

  /** Canvas pixel → Geographic */
  xyToLatLon(x: number, y: number): { lat: number; lon: number } {
    const lon =
      this.bounds.minLon + ((x - this.offsetX) / this.drawW) * this.lonSpan
    const lat =
      this.bounds.maxLat - ((y - this.offsetY) / this.drawH) * this.latSpan

    return { lat, lon }
  }

  /**
   * Returns the pixel scale factor (pixels per degree of longitude).
   * Useful for deciding stroke widths that feel consistent at any resolution.
   */
  get pixelsPerDegree(): number {
    return this.drawW / this.lonSpan
  }
}

// ─────────────────────────────────────────────
// Highway rendering styles
// ─────────────────────────────────────────────

export interface RoadStyle {
  color: string
  lineWidth: number
}

const ROAD_STYLES: Record<string, RoadStyle> = {
  motorway:      { color: '#e8a020', lineWidth: 3.5 },
  trunk:         { color: '#e8a020', lineWidth: 3.0 },
  primary:       { color: '#f7c948', lineWidth: 2.5 },
  secondary:     { color: '#c0c8d8', lineWidth: 2.0 },
  tertiary:      { color: '#a0aabb', lineWidth: 1.5 },
  residential:   { color: '#88919e', lineWidth: 1.0 },
  service:       { color: '#6b7280', lineWidth: 0.8 },
  living_street: { color: '#6b7280', lineWidth: 0.8 },
  unclassified:  { color: '#6b7280', lineWidth: 0.8 },
}

const DEFAULT_ROAD_STYLE: RoadStyle = { color: '#6b7280', lineWidth: 0.8 }

export function getRoadStyle(highway: string): RoadStyle {
  return ROAD_STYLES[highway] ?? DEFAULT_ROAD_STYLE
}

// ─────────────────────────────────────────────
// Algorithm rendering palette
// ─────────────────────────────────────────────

export interface AlgorithmStyle {
  visitedColor: string
  pathColor: string
  /** pixel offset applied when drawing visited nodes to prevent overlap */
  offsetX: number
  offsetY: number
}

export const ALGORITHM_STYLES: Record<string, AlgorithmStyle> = {
  dijkstra: {
    visitedColor: 'rgba(59,130,246,0.55)',   // blue
    pathColor:    '#2563eb',
    offsetX: 0,
    offsetY: 0,
  },
  astar: {
    visitedColor: 'rgba(249,115,22,0.55)',   // orange
    pathColor:    '#ea580c',
    offsetX: 2,
    offsetY: 0,
  },
  bibfs: {
    visitedColor: 'rgba(34,197,94,0.55)',    // light green (forward)
    pathColor:    '#16a34a',
    offsetX: 0,
    offsetY: 2,
  },
}

/** Visited-node dot radius (px) */
export const VISITED_DOT_RADIUS = 2

/** Animation: how many visited nodes to reveal per RAF frame */
export const ANIMATION_SPEED = 8   // ← adjust this to change animation pace
