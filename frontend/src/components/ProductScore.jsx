import ScoreBar from './ScoreBar.jsx'

/**
 * One aggregated product row.
 *
 * The provenance tag reports the real mix (e.g. "1 of 3 from llama3.2")
 * rather than a single source, because an average built from one model score
 * and two fallback scores is not model output and must not look like it.
 */
export default function ProductScore({ row, expanded, onToggle }) {
  const { reviewCount, llmScoredCount } = row

  if (reviewCount === 0) {
    return (
      <article className="product">
        <div className="product__head">
          <h3 className="product__name">{row.productName}</h3>
          <span className="product__empty">No reviews yet</span>
        </div>
      </article>
    )
  }

  const allFromModel = llmScoredCount === reviewCount
  const source = allFromModel ? 'LLM' : 'FALLBACK'

  return (
    <article className="product">
      <div className="product__head">
        <h3 className="product__name">{row.productName}</h3>
        <span
          className={`provenance ${allFromModel ? 'from-model' : 'from-fallback'}`}
        >
          {llmScoredCount === 0
            ? 'keyword fallback'
            : `${llmScoredCount} of ${reviewCount} from the model`}
        </span>
        <button className="btn btn--plain" onClick={onToggle} aria-expanded={expanded}>
          {expanded ? 'Hide reviews' : `Show ${reviewCount} review${reviewCount === 1 ? '' : 's'}`}
        </button>
      </div>

      <div>
        <ScoreBar label="Overall" value={row.overallScore} source={source} lead />
        <ScoreBar label="Quality" value={row.qualityScore} source={source} />
        <ScoreBar label="Value" value={row.valueScore} source={source} />
        <ScoreBar label="Reliability" value={row.reliabilityScore} source={source} />
      </div>

      <div className="sentiment">
        <span>
          <b>{row.positiveCount}</b> positive
        </span>
        <span>
          <b>{row.neutralCount}</b> neutral
        </span>
        <span>
          <b>{row.negativeCount}</b> negative
        </span>
      </div>

      {row.topTopics.length > 0 && (
        <ul className="topics">
          {row.topTopics.map((topic) => (
            <li key={topic}>{topic}</li>
          ))}
        </ul>
      )}
    </article>
  )
}
