import type { SearchResult } from '../types'

interface SearchResultListProps {
  results: SearchResult[]
  selectedResultId: number | null
  onSelectResult: (result: SearchResult) => void
  onSetStart: (result: SearchResult) => void
  onSetEnd: (result: SearchResult) => void
}

export function SearchResultList({
  results,
  selectedResultId,
  onSelectResult,
  onSetStart,
  onSetEnd,
}: SearchResultListProps) {
  if (!results.length) {
    return <div className="panel-message">No results yet.</div>
  }

  return (
    <div className="search-results">
      {results.map(result => (
        <div
          key={result.id}
          className={`search-result ${selectedResultId === result.id ? 'search-result--active' : ''}`}
          onClick={() => onSelectResult(result)}
        >
          <div className="search-result__heading">
            <div>
              <div className="search-result__title">{result.displayName}</div>
              <div className="search-result__subtitle">
                {result.type} / {result.subType}
              </div>
            </div>
            {result.routable && <span className="pill">Routable</span>}
          </div>
          <div className="search-result__actions">
            <button
              type="button"
              className="small-button"
              onClick={event => {
                event.stopPropagation()
                onSetStart(result)
              }}
            >
              Set Start
            </button>
            <button
              type="button"
              className="small-button"
              onClick={event => {
                event.stopPropagation()
                onSetEnd(result)
              }}
            >
              Set End
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}
