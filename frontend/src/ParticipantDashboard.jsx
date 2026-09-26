import { useEffect, useMemo, useState } from 'react'

const blankForm = {
  id: '',
  team: '',
  track: '',
  name: '',
  tagline: '',
  long_description: '',
  repository_url: '',
  live_link: '',
  demo_video_url: '',
  thumbnail: '',
  tech_tags: '',
}

function projectToForm(project) {
  return {
    id: project.id || '',
    team: project.team || '',
    track: project.track || '',
    name: project.name || project.title || '',
    tagline: project.tagline || '',
    long_description: project.long_description || project.summary || '',
    repository_url: project.repository_url || project.repo_url || '',
    live_link: project.live_link || '',
    demo_video_url: project.demo_video_url || '',
    thumbnail: project.thumbnail || '',
    tech_tags: Array.isArray(project.tech_tags)
      ? project.tech_tags.join(', ')
      : '',
  }
}

function formToPayload(form) {
  return {
    team: form.team,
    track: form.track,
    name: form.name,
    tagline: form.tagline,
    long_description: form.long_description,
    repository_url: form.repository_url,
    live_link: form.live_link,
    demo_video_url: form.demo_video_url,
    thumbnail: form.thumbnail,
    tech_tags: form.tech_tags
      .split(',')
      .map((tag) => tag.trim())
      .filter(Boolean),
  }
}

