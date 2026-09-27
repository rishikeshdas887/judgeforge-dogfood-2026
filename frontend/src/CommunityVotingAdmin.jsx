import { useEffect, useState } from "react"

function toLocalInput(value) {
  if (!value) return ""
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ""
  const offset = date.getTimezoneOffset()
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 16)
}

function CommunityVotingAdmin() {
  const [config, setConfig] = useState(null)
  const [enabled, setEnabled] = useState(false)
  const [access, setAccess] = useState("OPEN_LINK")
  const [open, setOpen] = useState("")
  const [close, setClose] = useState("")
  const [allowedEmails, setAllowedEmails] = useState("")
  const [message, setMessage] = useState("")
  const [error, setError] = useState("")
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    loadConfig()
  }, [])

  async function loadConfig() {
    setError("")
    try {
      const response = await fetch("/api/organizer/community-voting", { credentials: "include" })
      const data = await response.json()
      if (!response.ok) throw new Error(data?.message || data || "Could not load community voting configuration")
      setConfig(data)
      setEnabled(Boolean(data.enabled))
      setAccess(data.access || "OPEN_LINK")
      setOpen(toLocalInput(data.open))
      setClose(toLocalInput(data.close))
      setAllowedEmails((data.allowed_emails || []).join("\\n"))
    } catch (err) {
      setError(err.message)
    }
  }

  async function saveConfig() {
    setSaving(true)
    setMessage("")
    setError("")
    try {
      const response = await fetch("/api/organizer/community-voting", {
        method: "PUT",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          enabled,
          access,
          open: new Date(open).toISOString(),
          close: new Date(close).toISOString(),
          allowed_emails: allowedEmails.split(/\\r?\\n/).map((item) => item.trim()).filter(Boolean),
        }),
      })
      const data = await response.json()
      if (!response.ok) throw new Error(data?.message || data || "Could not save community voting configuration")
      setConfig(data)
      setMessage("Community voting configuration saved.")
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  if (error && !config) {
    return (
      <section className="rounded-xl border border-red-200 bg-red-50 p-6 text-sm text-red-600">
        {error}
      </section>
    )
  }

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-start justify-between gap-4 mb-5">
        <div>
          <p className="text-xs font-semibold tracking-widest text-slate-400 uppercase">Community</p>
          <h3 className="text-base font-semibold text-slate-900 mt-1">Community Voting Control</h3>
          <p className="text-sm text-slate-500 mt-1">Configure access, voting window, and email allow-list.</p>
        </div>
        {config?.status && <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600">{config.status}</span>}
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <label className="flex items-center gap-3 rounded-lg border border-slate-200 p-3">
          <input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />
          <span className="text-sm font-medium text-slate-700">Enable community voting</span>
        </label>

        <div>
          <label className="block text-xs text-slate-400 mb-1">Access mode</label>
          <select value={access} onChange={(event) => setAccess(event.target.value)} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
            <option value="OPEN_LINK">Open link</option>
            <option value="EMAIL_GATED">Email gated</option>
            <option value="AUTHENTICATED">Authenticated users</option>
          </select>
        </div>

        <div>
          <label className="block text-xs text-slate-400 mb-1">Voting opens</label>
          <input type="datetime-local" value={open} onChange={(event) => setOpen(event.target.value)} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        </div>

        <div>
          <label className="block text-xs text-slate-400 mb-1">Voting closes</label>
          <input type="datetime-local" value={close} onChange={(event) => setClose(event.target.value)} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        </div>

        {access === "EMAIL_GATED" && (
          <div className="md:col-span-2">
            <label className="block text-xs text-slate-400 mb-1">Allowed emails</label>
            <textarea value={allowedEmails} onChange={(event) => setAllowedEmails(event.target.value)} rows="5" placeholder="one@example.com" className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
            <p className="text-xs text-slate-400 mt-1">One email per line.</p>
          </div>
        )}
      </div>

      {message && <p className="mt-4 rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{message}</p>}
      {error && <p className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}

      <div className="flex items-center justify-between mt-5 pt-5 border-t border-slate-100">
        <span className="text-sm text-slate-500">{config?.results_public ? "Results are currently public." : "Results remain hidden until voting closes."}</span>
        <button onClick={saveConfig} disabled={saving || !open || !close} className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">
          {saving ? "Saving..." : "Save voting settings"}
        </button>
      </div>
    </section>
  )
}

export default CommunityVotingAdmin
