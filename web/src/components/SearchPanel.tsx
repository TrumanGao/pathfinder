import { SearchResultList } from './SearchResultList'
import type { SearchResult } from '../types'

interface SearchPanelProps {
  query: string
  onQueryChange: (value: string) => void
  selectedTypes: string[]
  onSelectedTypesChange: (types: string[]) => void
  supportedTypes: string[]
  loading: boolean
  error: string | null
  results: SearchResult[]
  selectedResultId: number | null
  onSubmit: () => void
  onSelectResult: (result: SearchResult) => void
  onSetStart: (result: SearchResult) => void
  onSetEnd: (result: SearchResult) => void
}

export function SearchPanel({
  query,
  onQueryChange,
  selectedTypes,
  onSelectedTypesChange,
  supportedTypes,
  loading,
  error,
  results,
  selectedResultId,
  onSubmit,
  onSelectResult,
  onSetStart,
  onSetEnd,
}: SearchPanelProps) {
  function toggleType(type: string) {
    if (selectedTypes.includes(type)) {
      onSelectedTypesChange(selectedTypes.filter(item => item !== type))
      return
    }
    onSelectedTypesChange([...selectedTypes, type])
  }

  return (
    <section className="panel">
      <div className="panel__header">
        <div>
          <h2 className="panel__title">Search</h2>
          <p className="panel__subtitle">Find places and roads from the backend search index.</p>
        </div>
      </div>

      <div className="field-group">
        <label className="field-label" htmlFor="search-query">
          Keyword
        </label>
        <div className="search-form">
          <input
            id="search-query"
            className="text-input"
            value={query}
            onChange={event => onQueryChange(event.target.value)}
            placeholder="Search a place, road, shop, or POI"
          />
          <button type="button" className="primary-button" onClick={onSubmit} disabled={loading}>
            {loading ? 'Searching…' : 'Search'}
          </button>
        </div>
      </div>

      {supportedTypes.length > 0 && (
        <details className="filter-box">
          <summary>Filter by type</summary>
          <div className="filter-grid">
            {supportedTypes.map(type => (
              <label key={type} className="checkbox-chip">
                <input
                  type="checkbox"
                  checked={selectedTypes.includes(type)}
                  onChange={() => toggleType(type)}
                />
                <span>{type}</span>
              </label>
            ))}
          </div>
        </details>
      )}

      {error && <div className="panel-message panel-message--error">{error}</div>}

      <SearchResultList
        results={results}
        selectedResultId={selectedResultId}
        onSelectResult={onSelectResult}
        onSetStart={onSetStart}
        onSetEnd={onSetEnd}
      />
    </section>
  )
}
