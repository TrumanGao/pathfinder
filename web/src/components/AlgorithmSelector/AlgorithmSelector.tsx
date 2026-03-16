import type { AlgorithmType } from '../../types'
import { ALGORITHM_STYLES } from '../../utils/CoordinateTransformer'

interface Props {
  selected: AlgorithmType[]
  onChange: (selected: AlgorithmType[]) => void
  disabled?: boolean
}

const ALGORITHMS: { id: AlgorithmType; label: string; description: string }[] = [
  { id: 'dijkstra', label: 'Dijkstra',   description: 'Optimal, explores widely' },
  { id: 'astar',    label: 'A*',          description: 'Heuristic, faster in practice' },
  { id: 'bibfs',    label: 'Bi-BFS',     description: 'Bidirectional, meets in the middle' },
]

export function AlgorithmSelector({ selected, onChange, disabled }: Props) {
  function toggle(id: AlgorithmType) {
    if (selected.includes(id)) {
      // Keep at least one selected
      if (selected.length === 1) return
      onChange(selected.filter(a => a !== id))
    } else {
      onChange([...selected, id])
    }
  }

  return (
    <div style={containerStyle}>
      <div style={titleStyle}>Algorithms</div>
      <div style={listStyle}>
        {ALGORITHMS.map(algo => {
          const active = selected.includes(algo.id)
          const accentColor = ALGORITHM_STYLES[algo.id].pathColor
          return (
            <button
              key={algo.id}
              onClick={() => toggle(algo.id)}
              disabled={disabled}
              style={{
                ...btnStyle,
                borderColor: active ? accentColor : '#334155',
                background: active ? `${accentColor}22` : '#1e293b',
                color: active ? '#f1f5f9' : '#64748b',
                cursor: disabled ? 'not-allowed' : 'pointer',
              }}
            >
              <span
                style={{
                  display: 'inline-block',
                  width: 10,
                  height: 10,
                  borderRadius: '50%',
                  background: active ? accentColor : '#334155',
                  marginRight: 8,
                  flexShrink: 0,
                }}
              />
              <span>
                <span style={{ fontWeight: 600, fontSize: 13 }}>{algo.label}</span>
                <br />
                <span style={{ fontSize: 11, opacity: 0.7 }}>{algo.description}</span>
              </span>
            </button>
          )
        })}
      </div>
    </div>
  )
}

const containerStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
}

const titleStyle: React.CSSProperties = {
  fontSize: 11,
  fontWeight: 700,
  textTransform: 'uppercase',
  letterSpacing: '0.08em',
  color: '#64748b',
  marginBottom: 2,
}

const listStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
}

const btnStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  padding: '8px 10px',
  borderRadius: 8,
  border: '1px solid',
  textAlign: 'left',
  transition: 'all 0.15s ease',
  width: '100%',
}
