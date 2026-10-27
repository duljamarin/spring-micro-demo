import { useEffect, useState } from 'react'
import { getReviews } from '../api.js'
import EngineReadout from '../components/EngineReadout.jsx'
import ProductScore from '../components/ProductScore.jsx'
import ReviewList from '../components/ReviewList.jsx'
import SubmitReview from '../components/SubmitReview.jsx'

/** The LLM feature: aggregated product scores, the reviews behind them, and
 *  the form that puts a new review through the model. */
export default function Scores({ engine, rows, author, onChanged, onNotify }) {
  const [openProductId, setOpenProductId] = useState(null)
  const [reviews, setReviews] = useState(null)

  useEffect(() => {
    if (openProductId === null) {
      setReviews(null)
      return
    }
    let cancelled = false
    setReviews(null)
    getReviews(openProductId)
      .then((list) => !cancelled && setReviews(list))
      .catch((err) => !cancelled && onNotify(null, err))
    return () => {
      cancelled = true
    }
  }, [openProductId, onNotify])

  /** A re-scored review changes both the list and the product's average. */
  function handleRescored(updated) {
    setReviews((current) =>
      current?.map((review) => (review.id === updated.id ? updated : review)),
    )
    onChanged()
  }

  return (
    <>
      <div style={{ marginTop: '1.25rem' }}>
        <EngineReadout engine={engine} />
      </div>

      <div className="scale-key">
        <h2>Scoreboard</h2>
        <span className="scale-key__hint">
          Every bar runs 0&ndash;100. The tick at the middle is 50, which means the review never
          mentioned that aspect.
        </span>
      </div>

      {rows === null && <p className="product__meta">Loading scores…</p>}

      {rows?.length === 0 && (
        <div className="empty">
          <p>No products to score yet. Add one in the catalog first.</p>
        </div>
      )}

      {rows?.map((row) => (
        <div key={row.productId}>
          <ProductScore
            row={row}
            expanded={openProductId === row.productId}
            onToggle={() =>
              setOpenProductId(openProductId === row.productId ? null : row.productId)
            }
          />
          {openProductId === row.productId && (
            <div style={{ padding: '0 0 1.5rem' }}>
              <ReviewList reviews={reviews} onRescored={handleRescored} />
            </div>
          )}
        </div>
      ))}

      {rows && rows.length > 0 && (
        <SubmitReview products={rows} author={author} onSubmitted={onChanged} />
      )}
    </>
  )
}
