import { useState, useCallback } from 'react'
import { MapCanvas } from './components/MapCanvas/MapCanvas'
import { AlgorithmSelector } from './components/AlgorithmSelector/AlgorithmSelector'
import { SearchBar } from './components/SearchBar/SearchBar'
import { ComparePanel } from './components/ComparePanel/ComparePanel'
import { useRoute } from './hooks/useRoute'
import { getNearestNode } from './api/pathfinderApi'
import type { AlgorithmType, POI, Waypoint } from './types'
import './App.css'

type ClickMode = 'start' | 'end'

export default function App() {
  const [selectedAlgos, setSelectedAlgos] = useState<AlgorithmType[]>([
    'dijkstra', 'astar', 'bibfs',
  ])
  const [startWaypoint, setStartWaypoint] = useState<Waypoint | null>(null)
  const [endWaypoint, setEndWaypoint]     = useState<Waypoint | null>(null)
  const [clickMode, setClickMode]         = useState<ClickMode>('start')
  const [pois, setPois]                   = useState<POI[]>([])
  const [animFrame, setAnimFrame]         = useState(0)
  const [animDone, setAnimDone]           = useState(false)

  const { results, loading: routeLoading, fetchRoute, clearRoute } = useRoute()

  // ── Map click → find nearest node → set waypoint ────────────────
  const handleMapClick = useCallback(
    async (lat: number, lon: number) => {
      try {
        const node = await getNearestNode(lat, lon)
        const wp: Waypoint = {
          nodeId: node.nodeId,
          lat: node.lat,
          lon: node.lon,
          label: `Node ${node.nodeId}`,
        }
        if (clickMode === 'start') {
          setStartWaypoint(wp)
          setClickMode('end')
        } else {
          setEndWaypoint(wp)
          setClickMode('start')
        }
      } catch (e) {
        console.error('getNearestNode failed:', e)
      }
    },
    [clickMode],
  )

  // ── POI search result → set waypoint ───────────────────────────
  function handlePOISelect(poi: POI) {
    const wp: Waypoint = {
      nodeId: -poi.poiId,        // negative id flags it as a POI (resolved server-side)
      lat: poi.lat,
      lon: poi.lon,
      label: poi.name,
    }
    // Use POI for the next expected waypoint role
    if (!startWaypoint) setStartWaypoint(wp)
    else setEndWaypoint(wp)
    setPois(prev => [...prev.filter(p => p.poiId !== poi.poiId), poi])
  }

  // ── Find route ──────────────────────────────────────────────────
  function handleFindRoute() {
    if (!startWaypoint || !endWaypoint) return
    setAnimFrame(0)
    setAnimDone(false)
    clearRoute()
    fetchRoute(startWaypoint, endWaypoint, selectedAlgos)
  }

  // ── Clear all ───────────────────────────────────────────────────
  function handleClear() {
    setStartWaypoint(null)
    setEndWaypoint(null)
    setPois([])
    setAnimFrame(0)
    setAnimDone(false)
    clearRoute()
    setClickMode('start')
  }

  const canFind = !!startWaypoint && !!endWaypoint && selectedAlgos.length > 0

  return (
    <div style={rootStyle}>
      {/* ── Sidebar ─────────────────────────────────────────── */}
      <aside style={sidebarStyle}>
        {/* Header */}
        <div style={headerStyle}>
          <div style={logoStyle}>⬡</div>
          <div>
            <div style={{ fontWeight: 800, fontSize: 16, color: '#f1f5f9' }}>Pathfinder</div>
            <div style={{ fontSize: 11, color: '#64748b' }}>Algorithm Visualizer</div>
          </div>
        </div>

        {/* Waypoint search */}
        <section style={sectionStyle}>
          <div style={labelStyle}>Start Point</div>
          <SearchBar
            placeholder="Search start location…"
            onSelect={poi => {
              const wp: Waypoint = { nodeId: -poi.poiId, lat: poi.lat, lon: poi.lon, label: poi.name }
              setStartWaypoint(wp)
              setPois(prev => [...prev.filter(p => p.poiId !== poi.poiId), poi])
            }}
          />
          {startWaypoint && (
            <div style={waypointTagStyle('#22c55e')}>
              S — {startWaypoint.label}
            </div>
          )}
        </section>

        <section style={sectionStyle}>
          <div style={labelStyle}>End Point</div>
          <SearchBar
            placeholder="Search end location…"
            onSelect={handlePOISelect}
          />
          {endWaypoint && (
            <div style={waypointTagStyle('#ef4444')}>
              E — {endWaypoint.label}
            </div>
          )}
        </section>

        {/* Click mode hint */}
        <div style={hintStyle}>
          Or click the map to set{' '}
          <span style={{ color: clickMode === 'start' ? '#22c55e' : '#ef4444', fontWeight: 600 }}>
            {clickMode === 'start' ? 'Start' : 'End'}
          </span>
        </div>

        <hr style={dividerStyle} />

        {/* Algorithm selector */}
        <AlgorithmSelector
          selected={selectedAlgos}
          onChange={setSelectedAlgos}
          disabled={routeLoading}
        />

        <hr style={dividerStyle} />

        {/* Actions */}
        <button
          onClick={handleFindRoute}
          disabled={!canFind || routeLoading}
          style={primaryBtnStyle(!canFind || routeLoading)}
        >
          {routeLoading ? 'Computing…' : 'Find Route'}
        </button>
        <button onClick={handleClear} style={secondaryBtnStyle}>
          Clear
        </button>

        {/* Spacer */}
        <div style={{ flex: 1 }} />

        {/* Compare panel */}
        <ComparePanel
          results={results}
          animFrame={animFrame}
          loading={routeLoading}
        />

        {animDone && results.length > 0 && (
          <div style={doneStyle}>Animation complete</div>
        )}
      </aside>

      {/* ── Map area ────────────────────────────────────────── */}
      <main style={mapAreaStyle}>
        <MapCanvas
          results={results}
          startWaypoint={startWaypoint}
          endWaypoint={endWaypoint}
          pois={pois}
          onMapClick={handleMapClick}
          onFrameUpdate={setAnimFrame}
          onAnimationEnd={() => setAnimDone(true)}
        />
      </main>
    </div>
  )
}

