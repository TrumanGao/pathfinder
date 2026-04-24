import { useState } from 'react'
import type { Annotation, AnnotationCategory, PendingMapClick } from '../types'

interface AnnotationPanelProps {
  annotations: Annotation[]
  pendingMapClick: PendingMapClick | null
  loading: boolean
  error: string | null
  onCreateAnnotation: (params: { lat: number; lon: number; category: AnnotationCategory; text: string; author: string }) => void
  onRefresh: () => void
  onDelete: (id: number) => void
}

const CATEGORIES: { value: AnnotationCategory; label: string; icon: string }[] = [
  { value: 'recommendation', label: 'Recommendation', icon: '\u2B50' },
  { value: 'warning', label: 'Warning', icon: '\u26A0\uFE0F' },
  { value: 'tip', label: 'Tip', icon: '\uD83D\uDCA1' },
]

export function AnnotationPanel({ annotations, pendingMapClick, loading, error, onCreateAnnotation, onRefresh, onDelete }: AnnotationPanelProps) {
  const [category, setCategory] = useState<AnnotationCategory>('recommendation')
  const [text, setText] = useState('')
  const [author, setAuthor] = useState('')

  function handleSubmit() {
    if (!pendingMapClick || !text.trim()) return
    onCreateAnnotation({
      lat: pendingMapClick.lat,
      lon: pendingMapClick.lon,
      category,
      text: text.trim(),
      author: author.trim() || 'Anonymous',
    })
    setText('')
  }

  return (
    <section className="panel">
      <div className="panel__header">
        <div>
          <h2 className="panel__title">Community Notes</h2>
          <p className="panel__subtitle">Share tips and recommendations with fellow students.</p>
        </div>
      </div>

      {pendingMapClick && (
        <div className="annotation-form">
          <div className="annotation-form__title">Add a note at clicked location</div>

          <div className="annotation-category-row">
            {CATEGORIES.map(c => (
              <button
                key={c.value}
                type="button"
                className={`annotation-cat-btn ${category === c.value ? 'annotation-cat-btn--active' : ''}`}
                onClick={() => setCategory(c.value)}
              >
                {c.icon} {c.label}
              </button>
            ))}
          </div>

          <textarea
            className="text-input annotation-textarea"
            value={text}
            onChange={e => setText(e.target.value)}
            placeholder="What should other students know about this place?"
            maxLength={500}
            rows={3}
          />

          <input
            className="text-input"
            value={author}
            onChange={e => setAuthor(e.target.value)}
            placeholder="Your name (optional)"
          />

          <button
            type="button"
            className="primary-button"
            disabled={!text.trim() || loading}
            onClick={handleSubmit}
          >
            {loading ? 'Posting...' : 'Post Note'}
          </button>
        </div>
      )}

      {!pendingMapClick && (
        <div className="panel-message">Click the map to add a community note at that location.</div>
      )}

      {error && <div className="panel-message panel-message--error">{error}</div>}

      <button type="button" className="secondary-button" onClick={onRefresh} style={{ marginTop: 8 }}>
        Refresh Notes
      </button>

      {annotations.length > 0 && (
        <div className="annotation-list">
          {annotations.map(a => (
            <div key={a.id} className={`annotation-card annotation-card--${a.category}`}>
              <div className="annotation-card__header">
                <span>{a.category === 'recommendation' ? '\u2B50' : a.category === 'warning' ? '\u26A0\uFE0F' : '\uD83D\uDCA1'}</span>
                <span className="annotation-card__author">{a.author}</span>
                <span className="annotation-card__time">{new Date(a.createdAt).toLocaleDateString()}</span>
                <button
                  type="button"
                  className="link-button annotation-card__delete"
                  onClick={() => onDelete(a.id)}
                  aria-label="Delete note"
                  title="Delete note"
                >
                  &times;
                </button>
              </div>
              <div className="annotation-card__text">{a.text}</div>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
