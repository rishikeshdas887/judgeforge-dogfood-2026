import { useEffect, useState } from "react"

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
    try {
      const response = await fetch("/api/community/voting/config")
      const data = await readBody(response)
      if (!response.ok) throw new Error(typeof data === "string" ? data : "Could not load voting configuration")
      setConfig(data)
      if (data.enabled) {
        await loadBallot(data.access === "EMAIL_GATED" ? email : "")
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function loadBallot(emailValue = "") {
    setError("")
    const query = emailValue.trim() ? `?email=${encodeURIComponent(emailValue.trim())}` : ""
    const response = await fetch(`/api/community/voting/ballot${query}`, { credentials: "include" })
    const data = await readBody(response)
    if (!response.ok) throw new Error(typeof data === "string" ? data : "Could not load voting ballot")
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
      if (!response.ok) throw new Error(typeof data === "string" ? data : "Vote could not be recorded")
      setMessage("Your community vote has been recorded.")
      setBallot((current) => current ? { ...current, already_voted: true } : current)
    } catch (err) {
      setError(err.message)
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
      if (!response.ok) throw new Error(typeof data === "string" ? data : "Comment could not be posted")
      setCommentText((current) => ({ ...current, [projectId]: "" }))
      setMessage("Comment posted.")
      const refreshed = await fetch(`/api/community/comments?project=${encodeURIComponent(projectId)}`)
      const refreshedData = await readBody(refreshed)
      if (refreshed.ok && Array.isArray(refreshedData)) {
        setComments((current) => ({ ...current, [projectId]: refreshedData }))
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setBusyProject("")
    }
  }

  if (loading) return null

  return (
    <section className="max-w-5xl mx-auto mt-8 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-start justify-between gap-4 mb-6">
        <div>
          <p className="text-xs font-semibold tracking-widest text-slate-400 uppercase">Community</p>
          <h2 className="text-xl font-semibold text-slate-900 mt-1">Community Voting</h2>
          <p className="text-sm text-slate-500 mt-1">
            {config?.enabled ? "Support the projects you want the community to recognize." : "Community voting is currently disabled."}
          </p>
        </div>
        {config?.enabled && <span className="text-xs font-semibold rounded-full bg-slate-100 px-3 py-1 text-slate-600">{config.access}</span>}
      </div>

      {config?.access === "EMAIL_GATED" && config.enabled && (
        <div className="mb-6 rounded-xl border border-slate-200 bg-slate-50 p-4">
          <label className="block text-sm font-medium text-slate-700 mb-2">Voting email</label>
          <div className="flex gap-3">
            <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" placeholder="you@example.com" className="flex-1 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" />
            <button onClick={() => loadBallot(email)} className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white">Load ballot</button>
          </div>
        </div>
      )}

      {message && <p className="mb-4 rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{message}</p>}
      {error && <p className="mb-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}

      {ballot?.projects?.length > 0 && (
        <div className="space-y-4">
          {ballot.projects.map((project) => (
            <article key={project.id} className="rounded-xl border border-slate-200 p-5">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-xs font-medium text-slate-400">{project.track} · {project.team}</p>
                  <h3 className="text-base font-semibold text-slate-900 mt-1">{project.title}</h3>
                  <p className="text-sm text-slate-500 mt-2">{project.summary}</p>
                </div>
                {!ballot.already_voted && (
                  <button onClick={() => vote(project.id)} disabled={busyProject === project.id} className="shrink-0 rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">
                    {busyProject === project.id ? "Recording..." : "Vote"}
                  </button>
                )}
              </div>

              <div className="mt-5 border-t border-slate-100 pt-4">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-3">Comments</p>
                <div className="space-y-3">
                  {(comments[project.id] || []).map((comment) => (
                    <div key={comment.id} className="rounded-lg bg-slate-50 p-3">
                      <p className="text-xs font-semibold text-slate-700">{comment.display_name}</p>
                      <p className="text-sm text-slate-600 mt-1">{comment.body}</p>
                    </div>
                  ))}
                </div>
                <div className="mt-4 grid gap-2 sm:grid-cols-[180px_1fr_auto]">
                  <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="Your name" className="rounded-lg border border-slate-300 px-3 py-2 text-sm" />
                  <input value={commentText[project.id] || ""} onChange={(e) => setCommentText((current) => ({ ...current, [project.id]: e.target.value }))} placeholder="Leave a comment..." className="rounded-lg border border-slate-300 px-3 py-2 text-sm" />
                  <button onClick={() => submitComment(project.id)} disabled={busyProject === `comment:${project.id}`} className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 disabled:opacity-50">
                    Comment
                  </button>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}

      {ballot?.already_voted && <p className="mt-4 text-sm text-slate-500">Your vote has already been recorded for this event.</p>}

      {results?.results?.length > 0 && (
        <div className="mt-8 border-t border-slate-200 pt-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <p className="text-xs font-semibold tracking-widest text-slate-400 uppercase">Community Results</p>
              <h3 className="text-lg font-semibold text-slate-900 mt-1">Vote Totals</h3>
            </div>
            <span className="text-sm text-slate-500">{results.total_votes} votes</span>
          </div>
          <div className="space-y-3">
            {results.results.map((row) => (
              <div key={row.project} className="rounded-lg border border-slate-200 p-3">
                <div className="flex items-center justify-between gap-3">
                  <span className="text-sm font-medium text-slate-800">{row.title}</span>
                  <span className="text-sm text-slate-500">{row.votes} · {Number(row.percentage).toFixed(1)}%</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </section>
  )
}

export default CommunityVoting
