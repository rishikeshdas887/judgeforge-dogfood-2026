import { useEffect, useState } from 'react'

export default function JudgeDashboard() {
  const [projects, setProjects] = useState([])
  const [rubric, setRubric] = useState(null)
  const [forms, setForms] = useState({})
  const [loading, setLoading] = useState(true)
  const [savingProject, setSavingProject] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  async function loadJudgeData() {
    try {
      const [projectsResponse, rubricResponse, ballotsResponse] =
        await Promise.all([
          fetch('/api/judge/projects', {
            credentials: 'include',
          }),
          fetch('/api/judge/rubric', {
            credentials: 'include',
          }),
          fetch('/api/judge/ballots', {
            credentials: 'include',
          }),
        ])

      if (!projectsResponse.ok) {
        throw new Error(
          `Could not load assigned projects: ${projectsResponse.status}`,
        )
      }

      if (!rubricResponse.ok) {
        throw new Error(
          `Could not load rubric: ${rubricResponse.status}`,
        )
      }

      if (!ballotsResponse.ok) {
        throw new Error(
          `Could not load saved ballots: ${ballotsResponse.status}`,
        )
      }

      const assignedProjects = await projectsResponse.json()
      const rubricData = await rubricResponse.json()
      const ballots = await ballotsResponse.json()

      const savedByProject = Object.fromEntries(
        ballots.map((ballot) => [ballot.project, ballot]),
      )

      const initialForms = Object.fromEntries(
        assignedProjects.map((project) => {
          const saved = savedByProject[project.id]

          const criteria = Object.fromEntries(
            rubricData.criteria.map((criterion) => [
              criterion.id,
              saved?.criteria?.[criterion.id] ?? '',
            ]),
          )

          return [
            project.id,
            {
              criteria,
              comment: saved?.comment ?? '',
              weighted_score: saved?.weighted_score ?? null,
            },
          ]
        }),
      )

      setProjects(assignedProjects)
      setRubric(rubricData)
      setForms(initialForms)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    queueMicrotask(loadJudgeData)
  }, [])

  function updateScore(projectId, criterionId, value) {
    setForms((current) => ({
      ...current,
      [projectId]: {
        ...current[projectId],
        criteria: {
          ...current[projectId].criteria,
          [criterionId]: value,
        },
      },
    }))
  }

  function updateComment(projectId, value) {
    setForms((current) => ({
      ...current,
      [projectId]: {
        ...current[projectId],
        comment: value,
      },
    }))
  }

  async function saveBallot(projectId) {
    const form = forms[projectId]

    if (!form || !rubric) {
      return
    }

    setSavingProject(projectId)
    setMessage('')
    setError('')

    try {
      const criteria = Object.fromEntries(
        rubric.criteria.map((criterion) => [
          criterion.id,
          Number(form.criteria[criterion.id]),
        ]),
      )

      if (
        Object.values(criteria).some(
          (score) => Number.isNaN(score),
        )
      ) {
        throw new Error('Enter a score for every rubric criterion.')
      }

      const response = await fetch(
        `/api/judge/ballots/${encodeURIComponent(projectId)}`,
        {
          method: 'PUT',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            criteria,
            comment: form.comment,
          }),
        },
      )

      const text = await response.text()

      if (!response.ok) {
        throw new Error(text || 'Could not save ballot')
      }

      const saved = JSON.parse(text)

      setForms((current) => ({
        ...current,
        [projectId]: {
          ...current[projectId],
          criteria: saved.criteria,
          comment: saved.comment ?? '',
          weighted_score: saved.weighted_score,
        },
      }))

      setMessage(`Saved score for ${projectId}.`)
    } catch (err) {
      setError(err.message)
    } finally {
      setSavingProject('')
    }
  }

  if (loading) {
    return (
      <section className="organizer-section">
        <div className="empty-state">Loading judge workspace...</div>
      </section>
    )
  }

  if (error && !rubric) {
    return (
      <section className="organizer-section">
        <div className="empty-state">{error}</div>
      </section>
    )
  }

  return (
    <section className="organizer-section">
      <div className="section-header organizer-header">
        <div>
          <p className="eyebrow">JUDGE</p>
          <h2>Assigned Projects</h2>
        </div>

        <span>{projects.length} assigned</span>
      </div>

      {projects.length === 0 ? (
        <div className="empty-state">
          No projects are currently assigned to this judge.
        </div>
      ) : (
        <div className="judge-project-list">
          {projects.map((project) => {
            const form = forms[project.id]

            return (
              <article className="dashboard-card" key={project.id}>
                <div className="card-heading">
                  <div>
                    <p className="eyebrow">{project.id}</p>
                    <h3>{project.title}</h3>
                    <p>{project.summary}</p>
                  </div>

                  {form?.weighted_score !== null &&
                    form?.weighted_score !== undefined && (
                      <strong>
                        {form.weighted_score.toFixed(2)} / 5
                      </strong>
                    )}
                </div>

                <div className="judge-rubric-grid">
                  {rubric.criteria.map((criterion) => (
                    <div key={criterion.id}>
                      <label>
                        {criterion.name} · max {criterion.max_score}
                      </label>

                      <input
                        type="number"
                        min="0"
                        max={criterion.max_score}
                        step="0.01"
                        value={form?.criteria?.[criterion.id] ?? ''}
                        onChange={(event) =>
                          updateScore(
                            project.id,
                            criterion.id,
                            event.target.value,
                          )
                        }
                      />

                      <small>
                        Weight {criterion.weight}%
                      </small>
                    </div>
                  ))}
                </div>

                <label className="judge-comment-label">
                  Judge comment
                </label>

                <textarea
                  className="judge-comment"
                  rows="4"
                  value={form?.comment ?? ''}
                  onChange={(event) =>
                    updateComment(project.id, event.target.value)
                  }
                  placeholder="Record concise evidence for the score."
                />

                <div className="card-actions">
                  {message && message.includes(project.id) ? (
                    <span className="weight-valid">{message}</span>
                  ) : (
                    <span>
                      Scores are enforced again by the backend.
                    </span>
                  )}

                  <button
                    className="primary-button compact-button"
                    disabled={savingProject === project.id}
                    onClick={() => saveBallot(project.id)}
                  >
                    {savingProject === project.id
                      ? 'Saving...'
                      : 'Save score'}
                  </button>
                </div>
              </article>
            )
          })}
        </div>
      )}

      {error && rubric && <p className="error">{error}</p>}
    </section>
  )
}
