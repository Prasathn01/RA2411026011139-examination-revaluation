// All calls to the Spring Boot backend. The portal never calls Camunda directly:
// React -> Spring Boot (/api/...) -> Camunda.
//
// VITE_API_BASE_URL is empty by default, so requests go to /api on the same origin
// (the Vite dev proxy or nginx forwards them to the backend).
const BASE = import.meta.env.VITE_API_BASE_URL || ''

// Staff sign-in token. sessionStorage keeps it for this browser tab only; reading it can throw
// in a private window or when storage is blocked, so every access is wrapped in try/catch.
const TOKEN_KEY = 'staffToken'
export function getToken() {
  try { return sessionStorage.getItem(TOKEN_KEY) } catch { return null }
}
export function setToken(token) {
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token)
    else sessionStorage.removeItem(TOKEN_KEY)
  } catch { /* storage blocked: the user simply has to sign in again after a reload */ }
}

// Throws an Error carrying the backend's message, field errors and HTTP status, so pages can show them.
async function request(method, path, body) {
  const headers = {}
  if (body) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) {
    // An expired token (for example after a backend restart): tell the AuthProvider to sign out
    if (res.status === 401 && token) {
      setToken(null)
      window.dispatchEvent(new Event('staff-signed-out'))
    }
    const err = new Error(data?.message || `Request failed (${res.status})`)
    err.status = res.status
    err.fieldErrors = data?.fieldErrors || {}
    throw err
  }
  return data
}

export const api = {
  submitApplication: (body) => request('POST', '/api/applications', body),
  listApplications: () => request('GET', '/api/applications'),
  getApplication: (id) => request('GET', `/api/applications/${id}`),
  getTimeline: (id) => request('GET', `/api/applications/${id}/timeline`),
  simulatePayment: (requestId) => request('POST', `/api/payments/${requestId}/simulate`),
  login: (username, password) => request('POST', '/api/auth/login', { username, password }),
  me: () => request('GET', '/api/auth/me'),
  logout: () => request('POST', '/api/auth/logout'),
  listTasks: () => request('GET', '/api/tasks'),
  getTask: (key) => request('GET', `/api/tasks/${key}`),
  completeTask: (key, body) => request('POST', `/api/tasks/${key}/complete`, body),
}
