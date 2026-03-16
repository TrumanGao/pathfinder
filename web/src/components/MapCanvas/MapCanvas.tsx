/**
 * MapCanvas — three-layer stacked canvas container
 *
 * Layer 0  BaseLayer        road base map (offline rendered once)
 * Layer 1  AnimationLayer   exploration animation (visitedOrder dots)
 * Layer 2  InteractionLayer waypoints, final paths, POI pins, click handling
 */
import { useRef, useState, useEffect, useCallback } from 'react'
import { BaseLayer } from './BaseLayer'
import { AnimationLayer, type AnimationLayerHandle } from './AnimationLayer'
import { InteractionLayer } from './InteractionLayer'
import { useMapInfo } from '../../hooks/useMapInfo'
import type { AlgorithmResult, POI, Waypoint } from '../../types'

interface Props {
  results: AlgorithmResult[]
  startWaypoint: Waypoint | null
  endWaypoint: Waypoint | null
  pois: POI[]
  onMapClick: (lat: number, lon: number) => void
  onFrameUpdate?: (frame: number) => void
  onAnimationEnd?: () => void
}

export function MapCanvas({
  results,
  startWaypoint,
  endWaypoint,
  pois,
  onMapClick,
  onFrameUpdate,
  onAnimationEnd,
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const animLayerRef = useRef<AnimationLayerHandle>(null)
  const [size, setSize] = useState({ w: 0, h: 0 })

  // Measure container and track resizes
  useEffect(() => {
    const el = containerRef.current
    if (!el) return
    const measure = () =>
      setSize({ w: el.clientWidth, h: el.clientHeight })
    measure()
    const ro = new ResizeObserver(measure)
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const { transformer, loading, error } = useMapInfo(size.w, size.h)

  // Reset animation when new results arrive
  useEffect(() => {
    animLayerRef.current?.reset()
  }, [results])

  const handleAnimationEnd = useCallback(() => {
    onAnimationEnd?.()
  }, [onAnimationEnd])

  return (
    <div
      ref={containerRef}
      style={{ position: 'relative', width: '100%', height: '100%', overflow: 'hidden' }}
    >
      {loading && (
        <div style={overlayStyle}>Loading map…</div>
      )}
      {error && (
        <div style={{ ...overlayStyle, color: '#f87171' }}>Error: {error}</div>
      )}

      {transformer && (
        <>
          <BaseLayer width={size.w} height={size.h} transformer={transformer} />
          <AnimationLayer
            ref={animLayerRef}
            width={size.w}
            height={size.h}
            transformer={transformer}
            results={results}
            onFrameUpdate={onFrameUpdate}
            onAnimationEnd={handleAnimationEnd}
          />
          <InteractionLayer
            width={size.w}
            height={size.h}
            transformer={transformer}
            startWaypoint={startWaypoint}
            endWaypoint={endWaypoint}
            results={results}
            pois={pois}
            onMapClick={onMapClick}
          />
        </>
      )}
    </div>
  )
}

const overlayStyle: React.CSSProperties = {
  position: 'absolute',
  inset: 0,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  color: '#94a3b8',
  fontSize: 14,
  zIndex: 10,
  background: '#1a1f2e',
}
