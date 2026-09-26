import { useEffect, useState } from 'react'

function toLocalInput(iso) {
  if (!iso) return ''
  const date = new Date(iso)
  const offset = date.getTimezoneOffset()
  return new Date(date.getTime() - offset * 60000)
    .toISOString()
    .slice(0, 16)
}

function toIso(value) {
  return value ? new Date(value).toISOString() : ''
}

function createTrack() {
  return {
    id: `trk_${Date.now()}`,
    name: '',
  }
}

function createPrize() {
  return {
    id: `prize_${Date.now()}`,
    name: '',
  }
}

export default function EventConfiguration() {
  const [event, setEvent] = useState(null)
  const [saving, setSaving] = useState(false)
  const [creating, setCreating] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  async function loadEvent() {
    const response = await fetch('/api/organizer/event', {
      credentials: 'include',
    })

    if (!response.ok) {
      throw new Error('Could not load event configuration')
    }

    const data = await response.json()

    setEvent({
      ...data,
      submissions_open: toLocalInput(data.submissions_open),
      submissions_close: toLocalInput(data.submissions_close),
      tracks: Array.isArray(data.tracks) ? data.tracks : [],
      prizes: Array.isArray(data.prizes) ? data.prizes : [],
    })
  }

  useEffect(() => {
    loadEvent().catch((err) => setError(err.message))
  }, [])

  function update(field, value) {
    setEvent((current) => ({
      ...current,
      [field]: value,
    }))
  }

  function updateTrack(index, field, value) {
    setEvent((current) => ({
      ...current,
      tracks: current.tracks.map((track, trackIndex) =>
        trackIndex === index
          ? { ...track, [field]: value }
          : track,
      ),
    }))
  }

  function updatePrize(index, field, value) {
    setEvent((current) => ({
      ...current,
      prizes: current.prizes.map((prize, prizeIndex) =>
        prizeIndex === index
          ? { ...prize, [field]: value }
          : prize,
      ),
    }))
  }

  function addTrack() {
    update('tracks', [...event.tracks, createTrack()])
  }

  function removeTrack(index) {
    if (event.tracks.length <= 1) return
    update(
      'tracks',
      event.tracks.filter((_, trackIndex) => trackIndex !== index),
    )
  }

  function addPrize() {
    update('prizes', [...event.prizes, createPrize()])
  }

  function removePrize(index) {
    update(
      'prizes',
      event.prizes.filter((_, prizeIndex) => prizeIndex !== index),
    )
  }

  function buildPayload(source) {
    return {
      ...source,
      submissions_open: toIso(source.submissions_open),
      submissions_close: toIso(source.submissions_close),
      tracks: source.tracks.map((track) => ({
        id: track.id.trim(),
        name: track.name.trim(),
      })),
      prizes: source.prizes.map((prize) => ({
        id: prize.id.trim(),
        name: prize.name.trim(),
      })),
    }
  }

  async function saveEvent() {
    setSaving(true)
    setError('')
    setMessage('')

    try {
      const payload = buildPayload(event)

      const response = await fetch('/api/organizer/event', {
        method: 'PUT',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
      })

      const text = await response.text()

      if (!response.ok) {
        throw new Error(text || 'Could not save event')
      }

      const saved = JSON.parse(text)

      setEvent({
        ...saved,
        submissions_open: toLocalInput(saved.submissions_open),
        submissions_close: toLocalInput(saved.submissions_close),
      })

      setMessage('Event configuration saved.')
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function createEvent() {
    setCreating(true)
    setError('')
    setMessage('')

    try {
      const payload = {
        id: '',
        name: event.name,
        submissions_open: toIso(event.submissions_open),
        submissions_close: toIso(event.submissions_close),
        tracks: event.tracks,
        prizes: event.prizes,
      }

      const response = await fetch('/api/organizer/events', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
      })

      const text = await response.text()

      if (!response.ok) {
        throw new Error(text || 'Could not create event')
      }

      const created = JSON.parse(text)

      setEvent({
        ...created,
        submissions_open: toLocalInput(created.submissions_open),
        submissions_close: toLocalInput(created.submissions_close),
      })

      setMessage(`Created event ${created.id}.`)
    } catch (err) {
      setError(err.message)
    } finally {
      setCreating(false)
    }
  }

  if (!event) {
    return (
      <section className="dashboard-card">
        <h3>Event Configuration</h3>
        <p>{error || 'Loading event...'}</p>
      </section>
    )
  }

  return (
    <section className="dashboard-card event-config-card">
      <div className="card-heading">
        <div>
          <p className="eyebrow">EVENT MANAGEMENT</p>
          <h3>Event Configuration</h3>
          <p>
            Configure the event name, submission window, tracks, and prizes.
          </p>
        </div>

        <strong>{event.id}</strong>
      </div>

      <div className="event-form-grid">
        <label>
          Event name
          <input
            value={event.name}
            onChange={(e) => update('name', e.target.value)}
          />
        </label>

        <label>
          Submissions open
          <input
            type="datetime-local"
            value={event.submissions_open}
            onChange={(e) =>
              update('submissions_open', e.target.value)
            }
          />
        </label>

        <label>
          Submissions close
          <input
            type="datetime-local"
            value={event.submissions_close}
            onChange={(e) =>
              update('submissions_close', e.target.value)
            }
          />
        </label>
      </div>

      <div className="event-list-header">
        <div>
          <h4>Tracks</h4>
          <p>At least one track is required.</p>
        </div>

        <button
          className="secondary-button"
          type="button"
          onClick={addTrack}
        >
          Add track
        </button>
      </div>

      <div className="event-item-list">
        {event.tracks.map((track, index) => (
          <div className="event-item-row" key={track.id}>
            <input
              value={track.id}
              onChange={(e) =>
                updateTrack(index, 'id', e.target.value)
              }
              placeholder="Track ID"
            />

            <input
              value={track.name}
              onChange={(e) =>
                updateTrack(index, 'name', e.target.value)
              }
              placeholder="Track name"
            />

            <button
              className="secondary-button"
              type="button"
              disabled={event.tracks.length <= 1}
              onClick={() => removeTrack(index)}
            >
              Remove
            </button>
          </div>
        ))}
      </div>

      <div className="event-list-header">
        <div>
          <h4>Prizes</h4>
          <p>Each prize requires an ID and name.</p>
        </div>

        <button
          className="secondary-button"
          type="button"
          onClick={addPrize}
        >
          Add prize
        </button>
      </div>

      <div className="event-item-list">
        {event.prizes.map((prize, index) => (
          <div className="event-item-row" key={prize.id}>
            <input
              value={prize.id}
              onChange={(e) =>
                updatePrize(index, 'id', e.target.value)
              }
              placeholder="Prize ID"
            />

            <input
              value={prize.name}
              onChange={(e) =>
                updatePrize(index, 'name', e.target.value)
              }
              placeholder="Prize name"
            />

            <button
              className="secondary-button"
              type="button"
              onClick={() => removePrize(index)}
            >
              Remove
            </button>
          </div>
        ))}

        {event.prizes.length === 0 && (
          <p className="muted">No prizes configured.</p>
        )}
      </div>

      <div className="card-actions">
        <button
          className="primary-button compact-button"
          disabled={saving}
          onClick={saveEvent}
        >
          {saving ? 'Saving...' : 'Save configuration'}
        </button>

        <button
          className="secondary-button"
          disabled={creating}
          onClick={createEvent}
        >
          {creating ? 'Creating...' : 'Create as new event'}
        </button>
      </div>

      {message && <p className="form-message">{message}</p>}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
