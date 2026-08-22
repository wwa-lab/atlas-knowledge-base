export class AtlasApiError extends Error {
  readonly status: number
  readonly payload: unknown

  constructor(message: string, status: number, payload: unknown) {
    super(message)
    this.name = 'AtlasApiError'
    this.status = status
    this.payload = payload
  }
}

let csrfToken: string | null = null

async function readResponse(response: Response): Promise<unknown> {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

export function messageFrom(payload: unknown, fallback: string): string {
  if (typeof payload !== 'object' || payload === null) return fallback
  const root = payload as Record<string, unknown>
  const nested = typeof root.error === 'object' && root.error !== null
    ? root.error as Record<string, unknown>
    : root
  return typeof nested.message === 'string' && nested.message.trim() ? nested.message : fallback
}

export function resetCsrfToken(): void {
  csrfToken = null
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', headers.get('Accept') ?? 'application/json')
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && !headers.has('X-CSRF-Token')) {
    if (!csrfToken) {
      const csrfResponse = await fetch('/api/v1/auth/csrf', { credentials: 'include' })
      const csrfPayload = await readResponse(csrfResponse)
      if (!csrfResponse.ok || typeof csrfPayload !== 'object' || csrfPayload === null) {
        throw new AtlasApiError(messageFrom(csrfPayload, 'Sign in to continue.'), csrfResponse.status, csrfPayload)
      }
      const value = (csrfPayload as { csrf_token?: unknown }).csrf_token
      if (typeof value !== 'string' || !value) {
        throw new AtlasApiError('The session did not provide a CSRF token.', csrfResponse.status, csrfPayload)
      }
      csrfToken = value
    }
    headers.set('X-CSRF-Token', csrfToken)
  }
  const response = await fetch(path, { ...init, headers, credentials: 'include' })
  const payload = await readResponse(response)
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) resetCsrfToken()
    throw new AtlasApiError(messageFrom(payload, `Request failed (${response.status}).`), response.status, payload)
  }
  return payload as T
}

export function payloadRecord(payload: unknown): Record<string, unknown> {
  return typeof payload === 'object' && payload !== null ? payload as Record<string, unknown> : {}
}

export function errorCode(payload: unknown): string | undefined {
  const root = payloadRecord(payload)
  const nested = payloadRecord(root.error)
  const source = Object.keys(nested).length ? nested : root
  return typeof source.code === 'string' ? source.code : undefined
}
