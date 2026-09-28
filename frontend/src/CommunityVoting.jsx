import { useEffect, useState } from "react"
import {
  AlertCircle,
  Check,
  CheckCircle2,
  ExternalLink,
  Loader2,
  MessageSquare,
  ShieldCheck,
} from "lucide-react"

function formatDate(value) {
  if (!value) return "—"

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString([], {
    dateStyle: "medium",
    timeStyle: "short",
  })
}

function CommunityVoting() {
  const [config, setConfig] = useState(null)
  const [ballot, setBallot] = useState(null)
  const [results, setResults] = useState(null)
  const [comments, setComments] = useState({})
  const [email, setEmail] = useState("")
  const [displayName, setDisplayName] = useState("")
  const [commentText, setCommentText] = useState({})
  const [loading, setLoading] = useState(true)
  const [busyProject, setBusyProject] = useState("")
  const [message, setMessage] = useState("")
  const [error, setError] = useState("")

  useEffect(() => {
    loadConfig()
  }, [])

  async function readBody(response) {
    const text = await response.text()

    if (!text) {
      return null
    }

    try {
      return JSON.parse(text)
    } catch {
      return text
    }
  }

  async function loadConfig() {
    setLoading(true)
    setError("")
    setMessage("")

    try {
      const response = await fetch("/api/community/voting/config")
      const data = await readBody(response)

      if (!response.ok) {
        throw new Error(
          typeof data === "string"
            ? data
            : "Could not load voting configuration",
        )
      }

      setConfig(data)

      if (!data.enabled) {
        return
      }

      if (data.status === "CLOSED") {
        await loadResults()
        return
      }

      if (data.status !== "OPEN") {
        return
      }

      if (data.access === "EMAIL_GATED") {
        return
      }

      await loadBallot("")
    } catch (err) {
      setError(err.message || "Could not load community voting")
    } finally {
      setLoading(false)
    }
  }

  async function loadBallot(emailValue = "") {
    setError("")
    setMessage("")

    if (config?.access === "EMAIL_GATED" && !emailValue.trim()) {
      return
    }

    const query = emailValue.trim()
      ? `?email=${encodeURIComponent(emailValue.trim())}`
      : ""

    const response = await fetch(
      `/api/community/voting/ballot${query}`,
      { credentials: "include" },
    )

    const data = await readBody(response)

    if (!response.ok) {
      throw new Error(
        typeof data === "string"
          ? data
          : "Could not load voting ballot",
      )
    }

    setBallot(data)

    await loadComments(data.projects || [])
    await loadResults()
  }

  async function loadComments(projects) {
    const entries = {}

    await Promise.all(
      projects.map(async (project) => {
        const response = await fetch(
          `/api/community/comments?project=${encodeURIComponent(project.id)}`,
        )

        const data = await readBody(response)

        if (response.ok && Array.isArray(data)) {
          entries[project.id] = data
        }
      }),
    )

    setComments(entries)
  }

  async function loadResults() {
    const response = await fetch(
      "/api/community/voting/results",
      { credentials: "include" },
    )

    if (!response.ok) {
      setResults(null)
      return
    }

    const data = await readBody(response)
    setResults(data)
  }

  async function vote(projectId) {
    setBusyProject(projectId)
    setMessage("")
    setError("")

    try {
      const body = {
        project_id: projectId,
      }

      if (config?.access === "EMAIL_GATED") {
        body.email = email.trim()
      }

      const response = await fetch(
        "/api/community/voting/vote",
        {
          method: "POST",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(body),
        },
      )

      const data = await readBody(response)

      if (!response.ok) {
        throw new Error(
          typeof data === "string"
            ? data
            : "Vote could not be recorded",
        )
      }

      setMessage("Community vote recorded successfully.")

      setBallot((current) =>
        current
          ? {
              ...current,
              already_voted: true,
            }
          : current,
      )
    } catch (err) {
      setError(err.message || "Vote could not be recorded")
    } finally {
      setBusyProject("")
    }
  }

  async function submitComment(projectId) {
    const text = (commentText[projectId] || "").trim()

    if (!displayName.trim() || !text) {
      setError("Enter your display name and comment first.")
      return
    }

    setBusyProject(`comment:${projectId}`)
    setMessage("")
    setError("")

    try {
      const body = {
        project_id: projectId,
        display_name: displayName.trim(),
        body: text,
      }

      if (config?.access === "EMAIL_GATED") {
        body.email = email.trim()
      }

      const response = await fetch(
        "/api/community/comments",
        {
          method: "POST",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(body),
        },
      )

      const data = await readBody(response)

      if (!response.ok) {
        throw new Error(
          typeof data === "string"
            ? data
            : "Comment could not be posted",
        )
      }

      setCommentText((current) => ({
        ...current,
        [projectId]: "",
      }))

      setMessage("Comment posted.")

      const refreshed = await fetch(
        `/api/community/comments?project=${encodeURIComponent(projectId)}`,
      )

      const refreshedData = await readBody(refreshed)

      if (refreshed.ok && Array.isArray(refreshedData)) {
        setComments((current) => ({
          ...current,
          [projectId]: refreshedData,
        }))
      }
    } catch (err) {
      setError(err.message || "Comment could not be posted")
    } finally {
      setBusyProject("")
    }
  }

  if (loading) {
    return (
      <section
        className="community-console"
        aria-busy="true"
      >
        <div className="community-shell">
          <div className="community-loading">
            <span className="community-loading-kicker">
              COMMUNITY_VOTING
            </span>

            <strong>INITIALIZING REVIEW CONSOLE</strong>

            <div className="community-loading-grid">
              <div />
              <div />
              <div />
            </div>
          </div>
        </div>
      </section>
    )
  }

  const enabled = Boolean(config?.enabled)
  const isOpen = config?.status === "OPEN"
  const isClosed = config?.status === "CLOSED"
  const isUpcoming = config?.status === "UPCOMING"
  const hasBallot = Boolean(ballot)
  const projects = ballot?.projects || []
  const isEmailGated = config?.access === "EMAIL_GATED"

  return (
    <section className="community-console">
      <div className="community-shell">

        <header className="community-header">
          <div className="community-header-top">
            <div className="community-breadcrumb">
              <span>SURFACE</span>
              <strong>COMMUNITY_VOTING</strong>
              <span>//</span>
              <span>PUBLIC_SIGNAL</span>
            </div>

            <div className="community-status-group">
              {enabled && (
                <span
                  className={`community-status ${
                    isOpen
                      ? "is-open"
                      : isClosed
                        ? "is-closed"
                        : "is-upcoming"
                  }`}
                >
                  <span className="community-status-dot" />
                  {config.status || "ACTIVE"}
                </span>
              )}

              {enabled && (
                <span className="community-access-token">
                  ACCESS: <strong>{config.access}</strong>
                </span>
              )}
            </div>
          </div>

          <div className="community-title-row">
            <div>
              <span className="community-kicker">
                DOGFOOD // COMMUNITY_VOTING
              </span>

              <h2>Community Review Console</h2>

              <p>
                Review submitted projects, cast one community vote,
                and leave constructive project feedback.
              </p>
            </div>
          </div>

          <div className="community-event-bar">
            <div>
              <span>EVENT</span>
              <strong>{ballot?.event_id || "CURRENT EVENT"}</strong>
            </div>

            <div>
              <span>OPEN</span>
              <strong>{formatDate(config?.open)}</strong>
            </div>

            <div>
              <span>CLOSE</span>
              <strong>{formatDate(config?.close)}</strong>
            </div>

            <div className="community-event-state">
              <span>WINDOW</span>
              <strong>
                {isOpen
                  ? "VOTING ACTIVE"
                  : isClosed
                    ? "VOTING CLOSED"
                    : isUpcoming
                      ? "NOT YET OPEN"
                      : "UNAVAILABLE"}
              </strong>
            </div>
          </div>
        </header>

        {!enabled && (
          <div className="community-state-card is-muted">
            <div className="community-state-icon">
              <AlertCircle size={18} />
            </div>

            <div>
              <span className="community-state-label">
                VOTING DISABLED
              </span>

              <strong>
                Community voting is currently unavailable.
              </strong>

              <p>
                The organizer has not enabled the community voting surface.
              </p>
            </div>
          </div>
        )}

        {enabled && isUpcoming && (
          <div className="community-state-card is-muted">
            <div className="community-state-icon">
              <AlertCircle size={18} />
            </div>

            <div>
              <span className="community-state-label">
                WINDOW NOT OPEN
              </span>

              <strong>
                Community voting has not opened yet.
              </strong>

              <p>
                Check the event window above for the configured opening time.
              </p>
            </div>
          </div>
        )}

        {enabled && isOpen && isEmailGated && !hasBallot && (
          <section className="community-identity-panel">
            <div className="community-panel-icon">
              <ShieldCheck size={18} />
            </div>

            <div className="community-panel-copy">
              <span className="community-panel-kicker">
                EMAIL-GATED ACCESS
              </span>

              <strong>Verify voter identity</strong>

              <p>
                Enter the registered event email to load the ballot.
                The backend enforces the voting rules.
              </p>
            </div>

            <div className="community-email-form">
              <input
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                type="email"
                placeholder="voter@example.com"
                autoComplete="email"
                aria-label="Voting email"
              />

              <button
                type="button"
                onClick={() => loadBallot(email)}
                disabled={!email.trim()}
                className="community-primary-button"
              >
                VERIFY & LOAD
              </button>
            </div>
          </section>
        )}

        {message && (
          <div
            className="community-feedback is-success"
            role="status"
          >
            <CheckCircle2 size={17} />
            <span>{message}</span>
          </div>
        )}

        {error && (
          <div
            className="community-feedback is-error"
            role="alert"
          >
            <AlertCircle size={17} />
            <span>{error}</span>
          </div>
        )}

        {enabled && isClosed && results?.results?.length > 0 && (
          <section className="community-results">
            <div className="community-results-head">
              <div>
                <span className="community-section-kicker">
                  PUBLIC RESULTS
                </span>

                <h3>Community vote totals</h3>
              </div>

              <span className="community-section-count">
                {results.total_votes ?? 0} VOTES
              </span>
            </div>

            <div className="community-results-table">
              {results.results.map((row, index) => (
                <div
                  key={row.project}
                  className="community-result-row"
                >
                  <div className="community-result-rank">
                    {String(index + 1).padStart(2, "0")}
                  </div>

                  <div className="community-result-main">
                    <strong>{row.title}</strong>
                    <span>{row.project}</span>
                  </div>

                  <div className="community-result-bar">
                    <span
                      style={{
                        width: `${Math.max(
                          0,
                          Math.min(
                            100,
                            Number(row.percentage) || 0,
                          ),
                        )}%`,
                      }}
                    />
                  </div>

                  <div className="community-result-value">
                    <strong>{row.votes}</strong>
                    <span>
                      {Number(row.percentage).toFixed(1)}%
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}

        {enabled && isOpen && hasBallot && (
          <>
            <div className="community-info-strip">
              <div className="community-info-item">
                <span>PROJECTS</span>
                <strong>{projects.length}</strong>
              </div>

              <div className="community-info-divider" />

              <div className="community-info-copy">
                <strong>
                  SERVER-SIDE RANDOMIZED BALLOT ORDER
                </strong>

                <span>
                  Project ordering is supplied by the backend ballot.
                </span>
              </div>

              {ballot.already_voted && (
                <div className="community-voted-badge">
                  <Check size={14} />
                  BALLOT COMMITTED
                </div>
              )}
            </div>

            <div className="community-section-heading">
              <div>
                <span className="community-section-kicker">
                  PROJECT QUEUE
                </span>

                <h3>Review submitted work</h3>
              </div>

              <span className="community-section-count">
                {projects.length}{" "}
                {projects.length === 1
                  ? "PROJECT"
                  : "PROJECTS"}
              </span>
            </div>

            {projects.length === 0 && (
              <div className="community-state-card is-muted">
                <div className="community-state-icon">
                  <AlertCircle size={18} />
                </div>

                <div>
                  <span className="community-state-label">
                    EMPTY QUEUE
                  </span>

                  <strong>
                    No projects are available in this ballot.
                  </strong>

                  <p>
                    The current event has no submitted projects available
                    for this voting surface.
                  </p>
                </div>
              </div>
            )}

            <div className="community-project-list">
              {projects.map((project, index) => {
                const projectComments =
                  comments[project.id] || []

                const commentBusy =
                  busyProject === `comment:${project.id}`

                return (
                  <article
                    key={project.id}
                    className="community-project-card"
                  >
                    <div className="community-project-topline">
                      <div className="community-project-identifiers">
                        <span className="community-project-index">
                          #{String(index + 1).padStart(2, "0")}
                        </span>

                        <span className="community-divider">
                          /
                        </span>

                        <span className="community-project-id">
                          {project.id}
                        </span>
                      </div>

                      <div className="community-project-tags">
                        {project.track && (
                          <span>{project.track}</span>
                        )}

                        <span>
                          {ballot.already_voted
                            ? "VOTE_COMMITTED"
                            : "UNVOTED"}
                        </span>
                      </div>
                    </div>

                    <div className="community-project-body">
                      <div className="community-project-head">
                        <div className="community-project-copy">
                          <h4>{project.title}</h4>

                          <p>
                            {project.summary ||
                              "No project summary was provided."}
                          </p>
                        </div>

                        <div className="community-project-action">
                          {!ballot.already_voted ? (
                            <button
                              type="button"
                              onClick={() => vote(project.id)}
                              disabled={
                                busyProject === project.id
                              }
                              className="community-primary-button community-vote-button"
                            >
                              {busyProject === project.id ? (
                                <>
                                  <Loader2
                                    size={15}
                                    className="is-spinning"
                                  />
                                  RECORDING
                                </>
                              ) : (
                                <>
                                  <Check size={15} />
                                  CAST COMMUNITY VOTE
                                </>
                              )}
                            </button>
                          ) : (
                            <div className="community-voted-project-state">
                              <CheckCircle2 size={15} />
                              BALLOT COMMITTED
                            </div>
                          )}
                        </div>
                      </div>

                      <div className="community-project-meta">
                        <span>
                          <strong>TEAM</strong>
                          {project.team || "—"}
                        </span>

                        <span>
                          <strong>TRACK</strong>
                          {project.track || "—"}
                        </span>

                        {project.repo_url && (
                          <a
                            href={project.repo_url}
                            target="_blank"
                            rel="noreferrer"
                            className="community-repo-link"
                          >
                            REPOSITORY
                            <ExternalLink size={13} />
                          </a>
                        )}
                      </div>

                      <div className="community-comment-panel">
                        <div className="community-comment-header">
                          <div>
                            <span className="community-section-kicker">
                              COMMUNITY FEEDBACK
                            </span>

                            <h5>
                              <MessageSquare size={15} />
                              COMMENTS
                            </h5>
                          </div>

                          <span>
                            {projectComments.length}
                          </span>
                        </div>

                        {projectComments.length > 0 ? (
                          <div className="community-comment-list">
                            {projectComments.map((comment) => (
                              <div
                                key={comment.id}
                                className="community-comment"
                              >
                                <div className="community-comment-author">
                                  {comment.display_name}
                                </div>

                                <p>{comment.body}</p>
                              </div>
                            ))}
                          </div>
                        ) : (
                          <div className="community-comment-empty">
                            No community comments yet.
                          </div>
                        )}

                        <div className="community-comment-form">
                          <input
                            value={displayName}
                            onChange={(event) =>
                              setDisplayName(event.target.value)
                            }
                            placeholder="Your name"
                            autoComplete="name"
                            aria-label="Your name"
                          />

                          <input
                            value={commentText[project.id] || ""}
                            onChange={(event) =>
                              setCommentText((current) => ({
                                ...current,
                                [project.id]:
                                  event.target.value,
                              }))
                            }
                            placeholder="Leave constructive feedback..."
                            aria-label={`Comment on ${project.title}`}
                          />

                          <button
                            type="button"
                            onClick={() =>
                              submitComment(project.id)
                            }
                            disabled={commentBusy}
                            className="community-secondary-button"
                          >
                            {commentBusy ? (
                              <Loader2
                                size={14}
                                className="is-spinning"
                              />
                            ) : (
                              <MessageSquare size={14} />
                            )}

                            POST COMMENT
                          </button>
                        </div>
                      </div>
                    </div>
                  </article>
                )
              })}
            </div>

            {ballot.already_voted && (
              <div className="community-state-card is-success">
                <div className="community-state-icon">
                  <CheckCircle2 size={18} />
                </div>

                <div>
                  <span className="community-state-label">
                    BALLOT LOCKED
                  </span>

                  <strong>
                    Your community vote has already been recorded.
                  </strong>

                  <p>
                    Additional votes are blocked by the backend.
                  </p>
                </div>
              </div>
            )}
          </>
        )}

        {enabled &&
          isOpen &&
          hasBallot &&
          !results && (
            <div className="community-results-hidden">
              <span>RESULTS</span>

              <strong>
                Community totals remain hidden while voting is open.
              </strong>
            </div>
          )}
      </div>
    </section>
  )
}

export default CommunityVoting
