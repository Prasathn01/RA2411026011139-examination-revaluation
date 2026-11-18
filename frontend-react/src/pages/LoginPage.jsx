import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { useAuth } from '../auth.jsx'

// Staff sign-in. The CoE uses the "coe" account; an examiner uses the username and temporary
// password from the assignment email. Students never sign in.
export default function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to="/tasks" replace />

  async function submit(e) {
    e.preventDefault()
    if (!username.trim() || !password) { setError('Enter your username and password.'); return }
    setBusy(true)
    try {
      await login(username.trim(), password)
      navigate('/tasks')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate className="card mx-auto max-w-sm space-y-4">
      <div>
        <h2 className="text-lg font-semibold">Staff sign-in</h2>
        <p className="text-sm text-muted">For the Controller of Examinations and assigned examiners. Examiners: use the username and temporary password from your assignment email.</p>
      </div>
      <label className="block text-sm font-medium">Username
        <input className="field mt-1" autoComplete="username" value={username} onChange={(e) => { setUsername(e.target.value); setError(null) }} />
      </label>
      <label className="block text-sm font-medium">Password
        <input type="password" className="field mt-1" autoComplete="current-password" value={password} onChange={(e) => { setPassword(e.target.value); setError(null) }} />
      </label>
      {error && <p className="text-sm text-red-700">{error}</p>}
      <button type="submit" className="btn w-full" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
    </form>
  )
}
