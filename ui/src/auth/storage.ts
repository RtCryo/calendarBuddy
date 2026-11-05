const TOKEN_KEY = 'calendarbuddy.token'
const EMAIL_KEY = 'calendarbuddy.email'

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function getEmail() {
  return sessionStorage.getItem(EMAIL_KEY)
}

export function setSession(token: string, email: string) {
  sessionStorage.setItem(TOKEN_KEY, token)
  sessionStorage.setItem(EMAIL_KEY, email)
}

export function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(EMAIL_KEY)
}
