import { useCallback, useEffect, useState } from 'react'
import { Dialog } from '../components/Dialog'
import { calendarApi, colors, familyApi, type Member, type RemovalImpact } from '../api/calendar'
import { add, today } from '../calendar/dates'

export function FamilyPage() {
  const [members, setMembers] = useState<Member[]>([])
  const [counts, setCounts] = useState<Record<number, number>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState<Member | 'new' | null>(null)
  const [name, setName] = useState('')
  const [color, setColor] = useState<string>('indigo')
  const [removing, setRemoving] = useState<Member | null>(null)
  const [impact, setImpact] = useState<RemovalImpact | null>(null)
  const [busy, setBusy] = useState(false)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      const next = await familyApi.list()
      setMembers(next)
      setError('')
      const from = today()
      const events = await calendarApi.range(from, add(from, 99))
      setCounts(Object.fromEntries(next.map(member => [member.id,
        events.filter(event => event.participantIds.includes(member.id)).length])))
    } catch { setError('Family members could not be loaded. Try again.') }
    finally { setLoading(false) }
  }, [])

  useEffect(() => { void Promise.resolve().then(reload) }, [reload])

  const openEditor = (member: Member | 'new') => {
    setEditing(member)
    setName(member === 'new' ? '' : member.name)
    setColor(member === 'new' ? 'indigo' : member.color)
    setError('')
  }

  const save = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!name.trim()) { setError('Enter a name.'); return }
    setBusy(true)
    try {
      if (editing === 'new') await familyApi.create({ name: name.trim(), color })
      else if (editing) await familyApi.update(editing.id, { name: name.trim(), color })
      setEditing(null)
      await reload()
    } catch { setError('Member could not be saved. Check the name and try again.') }
    finally { setBusy(false) }
  }

  const askRemove = async (member: Member) => {
    setRemoving(member)
    setImpact(null)
    setError('')
    try { setImpact(await familyApi.impact(member.id)) }
    catch { setError('Removal impact could not be loaded.') }
  }

  const remove = async () => {
    if (!removing) return
    setBusy(true)
    try { await familyApi.remove(removing.id); setRemoving(null); await reload() }
    catch { setError('Member could not be removed.') }
    finally { setBusy(false) }
  }

  return <>
    <header className="page-heading"><div><h1>Family</h1><p>Plan together, with a color for everyone.</p></div>
      <button className="button button-primary" onClick={() => openEditor('new')} disabled={members.length >= 20}>Add member</button></header>
    {error && !editing && !removing && <p role="alert" className="error-message">{error}</p>}
    {loading ? <p role="status">Loading family members…</p> : members.length === 0
      ? <div className="surface empty-state"><h2>Your family starts here</h2><p>Add a member to create appointments.</p>
          <button className="button button-primary" onClick={() => openEditor('new')}>Add your first member</button></div>
      : <div className="surface table-wrap"><table><caption className="sr-only">Family members</caption><thead><tr><th>Name</th><th>Upcoming appointments</th><th>Actions</th></tr></thead>
          <tbody>{members.map(member => <tr key={member.id}><td><span className={`member-dot member-${member.color}`} aria-hidden="true" />{member.name}</td>
            <td>{counts[member.id] ?? 0}</td><td><button className="button button-secondary" onClick={() => openEditor(member)}>Edit {member.name}</button>{' '}
              <button className="button button-danger" onClick={() => void askRemove(member)}>Remove {member.name}</button></td></tr>)}</tbody></table></div>}
    {editing && <Dialog labelledBy="member-dialog-title" onClose={() => setEditing(null)}>
      <h2 id="member-dialog-title">{editing === 'new' ? 'Add family member' : 'Edit family member'}</h2>
      <form onSubmit={event => void save(event)} noValidate>{error && <p role="alert" className="error-message">{error}</p>}<label className="field">Name<input autoFocus maxLength={100} value={name} onChange={event => setName(event.target.value)} required /></label>
        <fieldset className="color-choices"><legend>Color</legend>{colors.map(choice => <label key={choice} className="color-choice">
          <input type="radio" name="member-color" value={choice} checked={color === choice} onChange={() => setColor(choice)} />
          <span className={`member-dot member-${choice}`} aria-hidden="true" />{choice}{color === choice && <span aria-hidden="true"> ✓</span>}</label>)}</fieldset>
        <div className="dialog-actions"><button type="button" className="button button-secondary" onClick={() => setEditing(null)}>Cancel</button>
          <button className="button button-primary" disabled={busy}>Save member</button></div></form></Dialog>}
    {removing && <Dialog labelledBy="remove-title" onClose={() => setRemoving(null)}>
      <h2 id="remove-title">Remove {removing.name}?</h2>{error && <p role="alert" className="error-message">{error}</p>}{impact ? <p>{impact.deleteCount} appointments will be deleted and {impact.unlinkCount} shared appointments will be unlinked.</p>
        : <p role="status">Checking appointments…</p>}
      <div className="dialog-actions"><button className="button button-secondary" onClick={() => setRemoving(null)}>Cancel</button>
        <button className="button button-danger" disabled={!impact || busy} onClick={() => void remove()}>Remove member</button></div></Dialog>}
  </>
}
