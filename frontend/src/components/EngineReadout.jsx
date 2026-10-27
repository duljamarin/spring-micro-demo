/**
 * The page's hero: whether the local model is answering right now.
 *
 * It states the consequence, not just the state - "scores below came from the
 * keyword fallback" is what a developer needs to know, and it is why the
 * numbers further down might look flat.
 */
export default function EngineReadout({ engine }) {
  if (!engine) {
    return (
      <div className="engine">
        <span className="engine__lamp" />
        <div>
          <div className="engine__verdict">Checking the scoring engine</div>
          <p className="engine__detail">Asking review-service which scorer is live.</p>
        </div>
      </div>
    )
  }

  const live = engine.llmEnabled && engine.llmReachable

  return (
    <div className={`engine ${live ? 'engine--live' : 'engine--degraded'}`}>
      <span className="engine__lamp" />
      <div>
        <div className="engine__verdict">
          {live
            ? `Scoring with ${engine.model}`
            : `Scoring with the ${engine.fallbackScorer}`}
        </div>
        <p className="engine__detail">
          {engine.detail}
          {!live && ' New reviews are still scored and stored, but by keyword matching only.'}
        </p>
      </div>
    </div>
  )
}
