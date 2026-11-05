import { useEffect, useState } from 'react'
import axios from 'axios'
import { calendarApi, type Appointment, type AppointmentInput, type Member, type Recurrence } from '../api/calendar'
import { Dialog } from '../components/Dialog'
import { MemberBadge } from './MemberMarkers'
import { dateRangeLabel, emptyInput, scheduleChanged, toInput, toPayload, weekdayNames, weekdayOf } from './dates'
import { applyToSeries } from './series'
import { validate, type Errors } from './validation'

type Step = 'details' | 'edit' | 'scope-save' | 'warn-reset' | 'scope-delete' | 'confirm-delete' | 'conflict'
export type DialogTarget =
  | { kind: 'new'; day: string; hour: number; participantIds: number[] }
  | { kind: 'event'; event: Appointment }

const units = { DAILY: 'day', WEEKLY: 'week', MONTHLY: 'month', YEARLY: 'year' } as const

const repeatLabel = (rule: Recurrence | null) => {
  if (!rule) return 'Repeats'
  const unit = units[rule.frequency]
  const every = rule.interval === 1 ? `every ${unit}` : `every ${rule.interval} ${unit}s`
  const end = rule.until ? `, until ${rule.until}` : rule.count ? `, ${rule.count} times` : ''
  return `Repeats ${every}${end}`
}

