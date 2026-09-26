import { useEffect, useMemo, useState } from 'react'
import EventConfiguration from './EventConfiguration'

export default function OrganizerDashboard() {
  const [rubric, setRubric] = useState(null)
  const [progress, setProgress] = useState(null)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [loadError, setLoadError] = useState('')

  async function loadRubric() {
    const response = await fetch('/api/organizer/rubric', {
      credentials: 'include',
    })

    if (!response.ok) {
      throw new Error('Could not load rubric')
    }

    setRubric(await response.json())
  }

  async function loadProgress() {
    const response = await fetch('/api/organizer/judging-progress', {
      credentials: 'include',
    })

    if (!response.ok) {
      throw new Error('Could not load judging progress')
    }

    setProgress(await response.json())
  }

  useEffect(() => {
    async function load() {
      try {
        await Promise.all([loadRubric(), loadProgress()])
        setLoadError('')
      } catch (error) {
        setLoadError(error.message)
      }
    }

    load()

    const timer = window.setInterval(async () => {
      try {
        await loadProgress()
      } catch {
        // Keep the existing dashboard visible if a refresh temporarily fails.
      }
    }, 5000)

    return () => window.clearInterval(timer)
  }, [])

  const totalWeight = useMemo(
    () =>
      rubric?.criteria?.reduce(
        (total, criterion) => total + Number(criterion.weight || 0),
        0,
      ) ?? 0,
    [rubric],
  )

  function updateCriterion(index, field, value) {
    setRubric((current) => ({
      ...current,
      criteria: current.criteria.map((criterion, criterionIndex) =>
        criterionIndex === index
          ? { ...criterion, [field]: value }
          : criterion,
      ),
    }))
  }

  async function saveRubric() {
    if (!rubric) return

    setSaving(true)
    setMessage('')

    try {
      const payload = {
        ...rubric,
        version: Number(rubric.version),
        criteria: rubric.criteria.map((criterion) => ({
          ...criterion,
          weight: Number(criterion.weight),
          max_score: Number(criterion.max_score),
        })),
      }

      const response = await fetch('/api/organizer/rubric', {
        method: 'PUT',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
      })

      const text = await response.text()

      if (!response.ok) {
        throw new Error(text || 'Could not save rubric')
      }

      setRubric(JSON.parse(text))
      setMessage('Rubric saved.')
    } catch (error) {
      setMessage(error.message)
    } finally {
      setSaving(false)
    }
  }

  if (loadError) {
    return (
      <section className="organizer-section">
        <div className="empty-state">{loadError}</div>
      </section>
    )
  }

  return (
    <section className="organizer-section">
      <EventConfiguration />

      <div className="section-header organizer-header">
        <div>
          <p className="eyebrow">ORGANIZER</p>
          <h2>Judging Control</h2>
        </div>

        {progress && (
          <span>
            Updated{' '}
            {new Date(progress.generated_at).toLocaleTimeString()}
          </span>
        )}
      </div>

      <div className="organizer-grid">
        <div className="dashboard-card">
          <div className="card-heading">
            <div>
              <h3>Weighted Rubric</h3>
              <p>Configure criterion weights and score limits.</p>
            </div>

            <strong>{totalWeight.toFixed(2)}%</strong>
          </div>

          {rubric?.criteria?.map((criterion, index) => (
            <div className="rubric-row" key={criterion.id}>
              <div>
                <label>Name</label>
                <input
                  value={criterion.name}
                  onChange={(event) =>
                    updateCriterion(
                      index,
                      'name',
                      event.target.value,
                    )
                  }
                />
              </div>

              <div>
                <label>Weight %</label>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={criterion.weight}
                  onChange={(event) =>
                    updateCriterion(
                      index,
                      'weight',
                      event.target.value,
                    )
                  }
                />
              </div>

              <div>
                <label>Max score</label>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={criterion.max_score}
                  onChange={(event) =>
                    updateCriterion(
                      index,
                      'max_score',
                      event.target.value,
                    )
                  }
                />
              </div>
            </div>
          ))}

          <div className="card-actions">
            <span
              className={
                Math.abs(totalWeight - 100) < 0.01
                  ? 'weight-valid'
                  : 'weight-invalid'
              }
            >
              {Math.abs(totalWeight - 100) < 0.01
                ? 'Weights total 100%'
                : 'Weights must total 100%'}
            </span>

            <button
              className="primary-button compact-button"
              disabled={
                saving || Math.abs(totalWeight - 100) >= 0.01
              }
              onClick={saveRubric}
            >
              {saving ? 'Saving...' : 'Save rubric'}
            </button>
          </div>

          {message && <p className="form-message">{message}</p>}
        </div>

        <div className="dashboard-card">
          <div className="card-heading">
            <div>
              <h3>Judging Progress</h3>
              <p>Live assignment completion across all judges.</p>
            </div>

            <strong>
              {progress?.overall_completion_percent?.toFixed(2) ?? '0.00'}%
            </strong>
          </div>

          <div className="progress-summary">
            <div>
              <span>Assigned</span>
              <strong>{progress?.total_assigned_reviews ?? 0}</strong>
            </div>

            <div>
              <span>Completed</span>
              <strong>{progress?.total_completed_reviews ?? 0}</strong>
            </div>

            <div>
              <span>Pending</span>
              <strong>{progress?.total_pending_reviews ?? 0}</strong>
            </div>
          </div>

          <div className="progress-bar">
            <div
              style={{
                width: `${progress?.overall_completion_percent ?? 0}%`,
              }}
            />
          </div>

          <div className="judge-progress-list">
            {progress?.judges?.map((judge) => (
              <div className="judge-progress-row" key={judge.judge}>
                <div>
                  <strong>{judge.name}</strong>
                  <span>
                    {judge.judge} · {judge.completed_reviews}/
                    {judge.assigned_reviews} completed
                  </span>
                </div>

                <strong>
                  {judge.completion_percent.toFixed(2)}%
                </strong>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}
