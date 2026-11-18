import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { api, getToken, setToken } from './api.js'

// Who is signed in to the staff portal: null, or {username, displayName, role} with role COE or EXAMINER.
const AuthContext = createContext({ user: null, ready: false })

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [ready, setReady] = useState(!getToken())   // with a saved token, wait for /me before deciding

  useEffect(() => {
    if (getToken()) {
      api.me().then(setUser).catch(() => setToken(null)).finally(() => setReady(true))
    }
    const signedOut = () => setUser(null)
    window.addEventListener('staff-signed-out', signedOut)
    return () => window.removeEventListener('staff-signed-out', signedOut)
  }, [])

  const login = useCallback(async (username, password) => {
    const res = await api.login(username, password)
    setToken(res.token)
    setUser({ username: res.username, displayName: res.displayName, role: res.role })
  }, [])

  const logout = useCallback(async () => {
    try { await api.logout() } catch { /* already signed out on the server */ }
    setToken(null)
    setUser(null)
  }, [])

  return <AuthContext.Provider value={{ user, ready, login, logout }}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)
