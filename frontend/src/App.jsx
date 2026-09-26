import { useEffect, useState } from 'react'
import { Users, Gavel, ClipboardList, ShieldCheck, ArrowRight, LogOut } from 'lucide-react'
import OrganizerDashboard from './OrganizerDashboard'
import JudgeDashboard from './JudgeDashboard'
import ParticipantDashboard from './ParticipantDashboard'

const ROLES = [
  { value: 'participant', label: 'Participant', desc: 'Submit and manage your project', icon: Users },
  { value: 'judge_a', label: 'Judge A', desc: 'Score assigned projects', icon: Gavel },
  { value: 'judge_b', label: 'Judge B', desc: 'Score assigned projects', icon: Gavel },
  { value: 'organizer', label: 'Organizer', desc: 'Manage the event & judging', icon: ClipboardList },
  { value: 'admin', label: 'Admin', desc: 'Full platform access', icon: ShieldCheck },
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

  async function loadPublishedResults() {
    try {
      const response = await fetch('/api/results/published')

      if (response.status === 404) {
        setPublishedResults(null)
        return
      }

      if (response.ok === false) {
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
      <main className="min-h-screen flex items-center justify-center text-slate-500">
        Loading portal...
      </main>
    )
  }

  return (
    <main className="min-h-screen bg-gradient-to-b from-slate-50 to-slate-100 px-6 py-8">
      <header className="max-w-5xl mx-auto flex items-center justify-between mb-8">
        <div>
          <p className="text-xs font-semibold tracking-[0.2em] text-slate-400 uppercase">
            DOGFOOD 2026
          </p>
          <h1 className="text-2xl font-bold text-slate-900 mt-1">Hackathon Portal</h1>
        </div>

        {user && (
          <button
            className="flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 transition-colors"
            onClick={logout}
          >
            <LogOut size={16} />
            Logout
          </button>
        )}
      </header>

      {!user ? (
        <section className="max-w-xl mx-auto rounded-2xl border border-slate-200 bg-white p-8 shadow-sm mb-8">
          <p className="text-xs font-semibold tracking-widest text-slate-400 uppercase">
            Access Portal
          </p>
          <h2 className="text-2xl font-semibold text-slate-900 mt-1">Choose your role</h2>
          <p className="text-sm text-slate-500 mt-2">
            Sign in as a participant, judge, organizer, or admin to access
            the corresponding portal features.
          </p>

          <div className="grid grid-cols-2 gap-3 mt-6">
            {ROLES.map((item) => {
              const Icon = item.icon
              const active = role === item.value
              return (
                <button
                  key={item.value}
                  onClick={() => setRole(item.value)}
                  className={`group flex flex-col items-start gap-2 rounded-xl border p-4 text-left transition-all
                    ${active
                      ? 'border-slate-900 bg-slate-900 text-white shadow-md'
                      : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:shadow-sm'
                    }`}
                >
                  <Icon size={18} className={active ? 'text-white' : 'text-slate-400 group-hover:text-slate-600'} />
                  <span className="text-sm font-semibold">{item.label}</span>
                  <span className={`text-xs ${active ? 'text-slate-300' : 'text-slate-400'}`}>
                    {item.desc}
                  </span>
                </button>
              )
            })}
          </div>

          <button
            className="mt-6 flex w-full items-center justify-center gap-2 rounded-xl bg-slate-900 py-3.5 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60 transition-colors"
            onClick={login}
            disabled={loginLoading}
          >
            {loginLoading ? 'Signing in...' : 'Continue'}
            {!loginLoading && <ArrowRight size={16} />}
          </button>

          {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
        </section>
      ) : (
        <>
          <section className="max-w-5xl mx-auto rounded-2xl border border-slate-200 bg-white p-6 shadow-sm mb-8">
            <div>
              <p className="text-xs font-semibold tracking-widest text-slate-400 uppercase">
                Signed In
              </p>
              <h2 className="text-xl font-semibold text-slate-900 mt-1">{formatRole(user.role)}</h2>
              <p className="text-sm text-slate-500 mt-1">
                Account: <strong className="text-slate-700">{user.id}</strong>
              </p>
            </div>
          </section>

          {(user.role === 'ORGANIZER' || user.role === 'ADMIN') && (
            <OrganizerDashboard />
          )}

          {(user.role === 'JUDGE_A' || user.role === 'JUDGE_B') && (
            <JudgeDashboard />
          )}

          {user.role === 'PARTICIPANT' && (
            <ParticipantDashboard user={user} />
          )}
        </>
      )}

      <section className="max-w-5xl mx-auto mt-8">
        <div className="flex items-center justify-between mb-4">
          <div>
            <p className="text-xs font-semibold tracking-widest text-slate-400 uppercase">
              Public Gallery
            </p>
            <h2 className="text-xl font-semibold text-slate-900 mt-1">Projects</h2>
          </div>

          <span className="text-sm text-slate-500">{projects.length} projects</span>
        </div>

        <div className="flex gap-3 mb-6">
          <input
            type="search"
            value={search}
            placeholder="Search projects, teams, or tracks..."
            onChange={(e) => setSearch(e.target.value)}
            className="flex-1 rounded-lg border border-slate-300 px-4 py-2.5 text-sm text-slate-700 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-slate-900"
          />

          <select
            value={trackFilter}
            onChange={(e) => setTrackFilter(e.target.value)}
            className="rounded-lg border border-slate-300 px-4 py-2.5 text-sm text-slate-700 bg-white focus:outline-none focus:ring-2 focus:ring-slate-900"
          >
            <option value="">All tracks</option>
            {tracks.map((track) => (
              <option key={track} value={track}>
                {track}
              </option>
            ))}
          </select>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {projects.length === 0 ? (
            <div className="col-span-full text-center text-sm text-slate-400 py-12">
              No projects match the current filters.
            </div>
          ) : (
            projects.map((project) => (
              <article
                key={project.id}
                className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm hover:shadow-md transition-shadow"
              >
                <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
                  <span>{project.track}</span>
                  <span>{project.id}</span>
                </div>

                <h3 className="text-base font-semibold text-slate-900">{project.title}</h3>
                <p className="text-sm text-slate-500 mt-1">{project.summary}</p>

                <div className="flex items-center justify-between mt-4 pt-4 border-t border-slate-100">
                  <span className="text-xs text-slate-400">{project.team}</span>

                  
                    <a
                  
                      href={project.repo_url}
                    target="_blank"
                    rel="noreferrer"
                    className="text-xs font-medium text-slate-900 hover:underline"
                  >
                    Repository →
                  </a>
                </div>
              </article>
            ))
          )}
        </div>

        {publishedResults?.results?.length > 0 && (
          <section className="mt-10">
            <div className="flex items-center justify-between mb-4">
              <div>
                <p className="text-xs font-semibold tracking-widest text-slate-400 uppercase">
                  Published Results
                </p>
                <h2 className="text-xl font-semibold text-slate-900 mt-1">Final Rankings</h2>
              </div>
              <span className="text-sm text-slate-500">{publishedResults.results.length} ranked</span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {publishedResults.results.map((result) => (
                <article
                  key={result.project}
                  className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm"
                >
                  <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
                    <span>Rank #{result.rank}</span>
                    <span>{result.project}</span>
                  </div>

                  <h3 className="text-base font-semibold text-slate-900">{result.title}</h3>
                  <p className="text-sm text-slate-500 mt-1">
                    Normalized score: {Number(result.normalized_average).toFixed(2)}
                  </p>

                  <div className="mt-4 pt-4 border-t border-slate-100 text-xs text-slate-400">
                    Published {new Date(publishedResults.published_at).toLocaleString()}
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