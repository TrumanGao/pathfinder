/**
 * ComparePanel — side-by-side algorithm metrics
 *
 * Shows timing, distance and explored node count for each algorithm result.
 * During animation the visitedCount is approximated from the current frame.
 */
import type { AlgorithmResult } from '../../types'
import { ALGORITHM_STYLES, ANIMATION_SPEED } from '../../utils/CoordinateTransformer'

interface Props {
  results: AlgorithmResult[]
  /** Current animation frame — used to calculate live explored node count */
  animFrame: number
  loading: boolean
}

const ALGO_LABELS: Record<string, string> = {
  dijkstra: 'Dijkstra',
  astar:    'A*',
  bibfs:    'Bi-BFS',
}

export function ComparePanel({ results, animFrame, loading }: Props) {
  if (!results.length && !loading) return null

  const maxVisited  = Math.max(...results.map(r => r.visitedCount), 1)
  const maxDuration = Math.max(...results.map(r => r.durationMs), 1)
  const maxDistance = Math.max(...results.map(r => r.distanceM), 1)

  return (
    <div style={panelStyle}>
      <div style={titleStyle}>Algorithm Comparison</div>

      {loading && (
        <div style={{ color: '#64748b', fontSize: 13, padding: '8px 0' }}>
          Computing routes…
        </div>
      )}

      {results.map(result => {
        const accentColor = ALGORITHM_STYLES[result.algorithm]?.pathColor ?? '#94a3b8'
        const liveVisited = Math.min(animFrame * ANIMATION_SPEED, result.visitedCount)
        const isAnimating = liveVisited < result.visitedCount

        return (
          <div key={result.algorithm} style={cardStyle}>
            <div style={cardHeaderStyle}>
              <span
                style={{
                  display: 'inline-block',
                  width: 10,
                  height: 10,
                  borderRadius: '50%',
                  background: accentColor,
                  marginRight: 6,
                  flexShrink: 0,
                }}
              />
              <span style={{ fontWeight: 700, color: '#f1f5f9', fontSize: 14 }}>
                {ALGO_LABELS[result.algorithm] ?? result.algorithm}
              </span>
            </div>

            <MetricRow
              label="Time"
              value={`${result.durationMs} ms`}
              barFill={result.durationMs / maxDuration}
              color={accentColor}
              highlight={result.durationMs === Math.min(...results.map(r => r.durationMs))}
            />
            <MetricRow
              label="Distance"
              value={formatDistance(result.distanceM)}
              barFill={result.distanceM / maxDistance}
              color={accentColor}
            />
            <MetricRow
              label="Explored"
              value={
                isAnimating
                  ? `${liveVisited.toLocaleString()} / ${result.visitedCount.toLocaleString()}`
                  : result.visitedCount.toLocaleString()
              }
              barFill={liveVisited / maxVisited}
              color={accentColor}
              highlight={result.visitedCount === Math.min(...results.map(r => r.visitedCount))}
            />
          </div>
        )
      })}
    </div>
  )
}

// ─────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────

interface MetricRowProps {
  label: string
  value: string
  barFill: number   // 0–1
  color: string
  highlight?: boolean
}

function MetricRow({ label, value, barFill, color, highlight }: MetricRowProps) {
  return (
    <div style={{ marginTop: 8 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 3 }}>
        <span style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
          {label}
        </span>
        <span
          style={{
            fontSize: 12,
            fontWeight: 600,
            color: highlight ? color : '#cbd5e1',
          }}
        >
          {highlight && '★ '}{value}
        </span>
      </div>
      <div style={barTrackStyle}>
        <div
          style={{
            ...barFillStyle,
            width: `${Math.min(barFill * 100, 100)}%`,
            background: color,
          }}
        />
      </div>
    </div>
  )
}

function formatDistance(m: number): string {
  if (m >= 1000) return `${(m / 1000).toFixed(2)} km`
  return `${Math.round(m)} m`
}

// ─────────────────────────────────────────────
// Styles
// ─────────────────────────────────────────────

const panelStyle: React.CSSProperties = {
  background: '#0f172a',
  borderRadius: 10,
  padding: '14px 16px',
  display: 'flex',
  flexDirection: 'column',
  gap: 12,
}

const titleStyle: React.CSSProperties = {
  fontSize: 11,
  fontWeight: 700,
  textTransform: 'uppercase',
  letterSpacing: '0.08em',
  color: '#64748b',
}

const cardStyle: React.CSSProperties = {
  background: '#1e293b',
  borderRadius: 8,
  padding: '10px 12px',
}

const cardHeaderStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  marginBottom: 4,
}

const barTrackStyle: React.CSSProperties = {
  height: 4,
  background: '#334155',
  borderRadius: 2,
  overflow: 'hidden',
}

const barFillStyle: React.CSSProperties = {
  height: '100%',
  borderRadius: 2,
  transition: 'width 0.1s ease',
}
