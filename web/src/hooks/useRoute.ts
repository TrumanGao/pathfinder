import { useState, useCallback } from 'react'
import { postRoute } from '../api/pathfinderApi'
import type { AlgorithmResult, AlgorithmType, Waypoint } from '../types'

interface UseRouteResult {
  results: AlgorithmResult[]
  loading: boolean
  error: string | null
  fetchRoute: (start: Waypoint, end: Waypoint, algorithms: AlgorithmType[]) => void
  clearRoute: () => void
}

export function useRoute(): UseRouteResult {
  const [results, setResults] = useState<AlgorithmResult[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fetchRoute = useCallback(
    (start: Waypoint, end: Waypoint, algorithms: AlgorithmType[]) => {
      if (!algorithms.length) return
      setLoading(true)
      setError(null)
      setResults([])
      postRoute({ startNodeId: start.nodeId, endNodeId: end.nodeId, algorithms })
        .then(res => setResults(res.results))
        .catch(e => setError(String(e)))
        .finally(() => setLoading(false))
    },
    [],
  )

  const clearRoute = useCallback(() => {
    setResults([])
    setError(null)
  }, [])

  return { results, loading, error, fetchRoute, clearRoute }
}
