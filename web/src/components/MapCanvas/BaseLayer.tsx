/**
 * Layer 0 — Road base map
 *
 * Loads /mapData.json, renders all road edges onto an OffscreenCanvas once,
 * then stamps the result onto this canvas. Re-renders only when canvas size changes.
 */
import { useEffect, useRef } from 'react'
import type { CoordinateTransformer } from '../../utils/CoordinateTransformer'
import { getRoadStyle } from '../../utils/CoordinateTransformer'
import type { MapData } from '../../types'

interface Props {
  width: number
  height: number
  transformer: CoordinateTransformer
}

// Module-level cache so the JSON is fetched only once per page load
let cachedMapData: MapData | null = null
let fetchPromise: Promise<MapData> | null = null

function getMapData(): Promise<MapData> {
  if (cachedMapData) return Promise.resolve(cachedMapData)
  if (!fetchPromise) {
    fetchPromise = fetch('/mapData.json')
      .then(r => r.json())
      .then(d => {
        cachedMapData = d as MapData
        return cachedMapData
      })
  }
  return fetchPromise
}

export function BaseLayer({ width, height, transformer }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    if (!canvasRef.current || width === 0 || height === 0) return
    const canvas = canvasRef.current
    const ctx = canvas.getContext('2d')!

    // Render off-screen, then blit — avoids visible incremental drawing
    const offscreen = new OffscreenCanvas(width, height)
    const offCtx = offscreen.getContext('2d')!

    offCtx.clearRect(0, 0, width, height)

    // Dark map background
    offCtx.fillStyle = '#1a1f2e'
    offCtx.fillRect(0, 0, width, height)

    getMapData().then(data => {
      // Group edges by highway type so we can batch-draw in a single path per style
      const groups = new Map<string, typeof data.edges>()
      for (const edge of data.edges) {
        const hw = edge.highway || 'unclassified'
        if (!groups.has(hw)) groups.set(hw, [])
        groups.get(hw)!.push(edge)
      }

      // Draw from least prominent to most prominent (painter's algorithm)
      const order = [
        'service', 'living_street', 'unclassified',
        'residential', 'tertiary', 'secondary',
        'primary', 'trunk', 'motorway',
      ]

      const drawGroup = (hw: string, edges: typeof data.edges) => {
        const style = getRoadStyle(hw)
        offCtx.beginPath()
        offCtx.strokeStyle = style.color
        offCtx.lineWidth = style.lineWidth
        offCtx.lineCap = 'round'
        offCtx.lineJoin = 'round'
        for (const e of edges) {
          const { x: x1, y: y1 } = transformer.latLonToXY(e.uLat, e.uLon)
          const { x: x2, y: y2 } = transformer.latLonToXY(e.vLat, e.vLon)
          offCtx.moveTo(x1, y1)
          offCtx.lineTo(x2, y2)
        }
        offCtx.stroke()
      }

      // Draw ordered groups first
      for (const hw of order) {
        const edges = groups.get(hw)
        if (edges) drawGroup(hw, edges)
      }
      // Draw any remaining highway types not in the explicit order
      for (const [hw, edges] of groups) {
        if (!order.includes(hw)) drawGroup(hw, edges)
      }

      // Blit offscreen → visible canvas
      ctx.clearRect(0, 0, width, height)
      ctx.drawImage(offscreen, 0, 0)
    })
  }, [width, height, transformer])

  return (
    <canvas
      ref={canvasRef}
      width={width}
      height={height}
      style={{ position: 'absolute', top: 0, left: 0 }}
    />
  )
}
