import { useState } from 'react'
import ScoreBar from './ScoreBar.jsx'
import { rescore } from '../api.js'

function Review({ review, onRescored }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const fromModel = review.scoreSource === 'LLM'

  async function handleRescore() {
    setBusy(true)
    setError(null)
    try {
      onRescored(await rescore(review.id))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className={`review ${fromModel ? 'from-model' : 'from-fallback'}`}>
      <div className="review__head">
        <span className="review__author">{review.author}</span>
        <span className={`provenance ${fromModel ? 'from-model' : 'from-fallback'}`}>
          {review.scoredBy}
        </span>
        <span className="product__meta">{review.sentiment.toLowerCase()}</span>
      </div>

      <p className="review__text">{review.text}</p>

      {review.summary && <p className="review__summary">{review.summary}</p>}

      <div>
        <ScoreBar label="Overall" value={review.overallScore} source={review.scoreSource} lead />
        <ScoreBar label="Quality" value={review.qualityScore} source={review.scoreSource} />
        <ScoreBar label="Value" value={review.valueScore} source={review.scoreSource} />
        <ScoreBar
          label="Reliability"
          value={review.reliabilityScore}
          source={review.scoreSource}
        />
      </div>

      {review.topics.length > 0 && (
        <ul className="topics" style={{ marginTop: '0.75rem' }}>
          {review.topics.map((topic) => (
            <li key={topic}>{topic}</li>
          ))}
        </ul>
      )}

      <div className="review__footer">
        <button className="btn" onClick={handleRescore} disabled={busy}>
          {busy ? 'Scoring…' : 'Score again'}
        </button>
        <span>
          {fromModel
            ? `Took ${review.scoringMillis} ms`
            : 'Score again once the model is up to replace this with real analysis.'}
        </span>
      </div>

      {error && <p className="notice" style={{ marginTop: '0.75rem' }}>{error}</p>}
    </li>
  )
}

export default function ReviewList({ reviews, onRescored }) {
  if (reviews === null) {
    return <p className="product__meta">Loading reviews…</p>
  }
  return (
    <ul className="reviews">
      {reviews.map((review) => (
        <Review key={review.id} review={review} onRescored={onRescored} />
      ))}
    </ul>
  )
}
