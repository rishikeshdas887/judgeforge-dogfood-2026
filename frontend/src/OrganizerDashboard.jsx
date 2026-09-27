import { useEffect, useMemo, useState } from 'react'
import EventConfiguration from './EventConfiguration'
import CommunityVotingAdmin from './CommunityVotingAdmin'

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

  const primaryBtn =
    'rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-50 disabled:cursor-not-allowed transition-colors'
  const secondaryBtn =
    'rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50 transition-colors'
  const inputCls =
    'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-slate-900'
  const labelCls = 'block text-xs text-slate-400 mb-1'

  if (loadError) {
    return (
      <section className="max-w-5xl mx-auto mt-8">
        <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-sm text-red-600">
          {loadError}
        </div>
      </section>
    )
  }

  return (
    <section className="max-w-5xl mx-auto mt-8 space-y-6">
      <EventConfiguration />
      <CommunityVotingAdmin />

      <div className="flex items-center justify-between">
        <div>
          <p className="text-xs font-semibold tracking-widest text-slate-400 uppercase">
            Organizer
          </p>
          <h2 className="text-xl font-semibold text-slate-900 mt-1">Judging Control</h2>
        </div>

        {progress && (
          <span className="text-sm text-slate-500">
            Updated {new Date(progress.generated_at).toLocaleTimeString()}
          </span>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-start justify-between gap-4 mb-4">
            <div>
              <h3 className="text-base font-semibold text-slate-900">Weighted Rubric</h3>
              <p className="text-sm text-slate-500 mt-1">Configure criterion weights and score limits.</p>
            </div>
            <strong className="text-lg font-semibold text-slate-900 whitespace-nowrap">
              {totalWeight.toFixed(2)}%
            </strong>
          </div>

          {rubric?.criteria?.map((criterion, index) => (
            <div className="grid grid-cols-3 gap-3 py-3 border-t border-slate-100" key={criterion.id}>
              <div>
                <label className={labelCls}>Name</label>
                <input
                  className={inputCls}
                  value={criterion.name}
                  onChange={(event) => updateCriterion(index, 'name', event.target.value)}
                />
              </div>

              <div>
                <label className={labelCls}>Weight %</label>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  className={inputCls}
                  value={criterion.weight}
                  onChange={(event) => updateCriterion(index, 'weight', event.target.value)}
                />
              </div>

              <div>
                <label className={labelCls}>Max score</label>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  className={inputCls}
                  value={criterion.max_score}
                  onChange={(event) => updateCriterion(index, 'max_score', event.target.value)}
                />
              </div>
            </div>
          ))}

          <div className="flex items-center justify-between mt-4">
            <span
              className={
                Math.abs(totalWeight - 100) < 0.01
                  ? 'text-sm font-semibold text-green-600'
                  : 'text-sm font-semibold text-red-600'
              }
            >
              {Math.abs(totalWeight - 100) < 0.01 ? 'Weights total 100%' : 'Weights must total 100%'}
            </span>

            <button
              className={primaryBtn}
              disabled={saving || Math.abs(totalWeight - 100) >= 0.01}
              onClick={saveRubric}
            >
              {saving ? 'Saving...' : 'Save rubric'}
            </button>
          </div>

          {message && <p className="text-sm text-slate-500 mt-2">{message}</p>}
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-start justify-between gap-4 mb-4">
            <div>
              <h3 className="text-base font-semibold text-slate-900">Judging Progress</h3>
              <p className="text-sm text-slate-500 mt-1">Live assignment completion across all judges.</p>
            </div>
            <strong className="text-lg font-semibold text-slate-900">
              {progress?.overall_completion_percent?.toFixed(2) ?? '0.00'}%
            </strong>
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div className="rounded-lg bg-slate-50 p-3">
              <span className="block text-xs text-slate-400 mb-1">Assigned</span>
              <strong className="text-lg font-semibold text-slate-900">{progress?.total_assigned_reviews ?? 0}</strong>
            </div>
            <div className="rounded-lg bg-slate-50 p-3">
              <span className="block text-xs text-slate-400 mb-1">Completed</span>
              <strong className="text-lg font-semibold text-slate-900">{progress?.total_completed_reviews ?? 0}</strong>
            </div>
            <div className="rounded-lg bg-slate-50 p-3">
              <span className="block text-xs text-slate-400 mb-1">Pending</span>
              <strong className="text-lg font-semibold text-slate-900">{progress?.total_pending_reviews ?? 0}</strong>
            </div>
          </div>

          <div className="h-2.5 rounded-full bg-slate-200 overflow-hidden my-4">
            <div
              className="h-full bg-slate-900"
              style={{ width: `${progress?.overall_completion_percent ?? 0}%` }}
            />
          </div>

          <div className="max-h-96 overflow-y-auto border-t border-slate-100 divide-y divide-slate-100">
            {progress?.judges?.map((judge) => (
              <div className="flex items-center justify-between gap-4 py-3" key={judge.judge}>
                <div>
                  <strong className="block text-sm font-semibold text-slate-900">{judge.name}</strong>
                  <span className="block text-xs text-slate-400 mt-0.5">
                    {judge.judge} · {judge.completed_reviews}/{judge.assigned_reviews} completed
                  </span>
                </div>
                <strong className="text-sm font-semibold text-slate-900">
                  {judge.completion_percent.toFixed(2)}%
                </strong>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="mb-4">
            <h3 className="text-base font-semibold text-slate-900">Judge Management</h3>
            <p className="text-sm text-slate-500 mt-1">Invite judges and manage their assignments.</p>
          </div>

          {judgesLoading ? (
            <p className="text-sm text-slate-500">Loading judges...</p>
          ) : (
            <div className="max-h-96 overflow-y-auto divide-y divide-slate-100">
              {judges.map((judge) => (
                <div className="flex items-center justify-between gap-4 py-3" key={judge.judge}>
                  <div className="min-w-0">
                    <strong className="block text-sm font-semibold text-slate-900">{judge.name}</strong>
                    <span className="block text-xs text-slate-400 mt-0.5">
                      {judge.judge} · {judge.email}
                    </span>
                    <span className="block text-xs text-slate-400 mt-0.5">
                      {judge.invitation_status} · {judge.assigned_projects} assigned · tracks: {judge.allowed_tracks.join(', ')}
                    </span>
                  </div>

                  <div className="flex gap-2 flex-shrink-0">
                    <button
                      className={secondaryBtn}
                      onClick={() => {
                        setSelectedJudge(judge.judge)
                        setAssignmentMessage('')
                      }}
                    >
                      Manage
                    </button>
                    <button className={primaryBtn} onClick={() => inviteJudge(judge.judge)}>
                      Invite
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="mb-4">
            <h3 className="text-base font-semibold text-slate-900">Assignments</h3>
            <p className="text-sm text-slate-500 mt-1">
              {selectedJudgeDetails
                ? `Managing ${selectedJudgeDetails.name} (${selectedJudge})`
                : 'Select a judge to manage assignments.'}
            </p>
          </div>

          <label className={labelCls}>Judge</label>
          <select
            className={`${inputCls} bg-white`}
            value={selectedJudge}
            onChange={(event) => setSelectedJudge(event.target.value)}
          >
            {judges.map((judge) => (
              <option key={judge.judge} value={judge.judge}>
                {judge.judge} — {judge.name}
              </option>
            ))}
          </select>

          <div className="max-h-72 overflow-y-auto border border-slate-100 rounded-lg mt-3">
            {projects.map((project) => (
              <label
                key={project.id}
                className="flex items-start gap-2 px-3 py-2 border-b border-slate-100 last:border-0 cursor-pointer hover:bg-slate-50"
              >
                <input
                  type="checkbox"
                  className="mt-1"
                  checked={selectedProjects.includes(project.id)}
                  onChange={() => toggleProject(project.id)}
                />
                <span className="text-sm text-slate-700">
                  <strong className="font-semibold">{project.id}</strong> · {project.title || project.name}
                  <small className="block text-xs text-slate-400">{project.track}</small>
                </span>
              </label>
            ))}
          </div>

          <div className="flex items-center justify-between mt-4">
            <span className="text-sm text-slate-500">{selectedProjects.length} selected</span>
            <button className={primaryBtn} onClick={saveManualAssignment}>
              Save assignment
            </button>
          </div>

          <div className="mt-5 pt-4 border-t border-slate-100">
            <h4 className="text-sm font-semibold text-slate-900 mb-3">Automatic assignment</h4>
            <div className="flex items-end gap-3">
              <label className="flex-1">
                <span className={labelCls}>Reviews / project</span>
                <input
                  type="number"
                  min="1"
                  max={judges.length || 1}
                  className={inputCls}
                  value={targetReviews}
                  onChange={(event) => setTargetReviews(event.target.value)}
                />
              </label>
              <button className={primaryBtn} onClick={runAutomaticAssignment}>
                Run automatic assignment
              </button>
            </div>
          </div>

          <div className="mt-5 pt-4 border-t border-slate-100">
            <h4 className="text-sm font-semibold text-slate-900 mb-3">Batch assignment JSON</h4>
            <textarea
              className={inputCls}
              rows="8"
              value={batchJson}
              onChange={(event) => setBatchJson(event.target.value)}
              placeholder='{"assignments":[{"judge_id":"jdg_01","project_ids":["prj_01"]}]}'
            />
            <div className="flex gap-2 mt-3">
              <button className={secondaryBtn} onClick={() => runBatchAssignment(true)}>
                Validate batch
              </button>
              <button className={primaryBtn} onClick={() => runBatchAssignment(false)}>
                Apply batch
              </button>
            </div>
          </div>

          {assignmentMessage && <p className="text-sm text-slate-500 mt-3">{assignmentMessage}</p>}
        </div>
      </div>

      <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-start justify-between gap-4 mb-4">
          <div>
            <h3 className="text-base font-semibold text-slate-900">Normalization & Results</h3>
            <p className="text-sm text-slate-500 mt-1">
              Run deterministic normalization, inspect the latest run, or export judging data.
            </p>
          </div>
          <strong className="text-sm font-semibold text-slate-900 whitespace-nowrap">
            {results?.normalization_version ?? 'No run'}
          </strong>
        </div>

        <div className="flex flex-wrap gap-2">
          <button className={primaryBtn} onClick={normalizeResults}>
            Run normalization
          </button>
          <button className={secondaryBtn} onClick={publishResults} disabled={!results}>
            Publish results
          </button>
          <button className={secondaryBtn} onClick={refreshResults}>
            Refresh results
          </button>
          <button className={secondaryBtn} onClick={exportCsv}>
            Export CSV
          </button>
        </div>

        {resultMessage && <p className="text-sm text-slate-500 mt-3">{resultMessage}</p>}

        {results && (
          <pre className="mt-4 max-h-96 overflow-auto rounded-lg bg-slate-50 p-4 text-xs text-slate-600">
            {JSON.stringify(results, null, 2)}
          </pre>
        )}
      </div>
    </section>
  )
}