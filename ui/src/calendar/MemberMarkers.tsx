import type { Member } from '../api/calendar'
import { initials } from './dates'

/** Color marker with initials; the member name is always available as text. */
export function MemberBadge({ member, showName = false }: { member: Member; showName?: boolean }) {
  return <span className={`member-badge member-${member.color}`}>
    <span className="member-initials" aria-hidden="true">{initials(member.name)}</span>
    {showName ? <span className="member-name">{member.name}</span> : <span className="sr-only">{member.name}</span>}
  </span>
}
