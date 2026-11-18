import { NavLink, Route, Routes } from 'react-router'
import ApplyPage from './pages/ApplyPage.jsx'
import ApplicationsPage from './pages/ApplicationsPage.jsx'
import ApplicationDetailPage from './pages/ApplicationDetailPage.jsx'
import TaskInboxPage from './pages/TaskInboxPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import { ToastProvider } from './components/Toast.jsx'
import { AuthProvider, useAuth } from './auth.jsx'

const link = ({ isActive }) =>
  `rounded-md px-3 py-2 text-sm font-medium ${isActive ? 'bg-white/15 text-white' : 'text-white/75 hover:text-white'}`

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <Shell />
      </ToastProvider>
    </AuthProvider>
  )
}

function Shell() {
  const { user, logout } = useAuth()
  return (
    <>
      <header className="bg-navy">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div>
            <p className="text-xs uppercase tracking-wide text-white/60">SRM Institute of Science and Technology</p>
            <h1 className="text-lg font-semibold text-white">Examination Revaluation Portal</h1>
          </div>
          <nav className="flex flex-wrap gap-1">
            <NavLink to="/" end className={link}>Apply</NavLink>
            <NavLink to="/applications" className={link}>Applications</NavLink>
            {user
              ? <>
                  <NavLink to="/tasks" className={link}>Task inbox</NavLink>
                  <button className="rounded-md px-3 py-2 text-sm font-medium text-white/75 hover:text-white" onClick={logout}>
                    Sign out ({user.displayName})
                  </button>
                </>
              : <NavLink to="/login" className={link}>Staff sign-in</NavLink>}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">
        <Routes>
          <Route path="/" element={<ApplyPage />} />
          <Route path="/applications" element={<ApplicationsPage />} />
          <Route path="/applications/:applicationId" element={<ApplicationDetailPage />} />
          <Route path="/tasks" element={<TaskInboxPage />} />
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </main>
    </>
  )
}
