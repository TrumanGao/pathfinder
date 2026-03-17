import { useState, useRef, useEffect, useCallback } from 'react'
import { searchPOI } from '../../api/pathfinderApi'
import type { POI } from '../../types'

interface Props {
  placeholder: string
  onSelect: (poi: POI) => void
}

/** Debounce delay in ms */
const DEBOUNCE_MS = 300

export function SearchBar({ placeholder, onSelect }: Props) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<POI[]>([])
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  const search = useCallback((q: string) => {
    if (!q.trim()) { setResults([]); setOpen(false); return }
    setLoading(true)
    searchPOI(q)
      .then(res => { setResults(res); setOpen(res.length > 0) })
      .finally(() => setLoading(false))
  }, [])

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const v = e.target.value
    setQuery(v)
    if (timerRef.current) clearTimeout(timerRef.current)
    timerRef.current = setTimeout(() => search(v), DEBOUNCE_MS)
  }

  function handleSelect(poi: POI) {
    setQuery(poi.name)
    setOpen(false)
    onSelect(poi)
  }

  // Close dropdown on outside click
  useEffect(() => {
    function onOutside(e: MouseEvent) {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onOutside)
    return () => document.removeEventListener('mousedown', onOutside)
  }, [])

  return (
    <div ref={containerRef} style={{ position: 'relative', width: '100%' }}>
      <div style={inputWrapStyle}>
        <svg style={iconStyle} viewBox="0 0 20 20" fill="currentColor">
          <path
            fillRule="evenodd"
            d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z"
            clipRule="evenodd"
          />
        </svg>
        <input
          value={query}
          onChange={handleChange}
          onFocus={() => results.length > 0 && setOpen(true)}
          placeholder={placeholder}
          style={inputStyle}
        />
        {loading && <span style={{ color: '#64748b', fontSize: 12, marginRight: 8 }}>…</span>}
      </div>

      {open && (
        <ul style={dropdownStyle}>
          {results.map(poi => (
            <li
              key={poi.poiId}
              onMouseDown={() => handleSelect(poi)}
              style={dropdownItemStyle}
              onMouseEnter={e => {
                ;(e.currentTarget as HTMLLIElement).style.background = '#334155'
              }}
              onMouseLeave={e => {
                ;(e.currentTarget as HTMLLIElement).style.background = 'transparent'
              }}
            >
              <div style={{ fontWeight: 500, fontSize: 13, color: '#e2e8f0' }}>
                {poi.name}
              </div>
              <div style={{ fontSize: 11, color: '#64748b', marginTop: 1 }}>
                {[poi.amenity, poi.shop, poi.leisure, poi.addrStreet]
                  .filter(Boolean)
                  .join(' · ')}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

const inputWrapStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  background: '#1e293b',
  border: '1px solid #334155',
  borderRadius: 8,
  overflow: 'hidden',
}

const iconStyle: React.CSSProperties = {
  width: 16,
  height: 16,
  color: '#64748b',
  flexShrink: 0,
  marginLeft: 10,
}

const inputStyle: React.CSSProperties = {
  flex: 1,
  background: 'transparent',
  border: 'none',
  outline: 'none',
  color: '#e2e8f0',
  fontSize: 13,
  padding: '9px 10px',
}

const dropdownStyle: React.CSSProperties = {
  position: 'absolute',
  top: '100%',
  left: 0,
  right: 0,
  zIndex: 100,
  background: '#1e293b',
  border: '1px solid #334155',
  borderRadius: 8,
  marginTop: 4,
  listStyle: 'none',
  padding: '4px 0',
  maxHeight: 240,
  overflowY: 'auto',
  boxShadow: '0 8px 24px rgba(0,0,0,0.5)',
}

const dropdownItemStyle: React.CSSProperties = {
  padding: '8px 12px',
  cursor: 'pointer',
  transition: 'background 0.1s',
}
