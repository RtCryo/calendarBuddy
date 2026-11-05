export function safeReturnPath(path: string | null | undefined) {
  if (!path || !path.startsWith('/') || path.startsWith('//')
      || path.includes('\\') || path.includes('://')
      || /%(?:2f|5c|2e)/i.test(path)) return '/calendar'
  const pathname = path.split(/[?#]/, 1)[0]
  return pathname === '/calendar' || pathname === '/family' ? path : '/calendar'
}

export function returnPathFromLocation(state: unknown, search: string) {
  const from = (state as { from?: { pathname?: string, search?: string, hash?: string } } | null)?.from
  const destination = from?.pathname
    ? `${from.pathname}${from.search ?? ''}${from.hash ?? ''}`
    : new URLSearchParams(search).get('next')
  return safeReturnPath(destination)
}
