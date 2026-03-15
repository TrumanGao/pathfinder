/**
 * Layer 2 — Waypoints, final path, POI markers, and click interaction
 *
 * This is the topmost canvas layer. It:
 *  - Handles map clicks → calls onMapClick(lat, lon)
 *  - Renders start (green) and end (red) waypoint markers
 *  - Renders the final shortest paths (one per algorithm, thicker lines)
 *  - Renders POI search result pins
 */
import { useEffect, useRef } from 'react'
import type { AlgorithmResult, POI, Waypoint } from '../../types'
import type { CoordinateTransformer } from '../../utils/CoordinateTransformer'
import { ALGORITHM_STYLES } from '../../utils/CoordinateTransformer'

interface Props {
  width: number
  height: number
  transformer: CoordinateTransformer
  startWaypoint: Waypoint | null
  endWaypoint: Waypoint | null
  results: AlgorithmResult[]
  pois: POI[]
  onMapClick: (lat: number, lon: number) => void
}

const MARKER_RADIUS = 10

export function InteractionLayer({
  width,
  height,
  transformer,
  startWaypoint,
  endWaypoint,
  results,
  pois,
  onMapClick,
}: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  // Click handler — translate canvas pixel to lat/lon
  function handleClick(e: React.MouseEvent<HTMLCanvasElement>) {
    const rect = canvasRef.current!.getBoundingClientRect()
    const x = e.clientX - rect.left
    const y = e.clientY - rect.top
    const { lat, lon } = transformer.xyToLatLon(x, y)
    onMapClick(lat, lon)
  }

  // Redraw whenever any dependency changes
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')!
    ctx.clearRect(0, 0, width, height)

    // ── Final paths ──────────────────────────────────────────────────
    for (const result of results) {
      if (!result.path.length) continue
      const style = ALGORITHM_STYLES[result.algorithm] ?? ALGORITHM_STYLES['dijkstra']

      ctx.beginPath()
      ctx.strokeStyle = style.pathColor
      ctx.lineWidth = 3
      ctx.lineCap = 'round'
      ctx.lineJoin = 'round'
      ctx.shadowColor = style.pathColor
      ctx.shadowBlur = 6

      result.path.forEach((node, i) => {
        const { x, y } = transformer.latLonToXY(node.lat, node.lon)
        if (i === 0) ctx.moveTo(x, y)
        else ctx.lineTo(x, y)
      })
      ctx.stroke()
      ctx.shadowBlur = 0
    }

    // ── POI pins ─────────────────────────────────────────────────────
    for (const poi of pois) {
      const { x, y } = transformer.latLonToXY(poi.lat, poi.lon)
      ctx.beginPath()
      ctx.arc(x, y, 5, 0, Math.PI * 2)
      ctx.fillStyle = 'rgba(250,204,21,0.85)'
      ctx.fill()
      ctx.strokeStyle = '#92400e'
      ctx.lineWidth = 1
      ctx.stroke()
    }

    // ── Waypoint markers ─────────────────────────────────────────────
    if (startWaypoint) drawMarker(ctx, transformer, startWaypoint, '#22c55e', 'S')
    if (endWaypoint)   drawMarker(ctx, transformer, endWaypoint,   '#ef4444', 'E')
  }, [width, height, transformer, startWaypoint, endWaypoint, results, pois])

  return (
    <canvas
      ref={canvasRef}
      width={width}
      height={height}
      style={{ position: 'absolute', top: 0, left: 0, cursor: 'crosshair' }}
      onClick={handleClick}
    />
  )
}

function drawMarker(
  ctx: CanvasRenderingContext2D,
  transformer: CoordinateTransformer,
  waypoint: Waypoint,
  color: string,
  letter: string,
) {
  const { x, y } = transformer.latLonToXY(waypoint.lat, waypoint.lon)

  // Outer circle
  ctx.beginPath()
  ctx.arc(x, y, MARKER_RADIUS, 0, Math.PI * 2)
  ctx.fillStyle = color
  ctx.fill()
  ctx.strokeStyle = '#fff'
  ctx.lineWidth = 2
  ctx.stroke()

  // Drop shadow pin effect
  ctx.beginPath()
  ctx.moveTo(x, y + MARKER_RADIUS)
  ctx.lineTo(x - 5, y + MARKER_RADIUS + 8)
  ctx.lineTo(x + 5, y + MARKER_RADIUS + 8)
  ctx.closePath()
  ctx.fillStyle = color
  ctx.fill()

  // Letter label
  ctx.font = `bold ${MARKER_RADIUS}px sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillStyle = '#fff'
  ctx.fillText(letter, x, y)
}
