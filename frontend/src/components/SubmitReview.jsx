import { useState } from 'react'
import { submitReview } from '../api.js'
import ScoreBar from './ScoreBar.jsx'

const MIN_LENGTH = 15

/**
 * Submitting is where the LLM integration is felt: the request blocks while
 * a local model generates, so the button has to say so rather than appearing
 * broken for five seconds.
 */
export default function SubmitReview({ products, author, onSubmitted }) {
  const [productId, setProductId] = useState(products[0]?.productId ?? '')
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [result, setResult] = useState(null)

  const tooShort = text.trim().length > 0 && text.trim().length < MIN_LENGTH

  async function handleSubmit(event) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setResult(null)
    try {
      const saved = await submitReview(Number(productId), author, text.trim())
      setResult(saved)
      setText('')
      onSubmitted()
    } catch (err) {
      setError(
        err.status === 503
          ? 'review-service did not answer in time. The gateway served its fallback — try again shortly.'
          : err.message,
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="scale-key">
        <h2>Score a new review</h2>
      </div>

      <form className="form" onSubmit={handleSubmit}>
        <label className="field">
          <span>Product</span>
          <select value={productId} onChange={(e) => setProductId(e.target.value)}>
            {products.map((p) => (
              <option key={p.productId} value={p.productId}>
                {p.productName}
              </option>
            ))}
          </select>
        </label>

        <label className="field">
          <span>What the customer wrote</span>
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Great panel for the money, though the stand wobbles."
            required
            minLength={MIN_LENGTH}
            maxLength={2000}
          />
          <span className="field__note">
            {tooShort
              ? `${MIN_LENGTH - text.trim().length} more characters needed — short text carries no signal for any scorer.`
              : `${text.trim().length} of 2000 characters`}
          </span>
        </label>

        <div>
          <button className="btn btn--primary" type="submit" disabled={busy || tooShort}>
            {busy ? 'Reading the review…' : 'Score this review'}
          </button>
        </div>

        {busy && (
          <p className="notice notice--working">
            The model is generating on your CPU. First call after a restart can take 30 seconds
            while the weights load.
          </p>
        )}

        {error && <p className="notice">{error}</p>}
      </form>

      {result && (
        <ul className="reviews" style={{ marginTop: '1.5rem' }}>
          <li className={`review ${result.scoreSource === 'LLM' ? 'from-model' : 'from-fallback'}`}>
            <div className="review__head">
              <span className="review__author">{result.productName}</span>
              <span
                className={`provenance ${result.scoreSource === 'LLM' ? 'from-model' : 'from-fallback'}`}
              >
                {result.scoredBy}
              </span>
              <span className="product__meta">
                {result.sentiment.toLowerCase()} · {result.scoringMillis} ms
              </span>
            </div>
            {result.summary && <p className="review__summary">{result.summary}</p>}
            <ScoreBar label="Overall" value={result.overallScore} source={result.scoreSource} lead />
            <ScoreBar label="Quality" value={result.qualityScore} source={result.scoreSource} />
            <ScoreBar label="Value" value={result.valueScore} source={result.scoreSource} />
            <ScoreBar
              label="Reliability"
              value={result.reliabilityScore}
              source={result.scoreSource}
            />
          </li>
        </ul>
      )}
    </>
  )
}
