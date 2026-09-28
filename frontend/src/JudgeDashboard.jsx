import { useEffect, useMemo, useState } from 'react'
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Check,
  ChevronLeft,
  ChevronRight,
  CircleUserRound,
  ExternalLink,
  FileCode2,
  Info,
  LockKeyhole,
  Menu,
  Save,
  ShieldCheck,
  Terminal,
  X,
} from 'lucide-react'

import PairwisePanel from './PairwisePanel'

export default function JudgeDashboard() {
  const [projects, setProjects] = useState([])
  const [rubric, setRubric] = useState(null)
  const [forms, setForms] = useState({})
  const [selectedProjectId, setSelectedProjectId] = useState('')
  const [loading, setLoading] = useState(true)
  const [savingProject, setSavingProject] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [mobileQueueOpen, setMobileQueueOpen] = useState(false)
  const [pairwiseMode, setPairwiseMode] = useState(false)
  const [pairwiseComparison, setPairwiseComparison] = useState(null)
  const [pairwiseProgress, setPairwiseProgress] = useState(null)
  const [pairwiseLoading, setPairwiseLoading] = useState(false)
  const [pairwiseSubmitting, setPairwiseSubmitting] = useState(false)
  const [pairwiseMessage, setPairwiseMessage] = useState('')
  const [pairwiseError, setPairwiseError] = useState('')

  async function loadPairwiseData() {
    setPairwiseLoading(true)
    setPairwiseError('')

    try {
      const [nextResponse, progressResponse] = await Promise.all([
        fetch('/api/judge/pairwise/next', {
          credentials: 'include',
        }),
        fetch('/api/judge/pairwise/progress', {
          credentials: 'include',
        }),
      ])

      if (!nextResponse.ok) {
        const body = await nextResponse.text()
        throw new Error(
          body || `Could not load pairwise comparison: ${nextResponse.status}`,
        )
      }

      if (!progressResponse.ok) {
        const body = await progressResponse.text()
        throw new Error(
          body || `Could not load pairwise progress: ${progressResponse.status}`,
        )
      }

      const nextData = await nextResponse.json()
      const progressData = await progressResponse.json()

      setPairwiseComparison(nextData)
      setPairwiseProgress(progressData)
    } catch (err) {
      setPairwiseError(err.message)
    } finally {
      setPairwiseLoading(false)
    }
  }

  async function enterPairwiseMode() {
    setPairwiseMode(true)
    setPairwiseMessage('')
    setPairwiseError('')
    await loadPairwiseData()
  }

  function leavePairwiseMode() {
    setPairwiseMode(false)
    setPairwiseMessage('')
    setPairwiseError('')
  }

  async function submitPairwiseComparison(winner) {
    if (
      !pairwiseComparison?.project_a?.id ||
      !pairwiseComparison?.project_b?.id ||
      !winner
    ) {
      return
    }

    setPairwiseSubmitting(true)
    setPairwiseMessage('')
    setPairwiseError('')

    try {
      const response = await fetch(
        '/api/judge/pairwise/comparisons',
        {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            project_a: pairwiseComparison.project_a.id,
            project_b: pairwiseComparison.project_b.id,
            winner,
          }),
        },
      )

      const responseText = await response.text()

      if (!response.ok) {
        throw new Error(
          responseText || 'Could not save pairwise comparison',
        )
      }

      setPairwiseMessage('Comparison recorded. Loading the next pair...')
      await loadPairwiseData()
    } catch (err) {
      setPairwiseError(err.message)
    } finally {
      setPairwiseSubmitting(false)
    }
  }

  async function loadJudgeData() {
    try {
      setError('')

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

      if (assignedProjects.length > 0) {
        setSelectedProjectId((current) => {
          if (
            current &&
            assignedProjects.some((project) => project.id === current)
          ) {
            return current
          }

          const firstPending = assignedProjects.find(
            (project) =>
              savedByProject[project.id]?.weighted_score === undefined,
          )

          return firstPending?.id ?? assignedProjects[0].id
        })
      }
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

    setMessage('')
    setError('')
  }

  function updateComment(projectId, value) {
    setForms((current) => ({
      ...current,
      [projectId]: {
        ...current[projectId],
        comment: value,
      },
    }))

    setMessage('')
    setError('')
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
        throw new Error(
          'Enter a score for every rubric criterion.',
        )
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

      const responseText = await response.text()

      if (!response.ok) {
        throw new Error(
          responseText || 'Could not save ballot',
        )
      }

      const saved = JSON.parse(responseText)

      setForms((current) => ({
        ...current,
        [projectId]: {
          ...current[projectId],
          criteria: saved.criteria,
          comment: saved.comment ?? '',
          weighted_score: saved.weighted_score,
        },
      }))

      setMessage(`Score saved for ${projectId}.`)
    } catch (err) {
      setError(err.message)
    } finally {
      setSavingProject('')
    }
  }

  const selectedProject = useMemo(
    () =>
      projects.find(
        (project) => project.id === selectedProjectId,
      ) ?? null,
    [projects, selectedProjectId],
  )

  const selectedForm =
    selectedProject && forms[selectedProject.id]
      ? forms[selectedProject.id]
      : null

  const evaluatedCount = projects.filter(
    (project) =>
      forms[project.id]?.weighted_score !== null &&
      forms[project.id]?.weighted_score !== undefined,
  ).length

  const pendingCount = Math.max(
    projects.length - evaluatedCount,
    0,
  )

  const localWeightedScore = useMemo(() => {
    if (!selectedForm || !rubric) {
      return null
    }

    let total = 0

    for (const criterion of rubric.criteria) {
      const score = Number(
        selectedForm.criteria[criterion.id],
      )

      if (!Number.isFinite(score)) {
        return null
      }

      const maxScore = Number(criterion.max_score)

      if (!maxScore) {
        return null
      }

      total +=
        (score / maxScore) *
        (Number(criterion.weight) / 100) *
        5
    }

    return total.toFixed(2)
  }, [selectedForm, rubric])

  function selectProject(projectId) {
    setSelectedProjectId(projectId)
    setMobileQueueOpen(false)
    setMessage('')
    setError('')
  }

  function moveProject(direction) {
    if (projects.length === 0) {
      return
    }

    const currentIndex = projects.findIndex(
      (project) => project.id === selectedProjectId,
    )

    if (currentIndex === -1) {
      setSelectedProjectId(projects[0].id)
      return
    }

    const nextIndex =
      direction === 'next'
        ? Math.min(currentIndex + 1, projects.length - 1)
        : Math.max(currentIndex - 1, 0)

    setSelectedProjectId(projects[nextIndex].id)
    setMessage('')
    setError('')
  }

  if (loading) {
    return (
      <section className="judge-console judge-console-state">
        <div className="judge-state-card">
          <Terminal size={18} />
          <span>Loading judge workspace...</span>
        </div>
      </section>
    )
  }

  if (error && !rubric) {
    return (
      <section className="judge-console judge-console-state">
        <div className="judge-state-card judge-state-error">
          <AlertCircle size={18} />
          <span>{error}</span>
        </div>
      </section>
    )
  }

  if (!selectedProject) {
    return (
      <section className="judge-console judge-console-state">
        <div className="judge-state-card">
          <Info size={18} />
          <span>
            No projects are currently assigned to this judge.
          </span>
        </div>
      </section>
    )
  }

  return (
    <section className="judge-console">
      {/* MOBILE / TOP HEADER */}
      <header className="judge-mobile-header">
        <div className="judge-brand">
          <div className="judge-brand-mark">
            <Terminal size={17} />
          </div>

          <div>
            <strong>DOGFOOD</strong>
            <span>JUDGE CONSOLE</span>
          </div>
        </div>

        <div className="judge-header-right">
          <span className="judge-mobile-status">
            ACTIVE EVALUATION
          </span>

          <CircleUserRound size={19} />
        </div>
      </header>

      {/* EVENT / ROLE BAR */}
      <div className="judge-meta-bar">
        <div className="judge-meta-left">
          <span className="judge-live-dot" />
          <span>LOCAL</span>
          <span className="judge-divider">/</span>
          <span>JUDGE EVALUATION</span>
        </div>

        <div className="judge-meta-right">
          <span className="judge-role-badge">
            ROLE: JUDGE
          </span>

          <button
            className="judge-mobile-menu"
            type="button"
            onClick={() =>
              setMobileQueueOpen((current) => !current)
            }
            aria-label="Toggle assigned queue"
          >
            {mobileQueueOpen ? (
              <X size={15} />
            ) : (
              <Menu size={15} />
            )}
          </button>
        </div>
      </div>

      {/* JUDGING MODE */}
      <div className="judge-mode-bar">
        <div>
          <span className="judge-eyebrow">JUDGING MODE</span>
          <strong>
            {pairwiseMode
              ? 'Pairwise comparison'
              : 'Weighted rubric'}
          </strong>
        </div>

        <div className="judge-mode-switch" role="tablist">
          <button
            type="button"
            className={
              !pairwiseMode
                ? 'judge-mode-button is-active'
                : 'judge-mode-button'
            }
            onClick={leavePairwiseMode}
          >
            Standard rubric
          </button>

          <button
            type="button"
            className={
              pairwiseMode
                ? 'judge-mode-button is-active'
                : 'judge-mode-button'
            }
            onClick={enterPairwiseMode}
            disabled={pairwiseLoading}
          >
            {pairwiseLoading ? 'Loading...' : 'Pairwise mode'}
          </button>
        </div>
      </div>

      {/* MOBILE QUEUE */}
      {mobileQueueOpen && (
        <div className="judge-mobile-queue">
          <div className="judge-mobile-queue-header">
            <span>ASSIGNED PROJECTS</span>
            <span>
              {evaluatedCount}/{projects.length}
            </span>
          </div>

          {projects.map((project) => {
            const saved =
              forms[project.id]?.weighted_score !== null &&
              forms[project.id]?.weighted_score !== undefined

            return (
              <button
                key={project.id}
                type="button"
                className={`judge-queue-item ${
                  project.id === selectedProject.id
                    ? 'is-active'
                    : ''
                }`}
                onClick={() => selectProject(project.id)}
              >
                <span className="judge-queue-id">
                  {project.id}
                </span>

                <strong>
                  {project.title}
                </strong>

                <span className={saved ? 'is-done' : ''}>
                  {saved ? 'EVALUATED' : 'PENDING'}
                </span>
              </button>
            )
          })}
        </div>
      )}

      {/* PAIRWISE WORKSPACE */}
      {pairwiseMode && (
        <PairwisePanel
          comparison={pairwiseComparison}
          progress={pairwiseProgress}
          loading={pairwiseLoading}
          submitting={pairwiseSubmitting}
          message={pairwiseMessage}
          error={pairwiseError}
          onChoose={submitPairwiseComparison}
          onBack={leavePairwiseMode}
          onReload={loadPairwiseData}
        />
      )}

      {/* MAIN WORKSPACE */}
      <div className={`judge-workspace ${pairwiseMode ? "judge-workspace-hidden" : ""}`}>
        {/* DESKTOP QUEUE */}
        <aside className="judge-queue">
          <div className="judge-panel-heading">
            <div>
              <span className="judge-eyebrow">
                ASSIGNED QUEUE
              </span>

              <h3>
                Evaluation Worklist
              </h3>
            </div>

            <span className="judge-count">
              {projects.length}
            </span>
          </div>

          <div className="judge-progress-strip">
            <span>
              COMPLETED
            </span>

            <strong>
              {evaluatedCount}/{projects.length}
            </strong>

            <span>
              {pendingCount} pending
            </span>
          </div>

          <div className="judge-queue-list">
            {projects.map((project) => {
              const form = forms[project.id]

              const saved =
                form?.weighted_score !== null &&
                form?.weighted_score !== undefined

              const isActive =
                project.id === selectedProject.id

              return (
                <button
                  key={project.id}
                  type="button"
                  className={`judge-queue-card ${
                    isActive ? 'is-active' : ''
                  }`}
                  onClick={() =>
                    selectProject(project.id)
                  }
                >
                  <div className="judge-queue-top">
                    <span>{project.id}</span>

                    <span
                      className={
                        saved
                          ? 'queue-status done'
                          : 'queue-status'
                      }
                    >
                      {saved
                        ? 'EVALUATED'
                        : isActive
                          ? 'IN PROGRESS'
                          : 'PENDING'}
                    </span>
                  </div>

                  <strong>
                    {project.title}
                  </strong>

                  <div className="judge-queue-bottom">
                    <span>
                      {project.team ?? 'Team'}
                    </span>

                    {saved ? (
                      <span>
                        {Number(
                          form.weighted_score,
                        ).toFixed(2)}
                        /5
                      </span>
                    ) : (
                      <span>
                        —
                      </span>
                    )}
                  </div>
                </button>
              )
            })}
          </div>
        </aside>

        {/* ACTIVE EVALUATION */}
        <main className="judge-evaluation">
          <div className="judge-evaluation-header">
            <div>
              <div className="judge-breadcrumb">
                <span>JUDGE</span>
                <span>/</span>
                <strong>ACTIVE EVALUATION</strong>
              </div>

              <div className="judge-title-row">
                <div>
                  <span className="judge-project-id">
                    {selectedProject.id}
                  </span>

                  <h1>
                    {selectedProject.title}
                  </h1>

                  <p>
                    {selectedProject.summary}
                  </p>
                </div>

                <div className="judge-score-box">
                  <span>
                    WEIGHTED SCORE
                  </span>

                  <strong>
                    {selectedForm?.weighted_score !==
                    null &&
                    selectedForm?.weighted_score !==
                      undefined
                      ? Number(
                          selectedForm.weighted_score,
                        ).toFixed(2)
                      : localWeightedScore ?? '—'}
                  </strong>

                  <small>
                    / 5.00
                  </small>
                </div>
              </div>

              <div className="judge-project-meta">
                {selectedProject.team && (
                  <span>
                    TEAM: {selectedProject.team}
                  </span>
                )}

                {selectedProject.track && (
                  <span>
                    TRACK: {selectedProject.track}
                  </span>
                )}

                {selectedProject.repo_url && (
                  <a
                    href={selectedProject.repo_url}
                    target="_blank"
                    rel="noreferrer"
                  >
                    <FileCode2 size={14} />
                    REPOSITORY
                    <ExternalLink size={12} />
                  </a>
                )}
              </div>
            </div>
          </div>

          {/* ISOLATION NOTICE */}
          <div className="judge-isolation">
            <div className="judge-isolation-icon">
              <LockKeyhole size={17} />
            </div>

            <div>
              <strong>
                JUDGE ISOLATION ACTIVE
              </strong>

              <p>
                This evaluation view is restricted to projects
                assigned to the authenticated judge. Peer judge
                ballots are not exposed through the judge API.
              </p>
            </div>

            <ShieldCheck size={18} />
          </div>

          {/* RUBRIC */}
          <div className="judge-section-title">
            <div>
              <span className="judge-eyebrow">
                EVALUATION MATRIX
              </span>

              <h2>
                Weighted Rubric
              </h2>
            </div>

            <span>
              {rubric.criteria.length} criteria
            </span>
          </div>

          <div className="judge-rubric-stack">
            {rubric.criteria.map((criterion) => {
              const currentValue =
                selectedForm?.criteria?.[criterion.id] ??
                ''

              const numericValue =
                Number(currentValue)

              const maxScore =
                Number(criterion.max_score)

              const weightedPreview =
                Number.isFinite(numericValue) &&
                maxScore > 0
                  ? (
                      (numericValue / maxScore) *
                      (Number(criterion.weight) / 100) *
                      5
                    ).toFixed(2)
                  : '—'

              const quickScores =
                Number.isInteger(maxScore) &&
                maxScore <= 10
                  ? Array.from(
                      { length: maxScore },
                      (_, index) => index + 1,
                    )
                  : []

              return (
                <article
                  className="judge-criterion"
                  key={criterion.id}
                >
                  <div className="judge-criterion-head">
                    <div>
                      <span className="judge-criterion-id">
                        {criterion.id}
                      </span>

                      <h3>
                        {criterion.name}
                      </h3>
                    </div>

                    <div className="judge-criterion-weight">
                      <span>
                        WEIGHT
                      </span>

                      <strong>
                        {criterion.weight}%
                      </strong>
                    </div>
                  </div>

                  <div className="judge-score-row">
                    <div className="judge-score-options">
                      {quickScores.map((score) => (
                        <button
                          key={score}
                          type="button"
                          className={
                            Number(currentValue) === score
                              ? 'judge-score-option is-selected'
                              : 'judge-score-option'
                          }
                          onClick={() =>
                            updateScore(
                              selectedProject.id,
                              criterion.id,
                              String(score),
                            )
                          }
                        >
                          {score}
                        </button>
                      ))}
                    </div>

                    <div className="judge-score-input-wrap">
                      <span>
                        SCORE
                      </span>

                      <input
                        type="number"
                        min="0"
                        max={criterion.max_score}
                        step="0.01"
                        value={currentValue}
                        onChange={(event) =>
                          updateScore(
                            selectedProject.id,
                            criterion.id,
                            event.target.value,
                          )
                        }
                        aria-label={`Score for ${criterion.name}`}
                      />
                    </div>
                  </div>

                  <div className="judge-criterion-footer">
                    <span>
                      Allowed: 0–{criterion.max_score}
                    </span>

                    <span>
                      Weighted contribution: {weightedPreview}
                    </span>
                  </div>
                </article>
              )
            })}
          </div>

          {/* COMMENT */}
          <section className="judge-comment-panel">
            <div className="judge-comment-header">
              <div>
                <span className="judge-eyebrow">
                  EVIDENCE / RATIONALE
                </span>

                <h2>
                  Judge Comment
                </h2>
              </div>

              <span>
                {selectedForm?.comment?.length ?? 0} chars
              </span>
            </div>

            <textarea
              value={selectedForm?.comment ?? ''}
              onChange={(event) =>
                updateComment(
                  selectedProject.id,
                  event.target.value,
                )
              }
              rows="5"
              placeholder="Record concise evidence supporting the scores."
            />
          </section>

          {/* FEEDBACK */}
          {message && (
            <div className="judge-feedback judge-feedback-success">
              <Check size={16} />
              <span>{message}</span>
            </div>
          )}

          {error && rubric && (
            <div className="judge-feedback judge-feedback-error">
              <AlertCircle size={16} />
              <span>{error}</span>
            </div>
          )}

          {/* ACTION BAR */}
          <footer className="judge-action-bar">
            <div className="judge-navigation-actions">
              <button
                type="button"
                className="judge-secondary-button"
                onClick={() => moveProject('previous')}
                disabled={
                  projects.findIndex(
                    (project) =>
                      project.id === selectedProject.id,
                  ) <= 0
                }
              >
                <ChevronLeft size={16} />
                Previous
              </button>

              <button
                type="button"
                className="judge-secondary-button"
                onClick={() => moveProject('next')}
                disabled={
                  projects.findIndex(
                    (project) =>
                      project.id === selectedProject.id,
                  ) >=
                  projects.length - 1
                }
              >
                Next
                <ChevronRight size={16} />
              </button>
            </div>

            <div className="judge-action-context">
              <span>ACTIVE EVALUATION</span>
              <strong>
                {projects.findIndex(
                  (project) =>
                    project.id === selectedProject.id,
                ) + 1}
              </strong>
              <span>/ {projects.length}</span>
            </div>

            <button
              type="button"
              className="judge-primary-button"
              disabled={savingProject === selectedProject.id}
              onClick={() =>
                saveBallot(selectedProject.id)
              }
            >
              {savingProject === selectedProject.id ? (
                <>
                  <Save size={16} />
                  Saving...
                </>
              ) : message &&
                message.includes(selectedProject.id) ? (
                <>
                  <Check size={16} />
                  Saved
                </>
              ) : (
                <>
                  <Check size={16} />
                  Save evaluation
                </>
              )}
            </button>
          </footer>
        </main>
      </div>
    </section>
  )
}