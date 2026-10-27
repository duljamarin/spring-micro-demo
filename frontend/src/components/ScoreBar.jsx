/**
 * One score on the shared 0-100 scale.
 *
 * `source` drives the fill colour so provenance is visible without reading a
 * label, and `muted` dims a row whose value is exactly 50 - the backend uses
 * 50 to mean "the review never mentioned this aspect", and a dimmed bar
 * sitting on the midline reads as "no signal" rather than "mediocre".
 */
export default function ScoreBar({ label, value, source = 'FALLBACK', lead = false }) {
  const noSignal = value === 50
  const classes = [
    'bar',
    source === 'LLM' ? 'from-model' : 'from-fallback',
    lead ? 'bar--lead' : '',
    noSignal && !lead ? 'bar--muted' : '',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={classes}>
      <span className="bar__label">{label}</span>
      <div
        className="bar__track"
        role="meter"
        aria-label={`${label}: ${value} out of 100${noSignal ? ', not mentioned' : ''}`}
        aria-valuenow={value}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div className="bar__fill" style={{ width: `${value}%` }} />
      </div>
      <span className="bar__value">{value}</span>
    </div>
  )
}
