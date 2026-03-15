import { useEffect, useState } from 'react'
import { getMapInfo } from '../api/pathfinderApi'
import { CoordinateTransformer } from '../utils/CoordinateTransformer'
import type { MapBounds } from '../types'

interface UseMapInfoResult {
  bounds: MapBounds | null
  transformer: CoordinateTransformer | null
  loading: boolean
  error: string | null
}

export function useMapInfo(canvasW: number, canvasH: number): UseMapInfoResult {
  const [bounds, setBounds] = useState<MapBounds | null>(null)
  const [transformer, setTransformer] = useState<CoordinateTransformer | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (canvasW === 0 || canvasH === 0) return
    setLoading(true)
    getMapInfo()
      .then(info => {
        setBounds(info.bounds)
        setTransformer(new CoordinateTransformer(info.bounds, canvasW, canvasH))
      })
      .catch(e => setError(String(e)))
      .finally(() => setLoading(false))
  }, [canvasW, canvasH])

  return { bounds, transformer, loading, error }
}
