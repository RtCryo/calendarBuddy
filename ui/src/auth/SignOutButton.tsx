import { useNavigate } from 'react-router-dom'
import { clearSession } from './storage'

export function SignOutButton() {
  const navigate = useNavigate()
  return <button type="button" className="sign-out" onClick={() => { clearSession(); navigate("/login", { replace: true }) }}>Sign out</button>
}
