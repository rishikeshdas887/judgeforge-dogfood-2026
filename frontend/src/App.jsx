import { useEffect, useState } from 'react'
import OrganizerDashboard from './OrganizerDashboard'
import JudgeDashboard from './JudgeDashboard'
import ParticipantDashboard from './ParticipantDashboard'
import './App.css'

const ROLES = [
  { value: 'participant', label: 'Participant' },
  { value: 'judge_a', label: 'Judge A' },
  { value: 'judge_b', label: 'Judge B' },
  { value: 'organizer', label: 'Organizer' },
]

function App() {
  const [user, setUser] = useState(null)
  const [projects, setProjects] = useState([])
  const [tracks, setTracks] = useState([])
  const [search, setSearch] = useState('')
  const [trackFilter, setTrackFilter] = useState('')
  const [role, setRole] = useState('participant')
  const [loading, setLoading] = useState(true)
  const [loginLoading, setLoginLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    checkSession()
  }, [])

  useEffect(() => {
    loadProjects()
  }, [search, trackFilter])

  async function checkSession() {
    try {
      const response = await fetch('/api/auth/me')

      if (response.ok) {
        const data = await response.json()
        setUser(data)
      }
    } catch {
      setError('Could not connect to the portal.')
    } finally {
      setLoading(false)
    }
  }

  async function login() {
    setLoginLoading(true)
    setError('')

    try {
      const response = await fetch(
        `/api/auth/login?role=${encodeURIComponent(role)}`,
        {
          method: 'POST',
          credentials: 'include',
        },
      )

      if (!response.ok) {
        throw new Error('Login failed')
      }

      await checkSession()
    } catch (err) {
      setError(err.message)
    } finally {
      setLoginLoading(false)
    }
  }

  async function logout() {
    await fetch('/api/auth/logout', {
      method: 'POST',
      credentials: 'include',
    })

    setUser(null)
  }

  async function loadProjects() {
    try {
      const params = new URLSearchParams()

      if (search.trim()) {
        params.set('search', search.trim())
      }

      if (trackFilter) {
        params.set('track', trackFilter)
      }

      const query = params.toString()
      const response = await fetch(
        query ? `/projects?${query}` : '/projects',
      )

      if (!response.ok) {
        throw new Error(`Request failed: ${response.status}`)
      }

      const data = await response.json()
      setProjects(data)

      setTracks((current) => {
        const values = new Set(current)

        for (const project of data) {
          if (project.track) {
            values.add(project.track)
          }
        }

        return [...values].sort()
      })
    } catch {
      setError('Could not load projects.')
    }
  }

  if (loading) {
    return <main className="center-state">Loading portal...</main>
  }

  return (
    <main className="app">
      <header className="topbar">
        <div>
          <p className="eyebrow">DOGFOOD 2026</p>
          <h1>Hackathon Portal</h1>
        </div>

        {user && (
          <button className="secondary-button" onClick={logout}>
            Logout
          </button>
        )}
      </header>

      {!user ? (
        <section className="login-card">
          <p className="eyebrow">ACCESS PORTAL</p>
          <h2>Choose your role</h2>
          <p>
            Sign in as a participant, judge, or organizer to access the
            corresponding portal features.
          </p>

          <div className="role-grid">
            {ROLES.map((item) => (
              <button
                key={item.value}
                className={
                  role === item.value
                    ? 'role-button selected'
                    : 'role-button'
                }
                onClick={() => setRole(item.value)}
              >
                {item.label}
              </button>
            ))}
          </div>

          <button
            className="primary-button"
            onClick={login}
            disabled={loginLoading}
          >
            {loginLoading ? 'Signing in...' : 'Continue'}
          </button>

          {error && <p className="error">{error}</p>}
        </section>
      ) : (
        <>
          <section className="welcome">
            <div>
              <p className="eyebrow">SIGNED IN</p>
              <h2>{formatRole(user.role)}</h2>
              <p>
                Account: <strong>{user.id}</strong>
              </p>
            </div>
          </section>

          {user.role === 'ORGANIZER' && <OrganizerDashboard />}

          {(user.role === 'JUDGE_A' || user.role === 'JUDGE_B') && (
            <JudgeDashboard />
          )}

          {user.role === 'PARTICIPANT' && (
            <ParticipantDashboard user={user} />
          )}
        </>
      )}

      <section className="gallery-section">
        <div className="section-header">
          <div>
            <p className="eyebrow">PUBLIC GALLERY</p>
            <h2>Projects</h2>
          </div>

          <span>{projects.length} projects</span>
        </div>

        <div className="gallery-controls">
          <input
            type="search"
            value={search}
            placeholder="Search projects, teams, or tracks..."
            onChange={(e) => setSearch(e.target.value)}
          />

          <select
            value={trackFilter}
            onChange={(e) => setTrackFilter(e.target.value)}
          >
            <option value="">All tracks</option>
            {tracks.map((track) => (
              <option key={track} value={track}>
                {track}
              </option>
            ))}
          </select>
        </div>

        <div className="project-grid">
          {projects.length === 0 ? (
            <div className="empty-state">
              No projects match the current filters.
            </div>
          ) : (
            projects.map((project) => (
              <article className="project-card" key={project.id}>
                <div className="project-meta">
                  <span>{project.track}</span>
                  <span>{project.id}</span>
                </div>

                <h3>{project.title}</h3>
                <p>{project.summary}</p>

                <div className="project-footer">
                  <span>{project.team}</span>

                  <a
                    href={project.repo_url}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Repository →
                  </a>
                </div>
              </article>
            ))
          )}
        </div>
      </section>
    </main>
  )
}

function formatRole(role) {
  return role
    .replace('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase())
}

export default App
