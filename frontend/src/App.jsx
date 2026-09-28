import { useEffect, useState } from 'react'
import {
  ArrowRight,
  ClipboardList,
  Gavel,
  LogOut,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Users,
} from 'lucide-react'
import OrganizerDashboard from './OrganizerDashboard'
import JudgeDashboard from './JudgeDashboard'
import ParticipantDashboard from './ParticipantDashboard'
import CommunityVoting from './CommunityVoting'

const ROLES = [
  {
    value: 'participant',
    label: 'Participant',
    desc: 'Submit and manage your project',
    icon: Users,
  },
  {
    value: 'judge_a',
    label: 'Judge A',
    desc: 'Score assigned projects',
    icon: Gavel,
  },
  {
    value: 'judge_b',
    label: 'Judge B',
    desc: 'Score assigned projects',
    icon: Gavel,
  },
  {
    value: 'organizer',
    label: 'Organizer',
    desc: 'Manage the event & judging',
    icon: ClipboardList,
  },
  {
    value: 'admin',
    label: 'Admin',
    desc: 'Full platform access',
    icon: ShieldCheck,
  },
]

function App() {
  const [user, setUser] = useState(null)
  const [projects, setProjects] = useState([])
  const [publishedResults, setPublishedResults] = useState(null)
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

  useEffect(() => {
    loadPublishedResults()
  }, [])

  async function checkSession() {
    try {
      const response = await fetch('/api/auth/me', {
        credentials: 'include',
      })

      if (response.ok) {
        const data = await response.json()
        setUser(data)
        setRole(data.role?.toLowerCase() ?? 'participant')
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
    setError('')
  }

  async function loadPublishedResults() {
    try {
      const response = await fetch('/api/results/published')

      if (response.status === 404) {
        setPublishedResults(null)
        return
      }

      if (!response.ok) {
        throw new Error('Could not load published results')
      }

      setPublishedResults(await response.json())
    } catch {
      setPublishedResults(null)
    }
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
    return (
      <main className="dogfood-loading">
        <span className="dogfood-loading-mark">DF</span>
        <span>INITIALIZING PORTAL...</span>
      </main>
    )
  }

  return (
    <main className="dogfood-app">
      <header className="dogfood-header">
        <div className="dogfood-brand">
          <div className="dogfood-brand-mark">DF</div>

          <div>
            <p>DOGFOOD 2026</p>
            <h1>Hackathon Portal</h1>
          </div>
        </div>

        <div className="dogfood-header-actions">
          {user ? (
            <>
              <div className="dogfood-session-chip">
                <span className="dogfood-session-dot" />
                <span>
                  {formatRole(user.role)}
                </span>
                <strong>{user.id}</strong>
              </div>

              <button
                className="dogfood-logout"
                type="button"
                onClick={logout}
              >
                <LogOut size={15} />
                Logout
              </button>
            </>
          ) : (
            <span className="dogfood-header-status">
              LOCAL / OFFLINE
            </span>
          )}
        </div>
      </header>

      {!user ? (
        <section className="dogfood-access">
          <div className="dogfood-access-copy">
            <span className="dogfood-kicker">ACCESS PORTAL</span>
            <h2>Choose your operating role.</h2>
            <p>
              Enter the local hackathon portal as a participant,
              judge, organizer, or administrator.
            </p>
          </div>

          <div className="dogfood-role-grid">
            {ROLES.map((item) => {
              const Icon = item.icon
              const active = role === item.value

              return (
                <button
                  key={item.value}
                  type="button"
                  onClick={() => setRole(item.value)}
                  className={
                    active
                      ? 'dogfood-role is-active'
                      : 'dogfood-role'
                  }
                >
                  <Icon size={17} />
                  <span className="dogfood-role-name">
                    {item.label}
                  </span>
                  <span className="dogfood-role-desc">
                    {item.desc}
                  </span>
                </button>
              )
            })}
          </div>

          <button
            className="dogfood-enter-button"
            type="button"
            onClick={login}
            disabled={loginLoading}
          >
            {loginLoading ? 'AUTHENTICATING...' : 'ENTER PORTAL'}
            {!loginLoading && <ArrowRight size={16} />}
          </button>

          {error && (
            <div className="dogfood-global-error">
              {error}
            </div>
          )}
        </section>
      ) : (
        <>
          {!(user.role === 'JUDGE_A' || user.role === 'JUDGE_B') && (

          <section className="dogfood-context-bar">
            <div>
              <span>ACTIVE SESSION</span>
              <strong>{formatRole(user.role)}</strong>
              <em>{user.id}</em>
            </div>

            <div className="dogfood-context-state">
              <span />
              LOCAL SYSTEM
            </div>
          </section>
          )}

          {(user.role === 'ORGANIZER' ||
            user.role === 'ADMIN') && (
            <OrganizerDashboard />
          )}

          {(user.role === 'JUDGE_A' ||
            user.role === 'JUDGE_B') && (
            <JudgeDashboard />
          )}

          {user.role === 'PARTICIPANT' && (
            <ParticipantDashboard user={user} />
          )}
        </>
      )}

      {(!user || (user.role !== 'JUDGE_A' && user.role !== 'JUDGE_B')) && (
        <section className="dogfood-community-stage" aria-label="Community Voting">
          <div className="dogfood-community-transition" aria-hidden="true">
            <span className="dogfood-community-transition-line" />
            <span className="dogfood-community-transition-label">
              PUBLIC COMMUNITY LAYER
            </span>
            <span className="dogfood-community-transition-line" />
          </div>

          <CommunityVoting />
        </section>
      )}

      <section className="dogfood-gallery">
        <div className="dogfood-section-heading">
          <div>
            <span className="dogfood-kicker">PUBLIC GALLERY</span>
            <h2>Submitted Projects</h2>
          </div>

          <strong>{projects.length} projects</strong>
        </div>

        <div className="dogfood-gallery-tools">
          <div className="dogfood-search">
            <Search size={16} />
            <input
              type="search"
              value={search}
              placeholder="Search projects, teams, or tracks..."
              onChange={(event) =>
                setSearch(event.target.value)
              }
            />
          </div>

          <label className="dogfood-filter">
            <SlidersHorizontal size={15} />
            <select
              value={trackFilter}
              onChange={(event) =>
                setTrackFilter(event.target.value)
              }
            >
              <option value="">All tracks</option>

              {tracks.map((track) => (
                <option key={track} value={track}>
                  {track}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="dogfood-project-grid">
          {projects.length === 0 ? (
            <div className="dogfood-empty">
              No projects match the current filters.
            </div>
          ) : (
            projects.map((project) => (
              <article
                key={project.id}
                className="dogfood-project-card"
              >
                <div className="dogfood-project-topline">
                  <span>{project.id}</span>
                  <span>{project.track}</span>
                </div>

                <h3>{project.title}</h3>

                <p>{project.summary}</p>

                <div className="dogfood-project-footer">
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

        {publishedResults?.results?.length > 0 && (
          <section className="dogfood-results">
            <div className="dogfood-section-heading">
              <div>
                <span className="dogfood-kicker">
                  PUBLISHED RESULTS
                </span>
                <h2>Final Rankings</h2>
              </div>

              <strong>
                {publishedResults.results.length} ranked
              </strong>
            </div>

            <div className="dogfood-project-grid">
              {publishedResults.results.map((result) => (
                <article
                  key={result.project}
                  className="dogfood-project-card"
                >
                  <div className="dogfood-project-topline">
                    <span>RANK #{result.rank}</span>
                    <span>{result.project}</span>
                  </div>

                  <h3>{result.title}</h3>

                  <p>
                    Normalized score:{' '}
                    {Number(
                      result.normalized_average,
                    ).toFixed(2)}
                  </p>

                  <div className="dogfood-project-footer">
                    <span>
                      Published{' '}
                      {new Date(
                        publishedResults.published_at,
                      ).toLocaleString()}
                    </span>
                  </div>
                </article>
              ))}
            </div>
          </section>
        )}
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