export default function ParticipantDashboard({ user }) {
  const [event, setEvent] = useState(null)
  const [teams, setTeams] = useState([])
  const [projects, setProjects] = useState([])
  const [form, setForm] = useState(blankForm)
  const [teamName, setTeamName] = useState('')
  const [inviteToken, setInviteToken] = useState('')
  const [generatedInvite, setGeneratedInvite] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  const myTeams = useMemo(
    () => teams.filter((team) => team.members?.includes(user.id)),
    [teams, user.id],
  )

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === form.id) || null,
    [projects, form.id],
  )

  const submissionsOpen = useMemo(() => {
    if (!event?.submissions_open || !event?.submissions_close) return false
    const now = Date.now()
    return (
      now >= Date.parse(event.submissions_open) &&
      now < Date.parse(event.submissions_close)
    )
  }, [event])

  useEffect(() => {
    loadWorkspace()
  }, [])

  async function api(url, options = {}) {
    const response = await fetch(url, {
      credentials: 'include',
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers || {}),
      },
    })

    const text = await response.text()

    let data = text
    try {
      data = text ? JSON.parse(text) : null
    } catch {
      // Plain-text responses are valid.
    }

    if (!response.ok) {
      throw new Error(
        typeof data === 'string'
          ? data
          : data?.message || `Request failed: ${response.status}`,
      )
    }

    return data
  }

  async function loadWorkspace() {
    setLoading(true)
    setError('')

    try {
      const [eventData, teamData, projectData] = await Promise.all([
        api('/api/participant/event'),
        api('/api/teams'),
        api('/api/projects/mine'),
      ])

      setEvent(eventData)
      setTeams(teamData)
      setProjects(projectData)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  function startDraft() {
    setError('')
    setMessage('')

    if (!submissionsOpen) {
      setError('Submissions are currently closed.')
      return
    }

    if (myTeams.length === 0) {
      setError('Create or join a team before creating a project.')
      return
    }

    setForm({
      ...blankForm,
      team: myTeams[0].id,
      track: event?.tracks?.[0]?.id || '',
    })
  }

  function editDraft(project) {
    setError('')
    setMessage('')
    setForm(projectToForm(project))
  }

  async function createTeam(e) {
    e.preventDefault()
    setError('')
    setMessage('')

    try {
      const team = await api('/api/teams', {
        method: 'POST',
        body: JSON.stringify({ name: teamName }),
      })

      setTeams((current) => [...current, team])
      setTeamName('')
      setMessage(`Created team: ${team.name}`)
    } catch (err) {
      setError(err.message)
    }
  }

  async function acceptInvite(e) {
    e.preventDefault()
    setError('')
    setMessage('')

    try {
      const team = await api(
        `/api/teams/invites/${encodeURIComponent(inviteToken)}/accept`,
        { method: 'POST' },
      )

      setTeams((current) =>
        current.some((item) => item.id === team.id)
          ? current.map((item) => (item.id === team.id ? team : item))
          : [...current, team],
      )

      setInviteToken('')
      setMessage(`Joined team: ${team.name}`)
    } catch (err) {
      setError(err.message)
    }
  }

  async function generateInvite(teamId) {
    setError('')
    setMessage('')

    try {
      const invite = await api(`/api/teams/${teamId}/invites`, {
        method: 'POST',
      })

      setGeneratedInvite(invite.token)
      setMessage('Invite generated.')
    } catch (err) {
      setError(err.message)
    }
  }

  async function saveDraft(e) {
    e.preventDefault()
    setSaving(true)
    setError('')
    setMessage('')

    try {
      if (!form.team || !form.track || !form.name.trim()) {
        throw new Error('Team, track, and project name are required.')
      }

      let project

      if (!form.id) {
        project = await api('/api/projects', {
          method: 'POST',
          body: JSON.stringify({
            team: form.team,
            track: form.track,
            name: form.name,
          }),
        })
      }

      const projectId = form.id || project.id

      project = await api(`/api/projects/${projectId}`, {
        method: 'PUT',
        body: JSON.stringify(formToPayload(form)),
      })

      setProjects((current) =>
        current.some((item) => item.id === project.id)
          ? current.map((item) =>
              item.id === project.id ? project : item,
            )
          : [...current, project],
      )

      setForm(projectToForm(project))
      setMessage('Draft saved.')
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function submitProject() {
    setSaving(true)
    setError('')
    setMessage('')

    try {
      const updated = await api(`/api/projects/${form.id}`, {
        method: 'PUT',
        body: JSON.stringify(formToPayload(form)),
      })

      const submitted = await api(`/api/projects/${form.id}/submit`, {
        method: 'POST',
      })

      setProjects((current) =>
        current.map((item) =>
          item.id === submitted.id ? submitted : item,
        ),
      )

      setForm(projectToForm(submitted))
      setMessage(`Submitted: ${submitted.name}`)
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <section className="participant-section">
        <div className="panel">Loading participant workspace...</div>
      </section>
    )
  }

  return (
    <section className="participant-section">
      <div className="participant-grid">
        <div className="panel">
          <p className="eyebrow">TEAM WORKSPACE</p>
          <div className="section-header">
            <h2>{event?.name || 'Current Event'}</h2>
            <span
              className={submissionsOpen ? 'status-open' : 'status-closed'}
            >
              {submissionsOpen ? 'Submissions open' : 'Submissions closed'}
            </span>
          </div>

          <div className="team-list">
            {myTeams.length === 0 && (
              <p className="muted">You are not on a team yet.</p>
            )}

            {myTeams.map((team) => (
              <article className="team-card" key={team.id}>
                <div>
                  <strong>{team.name}</strong>
                  <p>{team.members?.length || 0} member(s)</p>
                </div>

                <button
                  className="secondary-button"
                  onClick={() => generateInvite(team.id)}
                >
                  Generate invite
                </button>
              </article>
            ))}
          </div>

          {generatedInvite && (
            <div className="invite-box">
              <span>Invite token</span>
              <code>{generatedInvite}</code>
            </div>
          )}

          <form className="inline-form" onSubmit={createTeam}>
            <input
              value={teamName}
              onChange={(e) => setTeamName(e.target.value)}
              placeholder="New team name"
            />
            <button className="primary-button" type="submit">
              Create team
            </button>
          </form>

          <form className="inline-form" onSubmit={acceptInvite}>
            <input
              value={inviteToken}
              onChange={(e) => setInviteToken(e.target.value)}
              placeholder="Paste invite token"
            />
            <button className="secondary-button" type="submit">
              Accept invite
            </button>
          </form>
        </div>

        <div className="panel">
          <div className="section-header">
            <div>
              <p className="eyebrow">SUBMISSIONS</p>
              <h2>My projects</h2>
            </div>

            <button className="primary-button compact" onClick={startDraft}>
              New draft
            </button>
          </div>

          <div className="submission-list">
            {projects.length === 0 && (
              <p className="muted">No projects yet.</p>
            )}

            {projects.map((project) => (
              <article className="submission-card" key={project.id}>
                <div>
                  <strong>{project.name || project.title}</strong>
                  <p>
                    {project.status} · {project.track}
                  </p>
                </div>

                {project.status === 'DRAFT' && (
                  <button
                    className="secondary-button"
                    onClick={() => editDraft(project)}
                  >
                    Edit
                  </button>
                )}
              </article>
            ))}
          </div>
        </div>
      </div>

      {(form.team || form.id) && (
        <form className="panel submission-form" onSubmit={saveDraft}>
          <p className="eyebrow">PROJECT FORM</p>
          <div className="section-header">
            <h2>{selectedProject ? 'Edit draft' : 'New draft'}</h2>
            {selectedProject && <span>{selectedProject.status}</span>}
          </div>

          <div className="form-grid">
            <label>
              Team
              <select
                value={form.team}
                disabled={Boolean(form.id)}
                onChange={(e) =>
                  setForm({ ...form, team: e.target.value })
                }
              >
                {myTeams.map((team) => (
                  <option key={team.id} value={team.id}>
                    {team.name}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Track
              <select
                value={form.track}
                onChange={(e) =>
                  setForm({ ...form, track: e.target.value })
                }
              >
                {event?.tracks?.map((track) => (
                  <option key={track.id} value={track.id}>
                    {track.name}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Project name *
              <input
                required
                value={form.name}
                onChange={(e) =>
                  setForm({ ...form, name: e.target.value })
                }
              />
            </label>

            <label>
              Tagline
              <input
                value={form.tagline}
                onChange={(e) =>
                  setForm({ ...form, tagline: e.target.value })
                }
              />
            </label>

            <label className="form-wide">
              Long description *
              <textarea
                rows="6"
                value={form.long_description}
                onChange={(e) =>
                  setForm({
                    ...form,
                    long_description: e.target.value,
                  })
                }
              />
            </label>

            <label>
              Repository URL *
              <input
                value={form.repository_url}
                placeholder="https://github.com/..."
                onChange={(e) =>
                  setForm({
                    ...form,
                    repository_url: e.target.value,
                  })
                }
              />
            </label>

            <label>
              Live link
              <input
                value={form.live_link}
                onChange={(e) =>
                  setForm({ ...form, live_link: e.target.value })
                }
              />
            </label>

            <label>
              Demo video URL
              <input
                value={form.demo_video_url}
                onChange={(e) =>
                  setForm({
                    ...form,
                    demo_video_url: e.target.value,
                  })
                }
              />
            </label>

            <label>
              Thumbnail URL
              <input
                value={form.thumbnail}
                onChange={(e) =>
                  setForm({ ...form, thumbnail: e.target.value })
                }
              />
            </label>

            <label className="form-wide">
              Tech tags
              <input
                value={form.tech_tags}
                placeholder="Java, Spring Boot, React"
                onChange={(e) =>
                  setForm({ ...form, tech_tags: e.target.value })
                }
              />
            </label>
          </div>

          <div className="form-actions">
            <button
              className="primary-button"
              type="submit"
              disabled={saving}
            >
              {saving ? 'Saving...' : 'Save draft'}
            </button>

            {form.id && selectedProject?.status === 'DRAFT' && (
              <button
                className="secondary-button"
                type="button"
                disabled={saving || !submissionsOpen}
                onClick={submitProject}
              >
                Submit project
              </button>
            )}
          </div>
        </form>
      )}

      {(error || message) && (
        <div className={error ? 'portal-message error' : 'portal-message'}>
          {error || message}
        </div>
      )}
    </section>
  )
}