export function AppointmentDialog({ target, members, onClose, onChanged, onReload }: {
  target: DialogTarget
  members: Member[]
  onClose: () => void
  onChanged: () => Promise<void>
  /** Re-reads the calendar and returns the current version of the occurrence, or null when it is gone. */
  onReload: (event: Appointment) => Promise<Appointment | null>
}) {
  const isNew = target.kind === 'new'
  const [event, setEvent] = useState<Appointment | null>(target.kind === 'event' ? target.event : null)
  const [series, setSeries] = useState<Appointment | null>(null)
  const [step, setStep] = useState<Step>(isNew ? 'edit' : 'details')
  const [draft, setDraft] = useState<AppointmentInput>(() => target.kind === 'new'
    ? { ...emptyInput(target.day, target.hour), participantIds: target.participantIds } : toInput(target.event))
  const [baseline, setBaseline] = useState<AppointmentInput>(draft)
  const [errors, setErrors] = useState<Errors>({})
  const [failure, setFailure] = useState('')
  const [busy, setBusy] = useState(false)
  const [loadError, setLoadError] = useState('')

  const seriesId = event?.recurring ? event.seriesId ?? event.appointmentId : null
  useEffect(() => {
    if (seriesId === null || !event) return
    let live = true
    calendarApi.get(seriesId).then(found => {
      if (!live) return
      setSeries(found)
      setLoadError('')
      const form = { ...toInput(event), recurrence: found.recurrence, version: found.version }
      setDraft(form)
      setBaseline(form)
    }).catch(() => { if (live) setLoadError('The repeat settings could not be loaded. Close the dialog and try again.') })
    return () => { live = false }
  }, [seriesId, event])

  const startDay = (draft.allDay ? draft.startDate : draft.startAt?.slice(0, 10))
    || (target.kind === 'new' ? target.day : target.event.occurrenceDate)

  const patch = (next: Partial<AppointmentInput>) => setDraft(current => ({ ...current, ...next }))
  const patchRule = (next: Partial<Recurrence>) => setDraft(current => ({ ...current, recurrence: { ...current.recurrence!, ...next } }))

  const toggleAllDay = (allDay: boolean) => {
    const from = draft.allDay ? draft.startDate! : draft.startAt!.slice(0, 10)
    const to = draft.allDay ? draft.endDate! : draft.endAt!.slice(0, 10)
    patch(allDay ? { allDay, startAt: null, endAt: null, startDate: from, endDate: to }
      : { allDay, startDate: null, endDate: null, startAt: `${from}T09:00`, endAt: `${to}T10:00` })
  }

  const setFrequency = (value: string) => {
    if (!value) { patch({ recurrence: null }); return }
    const previous = draft.recurrence
    patch({ recurrence: { frequency: value as Recurrence['frequency'], interval: previous?.interval ?? 1,
      weekdays: previous?.weekdays.length ? previous.weekdays : [weekdayOf(startDay)],
      until: previous?.until ?? null, count: previous?.count ?? null } })
  }

  const persist = async (scope: 'this' | 'all' | null) => {
    setBusy(true)
    setFailure('')
    try {
      if (isNew) await calendarApi.create(toPayload(draft))
      else if (!event!.recurring) await calendarApi.update(event!.appointmentId, toPayload({ ...draft, version: event!.version }))
      else if (scope === 'this') {
        await calendarApi.update(series!.appointmentId, toPayload({ ...draft, recurrence: null, version: series!.version }), 'this', event!.occurrenceDate)
      } else await calendarApi.update(series!.appointmentId, toPayload(applyToSeries(series!, toInput(series!), baseline, draft)), 'all')
      await onChanged()
      onClose()
    } catch (reason) {
      if (axios.isAxiosError(reason) && reason.response?.status === 409) setStep('conflict')
      else { setFailure('The appointment could not be saved. Check the dates and try again.'); setStep('edit') }
    } finally { setBusy(false) }
  }

  const submit = (domEvent: React.FormEvent) => {
    domEvent.preventDefault()
    const found = validate(draft)
    setErrors(found)
    if (Object.keys(found).length) return
    if (!isNew && event!.recurring) setStep('scope-save')
    else void persist(null)
  }

  const chooseSave = (scope: 'this' | 'all') => {
    if (scope === 'all' && scheduleChanged(baseline, draft)) setStep('warn-reset')
    else void persist(scope)
  }

  const remove = async (scope: 'this' | 'all' | null) => {
    setBusy(true)
    setFailure('')
    try {
      if (scope === null) await calendarApi.remove(event!.appointmentId, event!.version)
      else await calendarApi.remove(series!.appointmentId, series!.version, scope, event!.occurrenceDate)
      await onChanged()
      onClose()
    } catch (reason) {
      if (axios.isAxiosError(reason) && reason.response?.status === 409) setStep('conflict')
      else { setFailure('The appointment could not be deleted. Try again.'); setStep('details') }
    } finally { setBusy(false) }
  }

  const reloadAppointment = async () => {
    setBusy(true)
    try {
      const fresh = await onReload(event!)
      if (!fresh) { onClose(); return }
      const form = toInput(fresh)
      setEvent(fresh)
      setSeries(null)
      setDraft(form)
      setBaseline(form)
      setErrors({})
      setFailure('')
      setStep('details')
    } catch { setFailure('The calendar could not be reloaded. Try again.') }
    finally { setBusy(false) }
  }

  const heading = isNew ? 'New appointment' : step === 'edit' ? 'Edit appointment' : 'Appointment details'
  const field = (name: keyof Errors) => ({ 'aria-invalid': errors[name] ? true : undefined, 'aria-describedby': errors[name] ? `error-${name}` : undefined })
  const fieldError = (name: keyof Errors) => errors[name] && <small id={`error-${name}`} className="field-error" aria-hidden="true">{errors[name]}</small>
  const participants = members.filter(member => event?.participantIds.includes(member.id))
  const ready = !event?.recurring || series !== null
  const rule = draft.recurrence
  const ruleChanged = JSON.stringify(baseline.recurrence) !== JSON.stringify(draft.recurrence)

  return <Dialog labelledBy="appointment-title" onClose={onClose} className="appointment-dialog">
    <h2 id="appointment-title">{heading}</h2>

    {step === 'details' && event && <div className="appointment-details">
      <h3>{event.title}</h3>
      <p>{dateRangeLabel(event)}</p>
      {event.recurring && <p className="repeat-note"><span aria-hidden="true">↻ </span>{repeatLabel(series?.recurrence ?? event.recurrence)}</p>}
      <p className="detail-members">{participants.length ? participants.map(member => <MemberBadge key={member.id} member={member} showName />) : 'No participants'}</p>
      {event.location && <p><strong>Location:</strong> {event.location}</p>}
      {event.notes && <p className="detail-notes"><strong>Notes:</strong> {event.notes}</p>}
      {failure && <p role="alert" className="error-message">{failure}</p>}
      {loadError && <p role="alert" className="error-message">{loadError}</p>}
      <div className="dialog-actions">
        <button type="button" className="button button-danger" disabled={!ready || busy}
          onClick={() => setStep(event.recurring ? 'scope-delete' : 'confirm-delete')}>Delete</button>
        <button type="button" className="button button-secondary" onClick={onClose}>Close</button>
        <button type="button" className="button button-primary" disabled={!ready} onClick={() => setStep('edit')} autoFocus>Edit</button>
      </div>
    </div>}

    {step === 'edit' && <form onSubmit={submit} noValidate>
      {failure && <p role="alert" className="error-message">{failure}</p>}
      <label className="field">Title
        <input autoFocus maxLength={200} value={draft.title} onChange={e => patch({ title: e.target.value })} {...field('title')} />
        {fieldError('title')}</label>
      <label className="check-label"><input type="checkbox" checked={draft.allDay} onChange={e => toggleAllDay(e.target.checked)} /> All day</label>
      <div className="form-two">{draft.allDay ? <>
        <label className="field">Start date<input type="date" value={draft.startDate ?? ''} onChange={e => patch({ startDate: e.target.value })} {...field('start')} />{fieldError('start')}</label>
        <label className="field">End date<input type="date" value={draft.endDate ?? ''} onChange={e => patch({ endDate: e.target.value })} {...field('end')} />{fieldError('end')}</label></> : <>
        <label className="field">Start<input type="datetime-local" value={draft.startAt ?? ''} onChange={e => patch({ startAt: e.target.value })} {...field('start')} />{fieldError('start')}</label>
        <label className="field">End<input type="datetime-local" value={draft.endAt ?? ''} onChange={e => patch({ endAt: e.target.value })} {...field('end')} />{fieldError('end')}</label></>}</div>
      <fieldset className="participant-choices" aria-describedby={errors.participants ? 'error-participants' : undefined}>
        <legend>Participants</legend>
        {members.map(member => <label key={member.id} className="check-label">
          <input type="checkbox" checked={draft.participantIds.includes(member.id)}
            onChange={e => patch({ participantIds: e.target.checked ? [...draft.participantIds, member.id] : draft.participantIds.filter(id => id !== member.id) })} />
          <MemberBadge member={member} showName /></label>)}
        {fieldError('participants')}</fieldset>

      <label className="field">Repeat
        <select value={rule?.frequency ?? ''} disabled={!ready} onChange={e => setFrequency(e.target.value)}>
          <option value="">Does not repeat</option><option value="DAILY">Daily</option><option value="WEEKLY">Weekly</option>
          <option value="MONTHLY">Monthly</option><option value="YEARLY">Yearly</option></select></label>
      {rule && <>
        <label className="field">Every (number of {units[rule.frequency]}s)
          <input type="number" min={1} max={99} value={rule.interval || ''}
            onChange={e => patchRule({ interval: Number(e.target.value) })} {...field('interval')} />{fieldError('interval')}</label>
        {rule.frequency === 'WEEKLY' && <fieldset className="weekday-choices" aria-describedby={errors.weekdays ? 'error-weekdays' : undefined}>
          <legend>On days</legend>
          {weekdayNames.map(day => <label key={day} className="check-label">
            <input type="checkbox" checked={rule.weekdays.includes(day)}
              onChange={e => patchRule({ weekdays: e.target.checked ? [...rule.weekdays, day] : rule.weekdays.filter(item => item !== day) })} />
            <span aria-hidden="true">{day[0] + day.slice(1, 3).toLowerCase()}</span><span className="sr-only">{day[0] + day.slice(1).toLowerCase()}</span></label>)}
          {fieldError('weekdays')}</fieldset>}
        <label className="field">Ends
          <select value={rule.until !== null ? 'until' : rule.count !== null ? 'count' : 'never'}
            onChange={e => patchRule({ until: e.target.value === 'until' ? startDay : null, count: e.target.value === 'count' ? 5 : null })}>
            <option value="never">Never</option><option value="until">On date</option><option value="count">After a number of occurrences</option></select></label>
        {rule.until !== null && <label className="field">Last date
          <input type="date" value={rule.until} onChange={e => patchRule({ until: e.target.value })} {...field('until')} />{fieldError('until')}</label>}
        {rule.count !== null && <label className="field">Number of occurrences
          <input type="number" min={1} max={730} value={rule.count || ''}
            onChange={e => patchRule({ count: Number(e.target.value) })} {...field('count')} />{fieldError('count')}</label>}
      </>}
      {loadError && <p role="alert" className="error-message">{loadError}</p>}
      <label className="field">Location<input maxLength={200} value={draft.location ?? ''} onChange={e => patch({ location: e.target.value })} /></label>
      <label className="field">Notes<textarea maxLength={2000} value={draft.notes ?? ''} onChange={e => patch({ notes: e.target.value })} /></label>
      <div className="dialog-actions">
        <button type="button" className="button button-secondary"
          onClick={isNew ? onClose : () => { setDraft(baseline); setErrors({}); setStep('details') }}>Cancel</button>
        <button className="button button-primary" disabled={busy || !ready}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>}

    {step === 'scope-save' && <div>
      <p>This appointment repeats. Which appointments should the change apply to?</p>
      {ruleChanged && <p className="notice">Repeat settings apply to all occurrences only.</p>}
      <div className="dialog-actions">
        <button className="button button-secondary" onClick={() => setStep('edit')}>Cancel</button>
        <button className="button button-secondary" autoFocus disabled={busy} onClick={() => chooseSave('this')}>This occurrence</button>
        <button className="button button-primary" disabled={busy} onClick={() => chooseSave('all')}>All occurrences</button>
      </div></div>}

    {step === 'warn-reset' && <div>
      <p role="alert" className="notice notice-warning">Changing the schedule of all occurrences resets individually changed or deleted occurrences of this series. Save anyway?</p>
      <div className="dialog-actions">
        <button className="button button-secondary" autoFocus onClick={() => setStep('edit')}>Back to editing</button>
        <button className="button button-primary" disabled={busy} onClick={() => void persist('all')}>Save and reset</button>
      </div></div>}

    {step === 'scope-delete' && <div>
      <p>Delete only this occurrence of <strong>{event!.title}</strong>, or every occurrence?</p>
      <div className="dialog-actions">
        <button className="button button-secondary" autoFocus onClick={() => setStep('details')}>Cancel</button>
        <button className="button button-danger" disabled={busy} onClick={() => void remove('this')}>Delete this occurrence</button>
        <button className="button button-danger" disabled={busy} onClick={() => void remove('all')}>Delete all occurrences</button>
      </div></div>}

    {step === 'confirm-delete' && <div>
      <p>Delete <strong>{event!.title}</strong>? This cannot be undone.</p>
      <div className="dialog-actions">
        <button className="button button-secondary" autoFocus onClick={() => setStep('details')}>Cancel</button>
        <button className="button button-danger" disabled={busy} onClick={() => void remove(null)}>Delete appointment</button>
      </div></div>}

    {step === 'conflict' && <div>
      <p role="alert" className="error-message">This appointment was changed on another device after you opened it. Reload it to see the latest version.</p>
      {failure && <p role="alert" className="error-message">{failure}</p>}
      <div className="dialog-actions">
        <button className="button button-secondary" onClick={onClose}>Close</button>
        <button className="button button-primary" autoFocus disabled={busy} onClick={() => void reloadAppointment()}>Reload appointment</button>
      </div></div>}
  </Dialog>
}
