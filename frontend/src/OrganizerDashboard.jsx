import { useEffect, useMemo, useState } from 'react'
import EventConfiguration from './EventConfiguration'

async function readResponse(response, fallback) {
  const text = await response.text()
  let data = null

  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = text
  }

  if (!response.ok) {
    throw new Error(
      typeof data === 'string'
        ? data || fallback
        : data?.message || fallback,
    )
  }

  return data
}

export default function OrganizerDashboard() {
  const [rubric, setRubric] = useState(null)
  const [progress, setProgress] = useState(null)
  const [judges, setJudges] = useState([])
  const [projects, setProjects] = useState([])
  const [selectedJudge, setSelectedJudge] = useState('')
  const [selectedProjects, setSelectedProjects] = useState([])
  const [batchJson, setBatchJson] = useState('')
  const [targetReviews, setTargetReviews] = useState(3)
  const [results, setResults] = useState(null)
  const [saving, setSaving] = useState(false)
  const [judgesLoading, setJudgesLoading] = useState(false)
  const [assignmentMessage, setAssignmentMessage] = useState('')
  const [resultMessage, setResultMessage] = useState('')
  const [message, setMessage] = useState('')
  const [loadError, setLoadError] = useState('')

  async function loadRubric() {
    const response = await fetch('/api/organizer/rubric', {
      credentials: 'include',
    })

    const data = await readResponse(response, 'Could not load rubric')
    setRubric(data)
  }

  async function loadProgress() {
    const response = await fetch('/api/organizer/judging-progress', {
      credentials: 'include',
    })

    const data = await readResponse(
      response,
      'Could not load judging progress',
    )
    setProgress(data)
  }

  async function loadJudges() {
    setJudgesLoading(true)

    try {
      const response = await fetch('/api/organizer/judges', {
        credentials: 'include',
      })

      const data = await readResponse(
        response,
        'Could not load judges',
      )

      setJudges(Array.isArray(data) ? data : [])

      if (!selectedJudge && Array.isArray(data) && data.length > 0) {
        setSelectedJudge(data[0].judge)
      }
    } finally {
      setJudgesLoading(false)
    }
  }

  async function loadProjects() {
    const response = await fetch('/projects', {
      credentials: 'include',
    })

    const data = await readResponse(
      response,
      'Could not load projects',
    )

    setProjects(Array.isArray(data) ? data : [])
  }

  async function loadResults() {
    const response = await fetch('/api/results', {
      credentials: 'include',
    })

    if (response.status === 404) {
      setResults(null)
      return
    }

    const data = await readResponse(
      response,
      'Could not load normalization results',
    )

    setResults(data)
  }

  useEffect(() => {
    async function load() {
      try {
        await Promise.all([
          loadRubric(),
          loadProgress(),
          loadJudges(),
          loadProjects(),
          loadResults(),
        ])
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

  useEffect(() => {
    const judge = judges.find((item) => item.judge === selectedJudge)

    if (!judge) {
      setSelectedProjects([])
      return
    }

    setSelectedProjects(
      Array.isArray(judge.assigned_project_ids)
        ? judge.assigned_project_ids
        : [],
    )
  }, [selectedJudge, judges])

  const selectedJudgeDetails = useMemo(
    () => judges.find((judge) => judge.judge === selectedJudge) ?? null,
    [judges, selectedJudge],
  )

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

  function toggleProject(projectId) {
    setSelectedProjects((current) =>
      current.includes(projectId)
        ? current.filter((id) => id !== projectId)
        : [...current, projectId],
    )
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

      const data = await readResponse(response, 'Could not save rubric')
      setRubric(data)
      setMessage('Rubric saved.')
    } catch (error) {
      setMessage(error.message)
    } finally {
      setSaving(false)
    }
  }

  async function inviteJudge(judgeId) {
    setAssignmentMessage('')

    try {
      const response = await fetch(
        `/api/organizer/judges/${encodeURIComponent(judgeId)}/invite`,
        {
          method: 'POST',
          credentials: 'include',
        },
      )

      const data = await readResponse(response, 'Could not invite judge')
      setAssignmentMessage(
        `Invitation created for ${data.judge}. Token: ${data.invite_token}`,
      )
      await loadJudges()
    } catch (error) {
      setAssignmentMessage(error.message)
    }
  }

  async function saveManualAssignment() {
    if (!selectedJudge) {
      setAssignmentMessage('Select a judge first.')
      return
    }

    if (selectedProjects.length === 0) {
      setAssignmentMessage(
        'Select at least one project before saving. Use the batch or API path when intentionally clearing assignments.',
      )
      return
    }

    setAssignmentMessage('')

    try {
      const response = await fetch(
        `/api/organizer/judges/${encodeURIComponent(selectedJudge)}/assignments`,
        {
          method: 'PUT',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            project_ids: selectedProjects,
          }),
        },
      )

      const data = await readResponse(
        response,
        'Could not save judge assignment',
      )

      setAssignmentMessage(
        `${data.judge} now has ${data.assignment_count} assigned projects.`,
      )
      await Promise.all([loadJudges(), loadProgress()])
    } catch (error) {
      setAssignmentMessage(error.message)
    }
  }

  async function runBatchAssignment(dryRun) {
    setAssignmentMessage('')

    try {
      const payload = JSON.parse(batchJson)

      if (!Array.isArray(payload.assignments)) {
        throw new Error('Batch JSON must contain an assignments array.')
      }

      payload.dry_run = dryRun

      const response = await fetch(
        '/api/organizer/judges/assignments/batch',
        {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(payload),
        },
      )

      const data = await readResponse(
        response,
        'Could not process batch assignment',
      )

      setAssignmentMessage(
        `${dryRun ? 'Dry run' : 'Batch update'} completed for ${data.judge_count} judge(s).`,
      )

      if (!dryRun) {
        await Promise.all([loadJudges(), loadProgress()])
      }
    } catch (error) {
      setAssignmentMessage(error.message)
    }
  }

  async function runAutomaticAssignment() {
    setAssignmentMessage('')

    try {
      const response = await fetch(
        '/api/organizer/judges/assignments/auto',
        {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            target_reviews_per_project: Number(targetReviews),
            dry_run: false,
          }),
        },
      )

      const data = await readResponse(
        response,
        'Could not run automatic assignment',
      )

      setAssignmentMessage(
        `Automatic assignment added ${data.assignments_added} assignment(s) across ${data.projects_changed} project(s).`,
      )

      await Promise.all([loadJudges(), loadProgress()])
    } catch (error) {
      setAssignmentMessage(error.message)
    }
  }

  async function normalizeResults() {
    setResultMessage('')

    try {
      const response = await fetch('/api/results/normalize', {
        method: 'POST',
        credentials: 'include',
      })

      const data = await readResponse(
        response,
        'Could not normalize results',
      )

      setResults(data)
      setResultMessage(
        `Normalization ${data.normalization_version} completed for ${data.eligible_ballots} eligible ballot(s).`,
      )
    } catch (error) {
      setResultMessage(error.message)
    }
  }

  async function publishResults() {
    setResultMessage("")

    try {
      const response = await fetch("/api/results/publish", {
        method: "POST",
        credentials: "include",
      })

      const data = await readResponse(
        response,
        "Could not publish results",
      )

      setResultMessage(
        `Results published at ${data.published_at}.`,
      )
    } catch (error) {
      setResultMessage(error.message)
    }
  }

  async function refreshResults() {
    setResultMessage('')

    try {
      await loadResults()
      setResultMessage('Latest normalization results loaded.')
    } catch (error) {
      setResultMessage(error.message)
    }
  }

  function exportCsv() {
    window.open('/api/export.csv', '_blank', 'noopener,noreferrer')
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

      <div className="organizer-panel-grid">
        <div className="dashboard-card">
          <div className="card-heading">
            <div>
              <h3>Judge Management</h3>
              <p>Invite judges and manage their assignments.</p>
            </div>
          </div>

          {judgesLoading ? (
            <p>Loading judges...</p>
          ) : (
            <div className="judge-management-list">
              {judges.map((judge) => (
                <div className="judge-management-row" key={judge.judge}>
                  <div>
                    <strong>{judge.name}</strong>
                    <span>
                      {judge.judge} · {judge.email}
                    </span>
                    <span>
                      {judge.invitation_status} ·{' '}
                      {judge.assigned_projects} assigned · tracks:{' '}
                      {judge.allowed_tracks.join(', ')}
                    </span>
                  </div>

                  <div className="judge-management-actions">
                    <button
                      className="secondary-button compact-button"
                      onClick={() => {
                        setSelectedJudge(judge.judge)
                        setAssignmentMessage('')
                      }}
                    >
                      Manage
                    </button>

                    <button
                      className="primary-button compact-button"
                      onClick={() => inviteJudge(judge.judge)}
                    >
                      Invite
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="dashboard-card">
          <div className="card-heading">
            <div>
              <h3>Assignments</h3>
              <p>
                {selectedJudgeDetails
                  ? `Managing ${selectedJudgeDetails.name} (${selectedJudge})`
                  : 'Select a judge to manage assignments.'}
              </p>
            </div>
          </div>

          <label>Judge</label>
          <select
            className="organizer-select"
            value={selectedJudge}
            onChange={(event) => setSelectedJudge(event.target.value)}
          >
            {judges.map((judge) => (
              <option key={judge.judge} value={judge.judge}>
                {judge.judge} — {judge.name}
              </option>
            ))}
          </select>

          <div className="project-picker">
            {projects.map((project) => (
              <label key={project.id} className="project-check-row">
                <input
                  type="checkbox"
                  checked={selectedProjects.includes(project.id)}
                  onChange={() => toggleProject(project.id)}
                />
                <span>
                  <strong>{project.id}</strong> · {project.title || project.name}
                  <small>{project.track}</small>
                </span>
              </label>
            ))}
          </div>

          <div className="card-actions">
            <span>{selectedProjects.length} selected</span>
            <button
              className="primary-button compact-button"
              onClick={saveManualAssignment}
            >
              Save assignment
            </button>
          </div>

          <div className="assignment-subpanel">
            <h4>Automatic assignment</h4>
            <div className="inline-controls">
              <label>
                Reviews / project
                <input
                  type="number"
                  min="1"
                  max={judges.length || 1}
                  value={targetReviews}
                  onChange={(event) =>
                    setTargetReviews(event.target.value)
                  }
                />
              </label>
              <button
                className="primary-button compact-button"
                onClick={runAutomaticAssignment}
              >
                Run automatic assignment
              </button>
            </div>
          </div>

          <div className="assignment-subpanel">
            <h4>Batch assignment JSON</h4>
            <textarea
              className="organizer-textarea"
              rows="8"
              value={batchJson}
              onChange={(event) => setBatchJson(event.target.value)}
              placeholder='{"assignments":[{"judge_id":"jdg_01","project_ids":["prj_01"]}]}'
            />
            <div className="card-actions">
              <button
                className="secondary-button compact-button"
                onClick={() => runBatchAssignment(true)}
              >
                Validate batch
              </button>
              <button
                className="primary-button compact-button"
                onClick={() => runBatchAssignment(false)}
              >
                Apply batch
              </button>
            </div>
          </div>

          {assignmentMessage && (
            <p className="form-message">{assignmentMessage}</p>
          )}
        </div>
      </div>

      <div className="dashboard-card results-card">
        <div className="card-heading">
          <div>
            <h3>Normalization & Results</h3>
            <p>
              Run deterministic normalization, inspect the latest run, or
              export judging data.
            </p>
          </div>

          <strong>
            {results?.normalization_version ?? 'No run'}
          </strong>
        </div>

        <div className="results-actions">
          <button
            className="primary-button compact-button"
            onClick={normalizeResults}
          >
            Run normalization
          </button>

          <button
            className="secondary-button compact-button"
            onClick={publishResults}
            disabled={!results}
          >
            Publish results
          </button>

          <button
            className="secondary-button compact-button"
            onClick={refreshResults}
          >
            Refresh results
          </button>

          <button
            className="secondary-button compact-button"
            onClick={exportCsv}
          >
            Export CSV
          </button>
        </div>

        {resultMessage && <p className="form-message">{resultMessage}</p>}

        {results && (
          <pre className="results-json">
            {JSON.stringify(results, null, 2)}
          </pre>
        )}
      </div>
    </section>
  )
}
