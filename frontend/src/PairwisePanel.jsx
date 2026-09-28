export default function PairwisePanel({
  comparison,
  progress,
  loading,
  submitting,
  message,
  error,
  onChoose,
  onBack,
  onReload,
}) {
  const completed =
    progress?.completed ??
    comparison?.completed ??
    0

  const possible =
    progress?.possible ??
    comparison?.possible ??
    0

  const remaining =
    progress?.remaining ??
    comparison?.remaining ??
    0

  const isComplete = comparison?.complete === true

  if (loading && !comparison) {
    return (
      <section className="judge-pairwise">
        <div className="judge-pairwise-state">
          <span className="judge-pairwise-kicker">
            PAIRWISE JUDGING
          </span>
          <h2>Loading the next comparison...</h2>
        </div>
      </section>
    )
  }

  return (
    <section className="judge-pairwise">
      <div className="judge-pairwise-header">
        <div>
          <span className="judge-eyebrow">
            PAIRWISE JUDGING
          </span>
          <h2>Which project should rank higher?</h2>
          <p>
            Compare two assigned submissions at a time. Each choice is
            stored as a judge-owned comparison.
          </p>
        </div>

        <div className="judge-pairwise-progress">
          <span>PROGRESS</span>
          <strong>
            {completed}/{possible}
          </strong>
          <small>{remaining} remaining</small>
        </div>
      </div>

      <div className="judge-pairwise-isolation">
        <span>JUDGE ISOLATION ACTIVE</span>
        <p>
          Only projects assigned to the authenticated judge can be
          compared. The backend enforces this boundary.
        </p>
      </div>

      {isComplete ? (
        <div className="judge-pairwise-complete">
          <div className="judge-pairwise-complete-mark">✓</div>
          <span className="judge-eyebrow">
            PAIRWISE QUEUE COMPLETE
          </span>
          <h3>All assigned project pairs have been compared.</h3>
          <p>
            {completed} of {possible} comparisons are recorded for
            this judge.
          </p>

          <div className="judge-pairwise-complete-actions">
            <button
              type="button"
              className="judge-secondary-button"
              onClick={onBack}
            >
              Back to standard scoring
            </button>
            <button
              type="button"
              className="judge-primary-button"
              onClick={onReload}
              disabled={loading}
            >
              Refresh
            </button>
          </div>
        </div>
      ) : (
        <>
          <div className="judge-pair-grid">
            <article className="judge-pair-card">
              <div className="judge-pair-label">PROJECT A</div>
              <span className="judge-pair-id">
                {comparison?.project_a?.id}
              </span>
              <h3>{comparison?.project_a?.title}</h3>
              <p>{comparison?.project_a?.summary}</p>

              {comparison?.project_a?.track && (
                <span className="judge-pair-meta">
                  {comparison.project_a.track}
                </span>
              )}

              {comparison?.project_a?.repository_url && (
                <a
                  href={comparison.project_a.repository_url}
                  target="_blank"
                  rel="noreferrer"
                  className="judge-pair-repo"
                >
                  Open repository →
                </a>
              )}

              <button
                type="button"
                className="judge-pair-choice"
                onClick={() =>
                  onChoose(comparison.project_a.id)
                }
                disabled={submitting}
              >
                Choose Project A
              </button>
            </article>

            <div className="judge-pair-vs">VS</div>

            <article className="judge-pair-card">
              <div className="judge-pair-label">PROJECT B</div>
              <span className="judge-pair-id">
                {comparison?.project_b?.id}
              </span>
              <h3>{comparison?.project_b?.title}</h3>
              <p>{comparison?.project_b?.summary}</p>

              {comparison?.project_b?.track && (
                <span className="judge-pair-meta">
                  {comparison.project_b.track}
                </span>
              )}

              {comparison?.project_b?.repository_url && (
                <a
                  href={comparison.project_b.repository_url}
                  target="_blank"
                  rel="noreferrer"
                  className="judge-pair-repo"
                >
                  Open repository →
                </a>
              )}

              <button
                type="button"
                className="judge-pair-choice"
                onClick={() =>
                  onChoose(comparison.project_b.id)
                }
                disabled={submitting}
              >
                Choose Project B
              </button>
            </article>
          </div>

          <div className="judge-pairwise-footer">
            {submitting && (
              <span className="judge-pairwise-status">
                Recording comparison...
              </span>
            )}

            {message && (
              <span className="judge-pairwise-success">
                {message}
              </span>
            )}

            {error && (
              <span className="judge-pairwise-error">
                {error}
              </span>
            )}

            <div className="judge-pairwise-footer-actions">
              <button
                type="button"
                className="judge-secondary-button"
                onClick={onBack}
                disabled={submitting}
              >
                Standard rubric
              </button>

              <button
                type="button"
                className="judge-secondary-button"
                onClick={onReload}
                disabled={loading || submitting}
              >
                Refresh pair
              </button>
            </div>
          </div>
        </>
      )}
    </section>
  )
}
