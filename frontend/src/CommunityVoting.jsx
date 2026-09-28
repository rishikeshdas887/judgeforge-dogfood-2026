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
  if (Number.isNaN(date.getTime())) return value
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
    if (!text) return null
    try {
      return JSON.parse(text)
    } catch {
      return text
    }
  }

  async function loadConfig() {
    setLoading(true)
    setError("")
    try {
      const response = await fetch("/api/community/voting/config")
      const data = await readBody(response)
      if (!response.ok) {
        throw new Error(typeof data === "string" ? data : "Could not load voting configuration")
      }
      setConfig(data)
      if (data.enabled) {
        await loadBallot(data.access === "EMAIL_GATED" ? email : "")
      }
    } catch (err) {
      setError(err.message || "Could not load community voting")
    } finally {
      setLoading(false)
    }
  }

  async function loadBallot(emailValue = "") {
    setError("")
    setMessage("")
    const query = emailValue.trim() ? `?email=${encodeURIComponent(emailValue.trim())}` : ""
    const response = await fetch(`/api/community/voting/ballot${query}`, { credentials: "include" })
    const data = await readBody(response)
    if (!response.ok) {
      throw new Error(typeof data === "string" ? data : "Could not load voting ballot")
    }
    setBallot(data)
    await loadComments(data.projects || [])
    await loadResults()
  }

  async function loadComments(projects) {
    const entries = {}
    await Promise.all(
      projects.map(async (project) => {
        const response = await fetch(`/api/community/comments?project=${encodeURIComponent(project.id)}`)
        const data = await readBody(response)
        if (response.ok && Array.isArray(data)) entries[project.id] = data
      }),
    )
    setComments(entries)
  }

  async function loadResults() {
    const response = await fetch("/api/community/voting/results", { credentials: "include" })
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
      const body = { project_id: projectId }
      if (config?.access === "EMAIL_GATED") body.email = email.trim()
      const response = await fetch("/api/community/voting/vote", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      })
      const data = await readBody(response)
      if (!response.ok) {
        throw new Error(typeof data === "string" ? data : "Vote could not be recorded")
      }
      setMessage("Your community vote has been recorded.")
      setBallot((current) => (current ? { ...current, already_voted: true } : current))
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
      if (config?.access === "EMAIL_GATED") body.email = email.trim()
      const response = await fetch("/api/community/comments", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      })
      const data = await readBody(response)
      if (!response.ok) {
        throw new Error(typeof data === "string" ? data : "Comment could not be posted")
      }
      setCommentText((current) => ({ ...current, [projectId]: "" }))
      setMessage("Comment posted.")
      const refreshed = await fetch(`/api/community/comments?project=${encodeURIComponent(projectId)}`)
      const refreshedData = await readBody(refreshed)
      if (refreshed.ok && Array.isArray(refreshedData)) {
        setComments((current) => ({ ...current, [projectId]: refreshedData }))
      }
    } catch (err) {
      setError(err.message || "Comment could not be posted")
    } finally {
      setBusyProject("")
    }
  }

  if (loading) {
    return (
      <section className="community-console" aria-busy="true">
        <div className="community-shell">
          <div className="community-loading">
            <div className="community-skeleton community-skeleton-kicker" />
            <div className="community-skeleton community-skeleton-title" />
            <div className="community-skeleton community-skeleton-copy" />
            <div className="community-skeleton-card">
              <div className="community-skeleton community-skeleton-line" />
              <div className="community-skeleton community-skeleton-line short" />
              <div className="community-skeleton community-skeleton-action" />
            </div>
            <div className="community-skeleton-card">
              <div className="community-skeleton community-skeleton-line" />
              <div className="community-skeleton community-skeleton-line medium" />
              <div className="community-skeleton community-skeleton-action" />
            </div>
          </div>
        </div>
      </section>
    )
  }

  const enabled = Boolean(config?.enabled)
  const hasBallot = Boolean(ballot)
  const projects = ballot?.projects || []
  const isEmailGated = config?.access === "EMAIL_GATED"

  return (
    <section className="community-console">
      <div className="community-shell">
        <header className="community-header">
          <div className="community-header-main">
            <div className="community-eyebrow-row">
              <span className="community-eyebrow">COMMUNITY VOTING</span>
              {enabled && (
                <span className={`community-status ${config.status === "OPEN" ? "is-open" : config.status === "CLOSED" ? "is-closed" : "is-upcoming"}`}>
                  <span className="community-status-dot" />
                  {config.status || "ACTIVE"}
                </span>
              )}
            </div>

            <div className="community-title-row">
              <div>
                <h2>Community review console</h2>
                <p>Review submitted projects, cast one community vote, and leave public feedback.</p>
              </div>
              {enabled && (
                <div className="community-access-badge">
                  <span>ACCESS</span>
                  <strong>{config.access}</strong>
                </div>
              )}
            </div>
          </div>

          <div className="community-event-meta">
            <div>
              <span>EVENT</span>
              <strong>{ballot?.event_id || "—"}</strong>
            </div>
            <div>
              <span>WINDOW</span>
              <strong>{config?.open ? `${formatDate(config.open)} → ${formatDate(config.close)}` : "—"}</strong>
            </div>
          </div>
        </header>

        {!enabled && (
          <div className="community-state-card is-muted">
            <div className="community-state-icon"><AlertCircle size={18} /></div>
            <div>
              <span className="community-state-label">VOTING DISABLED</span>
              <strong>Community voting is currently unavailable.</strong>
              <p>Check back when the organizer opens the voting window.</p>
            </div>
          </div>
        )}

        {enabled && isEmailGated && (
          <section className="community-identity-panel">
            <div className="community-panel-icon"><ShieldCheck size={18} /></div>
            <div className="community-panel-copy">
              <span className="community-panel-kicker">EMAIL-GATED ACCESS</span>
              <strong>Enter your voting email</strong>
              <p>The backend will use your email for this gated ballot.</p>
            </div>
            <div className="community-email-form">
              <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" placeholder="you@example.com" autoComplete="email" aria-label="Voting email" />
              <button type="button" onClick={() => loadBallot(email)} disabled={!email.trim()} className="community-primary-button">
                Load ballot
              </button>
            </div>
          </section>
        )}

        {message && (
          <div className="community-feedback is-success" role="status">
            <CheckCircle2 size={17} /><span>{message}</span>
          </div>
        )}

        {error && (
          <div className="community-feedback is-error" role="alert">
            <AlertCircle size={17} /><span>{error}</span>
          </div>
        )}

        {enabled && hasBallot && (
          <>
            <div className="community-info-strip">
              <div className="community-info-item">
                <span>ASSIGNED PROJECTS</span>
                <strong>{projects.length}</strong>
              </div>
              <div className="community-info-divider" />
              <div className="community-info-copy">
                <strong>Randomized ballot order</strong>
                <span>Projects are presented in randomized ballot order.</span>
              </div>
              {ballot.already_voted && (
                <div className="community-voted-badge"><Check size={14} />VOTE RECORDED</div>
              )}
            </div>

            <div className="community-section-heading">
              <div>
                <span className="community-section-kicker">PROJECT QUEUE</span>
                <h3>Review submitted work</h3>
              </div>
              <span className="community-section-count">{projects.length} {projects.length === 1 ? "PROJECT" : "PROJECTS"}</span>
            </div>

            {projects.length === 0 && (
              <div className="community-state-card is-muted">
                <div className="community-state-icon"><AlertCircle size={18} /></div>
                <div>
                  <span className="community-state-label">EMPTY QUEUE</span>
                  <strong>No projects are available in this ballot.</strong>
                  <p>The current event does not have projects to review.</p>
                </div>
              </div>
            )}

            <div className="community-project-list">
              {projects.map((project, index) => {
                const projectComments = comments[project.id] || []
                const commentBusy = busyProject === `comment:${project.id}`

                return (
                  <article key={project.id} className="community-project-card">
                    <div className="community-project-topline">
                      <span className="community-project-index">#{String(index + 1).padStart(2, "0")}</span>
                      <span className="community-project-id">{project.id}</span>
                      {project.track && <span className="community-project-track">{project.track}</span>}
                    </div>

                    <div className="community-project-head">
                      <div className="community-project-copy">
                        <h4>{project.title}</h4>
                        <p>{project.summary || "No project summary was provided."}</p>
                      </div>

                      {!ballot.already_voted ? (
                        <button type="button" onClick={() => vote(project.id)} disabled={busyProject === project.id} className="community-primary-button community-vote-button">
                          {busyProject === project.id ? <><Loader2 size={15} className="is-spinning" />Recording</> : "Vote"}
                        </button>
                      ) : (
                        <div className="community-voted-project-state"><CheckCircle2 size={15} />Ballot submitted</div>
                      )}
                    </div>

                    <div className="community-project-meta">
                      <span><strong>TEAM</strong>{project.team || "—"}</span>
                      <span><strong>TRACK</strong>{project.track || "—"}</span>
                      {project.repo_url && (
                        <a href={project.repo_url} target="_blank" rel="noreferrer" className="community-repo-link">
                          Repository <ExternalLink size={13} />
                        </a>
                      )}
                    </div>

                    <div className="community-comment-panel">
                      <div className="community-comment-header">
                        <div>
                          <span className="community-section-kicker">COMMUNITY FEEDBACK</span>
                          <h5><MessageSquare size={15} />Comments</h5>
                        </div>
                        <span>{projectComments.length}</span>
                      </div>

                      {projectComments.length > 0 ? (
                        <div className="community-comment-list">
                          {projectComments.map((comment) => (
                            <div key={comment.id} className="community-comment">
                              <div className="community-comment-author">{comment.display_name}</div>
                              <p>{comment.body}</p>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <div className="community-comment-empty">No comments yet.</div>
                      )}

                      <div className="community-comment-form">
                        <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="Your name" autoComplete="name" aria-label="Your name" />
                        <input value={commentText[project.id] || ""} onChange={(event) => setCommentText((current) => ({ ...current, [project.id]: event.target.value }))} placeholder="Leave a constructive comment..." aria-label={`Comment on ${project.title}`} />
                        <button type="button" onClick={() => submitComment(project.id)} disabled={commentBusy} className="community-secondary-button">
                          {commentBusy ? <Loader2 size={14} className="is-spinning" /> : <MessageSquare size={14} />} Comment
                        </button>
                      </div>
                    </div>
                  </article>
                )
              })}
            </div>

            {ballot.already_voted && (
              <div className="community-state-card is-success">
                <div className="community-state-icon"><CheckCircle2 size={18} /></div>
                <div>
                  <span className="community-state-label">BALLOT LOCKED</span>
                  <strong>Your vote has already been recorded.</strong>
                  <p>The ballot endpoint reports that this participant has already voted for the event.</p>
                </div>
              </div>
            )}
          </>
        )}

        {results?.results?.length > 0 && (
          <section className="community-results">
            <div className="community-section-heading">
              <div>
                <span className="community-section-kicker">PUBLIC RESULTS</span>
                <h3>Community vote totals</h3>
              </div>
              <span className="community-section-count">{results.total_votes ?? 0} VOTES</span>
            </div>

            <div className="community-results-table">
              {results.results.map((row, index) => (
                <div key={row.project} className="community-result-row">
                  <div className="community-result-rank">{String(index + 1).padStart(2, "0")}</div>
                  <div className="community-result-main"><strong>{row.title}</strong><span>{row.project}</span></div>
                  <div className="community-result-bar"><span style={{ width: `${Math.max(0, Math.min(100, Number(row.percentage) || 0))}%` }} /></div>
                  <div className="community-result-value"><strong>{row.votes}</strong><span>{Number(row.percentage).toFixed(1)}%</span></div>
                </div>
              ))}
            </div>
          </section>
        )}

        {enabled && !results && ballot && (
          <div className="community-results-hidden">
            <span>RESULTS</span>
            <strong>Community totals are hidden while voting is open.</strong>
          </div>
        )}
      </div>
    </section>
  )
}

export default CommunityVoting