// ─────────────────────────────────────────────
// Styles
// ─────────────────────────────────────────────

const rootStyle: React.CSSProperties = {
  display: 'flex',
  height: '100vh',
  width: '100vw',
  background: '#0f172a',
  color: '#e2e8f0',
  fontFamily: 'system-ui, -apple-system, sans-serif',
  overflow: 'hidden',
}

const sidebarStyle: React.CSSProperties = {
  width: 280,
  flexShrink: 0,
  background: '#0f172a',
  borderRight: '1px solid #1e293b',
  display: 'flex',
  flexDirection: 'column',
  gap: 10,
  padding: '16px 14px',
  overflowY: 'auto',
}

const mapAreaStyle: React.CSSProperties = {
  flex: 1,
  position: 'relative',
}

const headerStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  marginBottom: 6,
}

const logoStyle: React.CSSProperties = {
  fontSize: 28,
  color: '#3b82f6',
  lineHeight: 1,
}

const sectionStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 5,
}

const labelStyle: React.CSSProperties = {
  fontSize: 11,
  fontWeight: 700,
  textTransform: 'uppercase',
  letterSpacing: '0.08em',
  color: '#64748b',
}

const hintStyle: React.CSSProperties = {
  fontSize: 12,
  color: '#475569',
  textAlign: 'center',
}

const dividerStyle: React.CSSProperties = {
  border: 'none',
  borderTop: '1px solid #1e293b',
  margin: '2px 0',
}

const doneStyle: React.CSSProperties = {
  fontSize: 12,
  color: '#22c55e',
  textAlign: 'center',
  padding: '4px 0',
}

function waypointTagStyle(color: string): React.CSSProperties {
  return {
    fontSize: 12,
    color,
    background: `${color}18`,
    borderRadius: 5,
    padding: '3px 8px',
    border: `1px solid ${color}44`,
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  }
}

function primaryBtnStyle(disabled: boolean): React.CSSProperties {
  return {
    padding: '10px 0',
    borderRadius: 8,
    border: 'none',
    background: disabled ? '#1e293b' : '#3b82f6',
    color: disabled ? '#475569' : '#fff',
    fontWeight: 700,
    fontSize: 14,
    cursor: disabled ? 'not-allowed' : 'pointer',
    transition: 'background 0.15s',
    width: '100%',
  }
}

const secondaryBtnStyle: React.CSSProperties = {
  padding: '8px 0',
  borderRadius: 8,
  border: '1px solid #334155',
  background: 'transparent',
  color: '#94a3b8',
  fontWeight: 500,
  fontSize: 13,
  cursor: 'pointer',
  width: '100%',
}
